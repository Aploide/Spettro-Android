package to.eyed.spettro.mobile.core.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.rpc.JsonRpcPeer
import to.eyed.spettro.mobile.core.rpc.RpcException
import to.eyed.spettro.mobile.core.rpc.connectWebSocketPeer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

// MARK: - Public state

/** The host the client is (or was last) attached to. */
data class HostInfo(
    val hostID: String,
    val hostName: String,
    val hostKind: String,
)

sealed interface RemoteState {
    /** No credential — the user has never scanned a code. */
    data object Unpaired : RemoteState

    /** Paired, looking for the host on the network. */
    data object Searching : RemoteState

    /** Found it; connecting and authenticating. */
    data object Connecting : RemoteState

    /**
     * Paired, and dropped on purpose (Settings › Disconnect): nothing is
     * looking or retrying until [RemoteClient.start].
     */
    data object Disconnected : RemoteState

    /** Attached and usable. */
    data class Connected(
        val host: HostInfo,
        val agentReady: Boolean,
        val pairingOpen: Boolean = false,
        /** Which route won: the LAN directly, or the host's internet tunnel. */
        val via: RemoteRoute = RemoteRoute.Lan,
        /** What carries it — Wi-Fi, Ethernet, a USB cable, or the internet. */
        val link: RemoteLink = if (via == RemoteRoute.Internet) RemoteLink.Internet else RemoteLink.LocalNetwork,
        /** What this host can do beyond the original protocol ([RemoteFeature]). */
        val features: Set<String> = emptySet(),
    ) : RemoteState {
        fun has(feature: String): Boolean = feature in features
    }

    /** Paired but unreachable. Carries what to tell the user. */
    data class Offline(val reason: OfflineReason) : RemoteState
}

enum class OfflineReason {
    /** Not on this network (closed, asleep, or elsewhere), and no tunnel to try. */
    HostNotFound,

    /** Not on this network, and its internet tunnel didn't answer either. */
    TunnelUnreachable,

    /**
     * Reached it but the handshake was turned down for a reason that can
     * pass — a stale proof, a closed pairing window, a rate limit. Retried
     * with backoff; the pairing itself is fine.
     */
    Rejected,

    /** The host speaks a different protocol version. Permanent, but not a lost pairing. */
    VersionMismatch,

    /** The host said it does not know (or no longer allows) this device. Permanent. */
    NotRecognised,

    /** The host deliberately stopped sharing. Polled slowly when it has a tunnel. */
    HostStopped,

    /** Android's local-network permission is missing, and nothing else reached the host. */
    NoLocalNetwork,

    /** A plain transport problem. */
    Transport,
}

/** Why a QR scan didn't result in a pairing — always user-actionable. */
class RemotePairingException(message: String) : Exception(message)

// MARK: - Client

/**
 * When a host that said goodbye ([OfflineReason.HostStopped]) is checked
 * on again. Every route, the tunnel included, is checked every
 * [RemoteClient.HOST_STOPPED_POLL_MS]. In between, its LAN address gets
 * quick checks — 3 s, then twice as long each time up to 15 s — so sharing
 * coming back on is noticed in seconds: Android's NSD often misses the
 * host's goodbye and so never reports it coming back.
 *
 * Not thread-safe; the connect loop is its only user.
 */
internal class GoodbyeChecks(now: Long, fullFirst: Boolean = false) {
    private var lanWait = RemoteClient.GOODBYE_LAN_FIRST_MS
    private var nextFullAt = if (fullFirst) now else now + RemoteClient.HOST_STOPPED_POLL_MS

    /**
     * Starts the check due at [now]: true when it covers every route (and
     * the next such check is scheduled), false for a LAN-only one.
     */
    fun takeCheck(now: Long): Boolean {
        if (now < nextFullAt) return false
        nextFullAt = now + RemoteClient.HOST_STOPPED_POLL_MS
        return true
    }

    /**
     * How long to wait from [now] before the next check: the next quick LAN
     * check when there is a LAN route to check ([lanToCheck]), never past
     * the next check of every route.
     */
    fun nextWait(now: Long, lanToCheck: Boolean): Long {
        val untilFull = (nextFullAt - now).coerceAtLeast(0L)
        if (!lanToCheck) return untilFull
        val wait = minOf(lanWait, untilFull)
        lanWait = (lanWait * 2).coerceAtMost(RemoteClient.GOODBYE_LAN_MAX_MS)
        return wait
    }
}

/**
 * The phone's end of the link: find the paired host, prove who we are, and
 * keep the connection alive.
 *
 * Design notes (mirroring the iOS `RemoteClient`):
 *  - The stored credential is the source of truth for "paired", never the
 *    socket. Losing the connection never unpairs anything; only [forget] does.
 *  - Every route to the host is tried at once with a head start each
 *    (Bonjour, last LAN address, then the public tunnel — see
 *    [EndpointPlan]); the first `hello` wins and the rest are closed before
 *    `auth` goes out.
 *  - Reconnection is driven by discovery as well as a backoff timer: the host
 *    reappearing on Bonjour resets the backoff and retries immediately.
 *  - Auth verdicts -33001/-33004/-33005 are permanent: retrying cannot fix
 *    them, so the loop stops. Only -33001/-33005 mean the pairing is gone.
 *  - `host/state` with `shuttingDown: true` is a deliberate goodbye, shown as
 *    [OfflineReason.HostStopped]. Bonjour may never say when it's back, so
 *    with an address to try the host is checked on instead of given up on:
 *    every route slowly, its LAN address quickly ([GoodbyeChecks]).
 */
class RemoteClient(
    private val store: RemoteCredentialStore,
    val discovery: RemoteDiscovery,
    /** Shown in the host's paired-devices list, e.g. "Pixel 9". */
    private val deviceName: String,
    /** e.g. "Android 15". */
    private val platform: String,
    private val environment: NetworkEnvironment = NetworkEnvironment.Unrestricted,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    okHttpClient: OkHttpClient? = null,
) {
    private val http: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Same pool, shorter fuse: a LAN address that doesn't answer in a few
     * seconds is on another network, and the tunnel is already being tried.
     */
    private val lanHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(LAN_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val _state = MutableStateFlow<RemoteState>(RemoteState.Unpaired)
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())

    /** Rolling connection log, last 40 lines, shown in Settings. */
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private val _refusals = MutableStateFlow(0)

    /**
     * How many handshakes in a row the host has turned down
     * ([OfflineReason.Rejected]); zero once one gets through. One is
     * usually a passing thing; a run of them likely isn't.
     */
    val refusals: StateFlow<Int> = _refusals.asStateFlow()

    private val _hostQuit = MutableStateFlow(false)

    /**
     * Whether the last goodbye ([OfflineReason.HostStopped]) was the app
     * quitting rather than sharing being switched off — see [isQuitGoodbye].
     * Sharing stays on across a quit, so "turn it back on" would be wrong.
     */
    val hostQuit: StateFlow<Boolean> = _hostQuit.asStateFlow()

    private val _inbound = MutableSharedFlow<InboundNotification>(
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** Every host -> client notification, parsed, with its place on the wire. */
    val inbound: SharedFlow<InboundNotification> = _inbound.asSharedFlow()

    /** Every host -> client notification, parsed. */
    val notifications: Flow<RemoteNotification> = _inbound.map { it.note }

    /** The stored pairing, if any. */
    val credential: StateFlow<RemoteCredential?> get() = store.credential

    /** The authenticated (or authenticating) connection; attempts still racing aren't here. */
    @Volatile
    private var active: Attempt? = null

    private val connectionIds = AtomicLong(0)

    private var connectJob: Job? = null
    private var discoveryJob: Job? = null
    private var pendingStop: Job? = null

    /** Doubles on each consecutive failure; reset the moment anything succeeds. */
    @Volatile
    private var backoffMs: Long = INITIAL_BACKOFF_MS

    /**
     * Set while the host has said goodbye: failed retries keep showing this
     * reason (not "can't reach") and wait [HOST_STOPPED_POLL_MS] between tries.
     */
    @Volatile
    private var holdReason: OfflineReason? = null

    /** When the checks on a host that said goodbye are due; see [GoodbyeChecks]. */
    @Volatile
    private var goodbyeChecks = GoodbyeChecks(now = 0L)

    /** Poked to cut a backoff wait short (foreground, host reappeared). */
    private val retrySignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Credential writes learnt from the wire, applied one at a time in the
     * order they were learnt — never on the connect path, which must not
     * wait on the disk once the connection is up.
     */
    private val persistQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in persistQueue) {
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("couldn't save the pairing's details: ${e.message}")
                }
            }
        }
    }

    // MARK: Lifecycle

    /**
     * Starts discovery and, when a credential exists, the reconnect loop.
     * Call on foreground; each call is a fresh start with reset backoff, and
     * cancels a stop that [stopSoon] scheduled.
     */
    fun start() {
        pendingStop?.cancel()
        pendingStop = null
        // Browsing without the local-network permission only fails; the
        // grant calls start() again.
        if (environment.localNetworkAllowed) discovery.start()
        observeDiscovery()
        scope.launch {
            val cred = store.load()
            if (cred == null) {
                if (_state.value !is RemoteState.Connected) _state.value = RemoteState.Unpaired
                return@launch
            }
            // Coming to the foreground is a fresh start, not a continuation of
            // whatever backoff the last session ended on.
            backoffMs = INITIAL_BACKOFF_MS
            retrySignal.trySend(Unit)
            when (_state.value) {
                is RemoteState.Connected, RemoteState.Connecting -> Unit
                // Check whether sharing is back, but keep saying it's off
                // until it is — "can't reach" would be the wrong story.
                RemoteState.Offline(OfflineReason.HostStopped) -> {
                    holdReason = OfflineReason.HostStopped
                    goodbyeChecks = GoodbyeChecks(now = monotonicMs(), fullFirst = true)
                    launchConnectLoop()
                }
                else -> {
                    holdReason = null
                    _state.value = RemoteState.Searching
                    launchConnectLoop()
                }
            }
        }
    }

    /**
     * Drops the socket deliberately (background). The pairing is untouched;
     * the next [start] reconnects.
     */
    fun stop() {
        pendingStop?.cancel()
        pendingStop = null
        connectJob?.cancel()
        connectJob = null
        holdReason = null
        teardown()
        discovery.stop()
        _state.value = when {
            store.credential.value == null -> RemoteState.Unpaired
            // A goodbye outlasts the background: start() re-checks it
            // quietly instead of telling a network-fault story.
            _state.value == RemoteState.Offline(OfflineReason.HostStopped) -> _state.value
            else -> RemoteState.Searching
        }
    }

    /**
     * Drops the socket because the user asked to, and stays down: unlike
     * [stop], which only ever runs out of sight, this one is looked at, so
     * it says nothing is happening rather than "looking". The next [start]
     * — Reconnect, or the app's next return to the foreground — reconnects.
     */
    fun disconnect() {
        stop()
        if (store.credential.value != null) {
            _state.value = RemoteState.Disconnected
            log("disconnected")
        }
    }

    /**
     * [stop] after [graceMs], unless [start] comes first. Going to the
     * background is often a detour — the photo picker, a rotation, a glance
     * at another app — and dropping the socket for each would reload the
     * chat and lose what was in flight.
     */
    fun stopSoon(graceMs: Long = BACKGROUND_GRACE_MS) {
        pendingStop?.cancel()
        pendingStop = scope.launch {
            delay(graceMs)
            log("background grace over; disconnecting")
            stop()
        }
    }

    /** Forgets the host entirely. The only path that unpairs. */
    fun forget() {
        pendingStop?.cancel()
        pendingStop = null
        connectJob?.cancel()
        connectJob = null
        holdReason = null
        teardown()
        _refusals.value = 0
        _state.value = RemoteState.Unpaired
        scope.launch { store.clear() }
        log("credential forgotten")
    }

    // MARK: Pairing

    /**
     * Completes a QR scan: connects (address hint and discovered host first,
     * then the tunnel), proves the pairing secret, stores the returned device
     * key, and discards the secret.
     *
     * Safe to call while already paired (a pairing link): the current
     * pairing is only replaced once the new one is made, and if this
     * fails, the client carries on with the old one.
     *
     * @throws RemotePairingException with a user-actionable message.
     */
    suspend fun pair(payload: PairPayload) {
        if (payload.protocolVersion != RemoteProtocolInfo.VERSION) {
            throw RemotePairingException("That code is from a different version of Spettro.")
        }
        pendingStop?.cancel()
        pendingStop = null
        connectJob?.cancel()
        connectJob = null
        holdReason = null
        teardown()
        // Unpaired, stay Unpaired while pairing runs: the pairing screen owns
        // the progress and error presentation. Flipping to Connecting/Offline
        // would route the UI to the disconnected screen mid-scan with copy
        // written for a *lost* pairing, not a failed one. Paired, the old
        // connection is gone now, and Connected would be a lie.
        if (store.credential.value != null) _state.value = RemoteState.Connecting
        log("pairing with ${payload.hostName} (${payload.hostID.take(8)}…)")
        try {
            pairOnce(payload)
        } catch (e: Throwable) {
            afterFailedPairing()
            throw e
        }
    }

    /** Back to the pairing that was there before [pair] — or to none, if there was none. */
    private fun afterFailedPairing() {
        if (store.credential.value == null) {
            _state.value = RemoteState.Unpaired
            return
        }
        log("pairing failed; carrying on with ${store.credential.value?.hostName}")
        backoffMs = INITIAL_BACKOFF_MS
        _state.value = RemoteState.Searching
        launchConnectLoop()
    }

    private suspend fun pairOnce(payload: PairPayload) {

        // The QR's address hint is the fast path — the user is standing in
        // front of the screen. But it can be missing or stale, so the
        // discovered (Bonjour) route and the tunnel race it.
        val candidates = EndpointPlan.pairingCandidates(payload, discovery.host(payload.hostID), environment)
        var opened: Attempt? = null
        var lastError: Throwable? = null
        // A tunnel that says pairing isn't open there (internet pairing is
        // off by default) can't pair this phone, however fast it answered —
        // so it doesn't win the race: a LAN route that is merely slower, or
        // Bonjour below, still gets to.
        var tunnelWontPair = false
        if (candidates.isNotEmpty()) {
            try {
                opened = race(candidates, payload.hostID) { attempt ->
                    if (!EndpointPlan.canPairOver(attempt.endpoint, attempt.hello.pairingOpen)) {
                        tunnelWontPair = true
                        throw RpcException(RemoteAuthError.PAIRING_EXPIRED, "the tunnel isn't open for pairing")
                    }
                }
            } catch (e: RaceFailure) {
                lastError = e.cause
                log("pairing: no candidate answered (${e.message})")
            }
        }

        // Everything failed (or there was nothing to try). Android's NSD
        // resolves slowly, so give Bonjour a moment to find the host before
        // declaring it gone — the user just scanned a code off a computer
        // that is clearly running.
        if (opened == null && environment.localNetworkAllowed) {
            // With the tunnel out of the running, Bonjour is the last hope.
            val wait = if (payload.remoteHost != null && !tunnelWontPair) DISCOVERY_WAIT_WITH_TUNNEL_MS else DISCOVERY_WAIT_MS
            val discovered = withTimeoutOrNull(wait) {
                discovery.hosts.first { payload.hostID in it }[payload.hostID]
            }
            val ep = discovered?.let { Endpoint.Lan(it.host, it.port) }
            if (ep != null && ep !in candidates) {
                try {
                    opened = openAndHello(ep, payload.hostID)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    log("pairing via discovered ${ep.label} failed: ${e.message}")
                }
            }
        }

        val attempt = opened ?: run {
            log("pairing failed: no reachable endpoint (${lastError?.message})")
            throw RemotePairingException(
                when {
                    lastError is WrongHostException -> "That code belongs to a different computer."
                    // The computer answered over the internet, and only
                    // pairs on its own network.
                    tunnelWontPair && !environment.localNetworkAllowed ->
                        "Pair on the same network as this computer first, and allow Spettro to reach devices on your local network."
                    tunnelWontPair -> "Pair on the same network as this computer first."
                    !environment.localNetworkAllowed && payload.remoteHost == null ->
                        "Allow Spettro to reach devices on your local network, then scan again."
                    !environment.localNetworkAllowed ->
                        "Couldn't reach that computer over the internet. To pair on this Wi-Fi, allow Spettro to reach devices on your local network."
                    payload.remoteHost != null ->
                        "Couldn't reach that computer, here or over the internet. Check that it's awake, then show a new code."
                    else ->
                        "Couldn't reach that computer. Make sure this phone is on the same Wi-Fi, then try again."
                },
            )
        }

        try {
            val gate = installHandlers(attempt)
            val result = authenticate(attempt, mode = RemoteAuthRequest.MODE_PAIR, key = payload.secret)
            val deviceKey = result.deviceKey?.let(Base64Url::decode)
            if (deviceKey == null || deviceKey.size != RemoteCrypto.KEY_LENGTH) {
                teardown()
                throw RemotePairingException("The computer didn't send a key back. Try showing a new code.")
            }
            val credential = RemoteCredential(
                hostID = result.hostID,
                hostName = result.hostName,
                hostKind = result.hostKind,
                deviceKey = deviceKey,
                pairedAt = isoNow(),
                lastAddress = (attempt.endpoint as? Endpoint.Lan)?.host ?: payload.address,
                lastPort = (attempt.endpoint as? Endpoint.Lan)?.port ?: payload.port,
                remoteHost = advertisedRemoteHost(result) ?: payload.remoteHost,
            )
            store.save(credential)
            // The QR secret is never kept: the durable key replaces it.
            finishConnected(attempt, result, gate)
            log("paired with ${result.hostName} via ${attempt.endpoint.label}")
        } catch (e: RpcException) {
            teardown()
            log("pairing auth failed (${e.code}): ${e.message}")
            throw RemotePairingException(e.message ?: "The computer refused the pairing.")
        } catch (e: RemotePairingException) {
            throw e
        } catch (e: CancellationException) {
            teardown()
            throw e
        } catch (e: Exception) {
            teardown()
            log("pairing failed: ${e.message}")
            throw RemotePairingException("Pairing didn't finish. Show a new code and try again.")
        }
    }

    // MARK: Requests

    /**
     * Sends a request to the host. Throws when not connected — callers
     * surface that rather than queue.
     */
    suspend fun request(method: String, params: JsonObject = JsonRpcPeer.EMPTY_PARAMS): JsonElement =
        requestMarked(method, params).first

    /**
     * [request], also saying where on the wire the reply sat: notifications
     * whose [FrameMark] [is after][FrameMark.isAfter] it arrived after the
     * reply, so a snapshot in the reply can't already contain them.
     */
    suspend fun requestMarked(
        method: String,
        params: JsonObject = JsonRpcPeer.EMPTY_PARAMS,
    ): Pair<JsonElement, FrameMark> {
        val current = active
        if (current == null || _state.value !is RemoteState.Connected) {
            throw RpcException.closed("not connected")
        }
        val framed = current.peer.requestFramed(method, params)
        return framed.result to FrameMark(current.connection, framed.frame)
    }

    // MARK: Reconnect loop

    private fun launchConnectLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            // A fresh loop tries at once; a poke left over from before it
            // ran would only cut its first wait short.
            retrySignal.tryReceive()
            connectLoop()
        }
    }

    private suspend fun connectLoop() {
        while (coroutineContext.isActive) {
            val cred = store.credential.value ?: store.load() ?: return
            val hold = holdReason

            val every = candidatesFor(cred)
            // While the host is gone on purpose, the LAN is checked often and
            // quietly in between the slow checks of every route: a refused
            // connection costs next to nothing, and the host doesn't count
            // it, while the tunnel is somebody else's server.
            val lanOnly = hold != null && !goodbyeChecks.takeCheck(monotonicMs())
            val candidates = if (lanOnly) every.filterIsInstance<Endpoint.Lan>() else every
            if (candidates.isEmpty()) {
                _state.value = when {
                    hold != null -> RemoteState.Offline(hold)
                    !environment.localNetworkAllowed -> RemoteState.Offline(OfflineReason.NoLocalNetwork)
                    else -> RemoteState.Searching
                }
                awaitRetry(slow = hold != null, lanToCheck = hasQuickLanCheck(every))
                continue
            }

            // A goodbye stays on screen while it is quietly re-checked, and
            // so does a refusal — flashing "Looking for…" between two
            // refusals would only make it flicker.
            if (hold == null && _state.value != RemoteState.Offline(OfflineReason.Rejected)) {
                _state.value = RemoteState.Connecting
            }
            try {
                val attempt = connectOnce(cred, candidates, quiet = lanOnly)
                // Connected; the close handler restarts the loop — unless the
                // socket already went in the instant before this returned,
                // when the handler found this loop still running.
                if (active === attempt) return
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: RpcException) {
                teardown()
                if (RemoteAuthError.isPermanent(e.code)) {
                    // The stored credential is dead (or the version is wrong).
                    // Retrying every second would just spin.
                    holdReason = null
                    _state.value = RemoteState.Offline(
                        if (RemoteAuthError.isRevocation(e.code)) OfflineReason.NotRecognised
                        else OfflineReason.VersionMismatch,
                    )
                    log("auth rejected permanently (${e.code}): ${e.message}")
                    return
                }
                val refused = hold == null && RemoteAuthError.isAuthCode(e.code)
                if (refused) _refusals.update { it + 1 }
                _state.value = RemoteState.Offline(
                    hold ?: if (refused) OfflineReason.Rejected else offlineTransportReason(cred, candidates),
                )
                if (!lanOnly || RemoteAuthError.isAuthCode(e.code)) log("connect failed (${e.code}): ${e.message}")
                if (refused && _refusals.value > 1) {
                    // The host counts failed handshakes per address and
                    // blocks a run of them; over the tunnel that address may
                    // be shared. Ask rarely enough never to trip it.
                    withTimeoutOrNull(REFUSED_RETRY_MS) { retrySignal.receive() }
                    continue
                }
            } catch (e: Exception) {
                teardown()
                _state.value = RemoteState.Offline(hold ?: offlineTransportReason(cred, candidates))
                if (!lanOnly) log("connect failed: ${e.message}")
            }

            awaitRetry(slow = hold != null, lanToCheck = hasQuickLanCheck(every))
        }
    }

    /**
     * Whether the quick LAN checks after a goodbye run: only with a LAN
     * route to try, and only in sight — a backgrounded app waits out its
     * grace on the slow poll, then [stop] ends the checks altogether.
     */
    private fun hasQuickLanCheck(candidates: List<Endpoint>): Boolean =
        candidates.any { it is Endpoint.Lan } && pendingStop?.isActive != true

    private fun candidatesFor(cred: RemoteCredential): List<Endpoint> =
        EndpointPlan.candidates(
            discovered = discovery.host(cred.hostID),
            lastAddress = cred.lastAddress,
            lastPort = cred.lastPort,
            remoteHost = cred.remoteHost,
            environment = environment,
        )

    private fun offlineTransportReason(cred: RemoteCredential, tried: List<Endpoint>): OfflineReason =
        EndpointPlan.offlineReason(tried, hostDiscovered = discovery.host(cred.hostID) != null, environment)

    /**
     * Sleeps out the backoff — or, after a goodbye ([slow]), until the next
     * quick LAN check or slow check of every route — or less if something
     * pokes [retrySignal].
     */
    private suspend fun awaitRetry(slow: Boolean = false, lanToCheck: Boolean = false) {
        if (slow) {
            withTimeoutOrNull(goodbyeChecks.nextWait(monotonicMs(), lanToCheck)) { retrySignal.receive() }
            return
        }
        withTimeoutOrNull(backoffMs) { retrySignal.receive() }
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
    }

    private suspend fun connectOnce(cred: RemoteCredential, candidates: List<Endpoint>, quiet: Boolean = false): Attempt {
        teardown()
        val attempt = try {
            race(candidates, cred.hostID, quiet = quiet)
        } catch (e: RaceFailure) {
            throw (e.cause as? Exception) ?: e
        }
        val result = try {
            val gate = installHandlers(attempt)
            authenticate(attempt, mode = RemoteAuthRequest.MODE_RESUME, key = cred.deviceKey)
                .also { finishConnected(attempt, it, gate) }
        } catch (e: CancellationException) {
            // stop() may have torn down before this attempt became active,
            // and the auth frame may already be out: close it here, or it
            // lives on as a signed-in socket nobody holds.
            discard(attempt)
            throw e
        }
        // Only a LAN route is an address worth remembering; the tunnel has
        // its own field, and must never overwrite the LAN hint.
        (attempt.endpoint as? Endpoint.Lan)?.let { lan ->
            persist { store.updateEndpoint(cred.hostID, lan.host, lan.port) }
        }
        // A host that announces the feature omits the field when it has no
        // tunnel, so absence is an answer; from an older host it says nothing.
        if (result.features.contains(RemoteFeature.REMOTE_HOST) || result.remoteHost != null) {
            val advertised = result.remoteHost.orEmpty()
            persist { store.updateRemoteHost(cred.hostID, advertised) }
        }
        log("connected to ${result.hostName} via ${attempt.endpoint.label}")
        return attempt
    }

    /**
     * The tunnel hostname an auth result vouches for. A host that announces
     * [RemoteFeature.REMOTE_HOST] omits the field when it has no tunnel, so
     * absence is an answer; from an older host it's no information (null
     * here, and the caller keeps what it had).
     */
    private fun advertisedRemoteHost(result: RemoteAuthResult): String? =
        result.remoteHost?.let(RemotePairing::normalizeRemoteHost)

    // MARK: Handshake

    /** A socket that said hello and is waiting to authenticate. */
    private class Attempt(
        val endpoint: Endpoint,
        val peer: JsonRpcPeer,
        val hello: RemoteHello,
        val connection: Long,
    )

    /** The socket answered, but as another host — a reused address, most likely. */
    private class WrongHostException : Exception("a different computer answered")

    /**
     * Races every candidate to a `hello` from [hostID]; losers are closed
     * before this returns. [vet] may turn a hello down by throwing — the
     * attempt is closed and counts as failed, and the race goes on.
     */
    private suspend fun race(
        candidates: List<Endpoint>,
        hostID: String,
        quiet: Boolean = false,
        vet: (Attempt) -> Unit = {},
    ): Attempt {
        if (!quiet) log("trying ${candidates.joinToString { it.label }}")
        return raceStaggered(
            candidates = candidates,
            stagger = RACE_HEAD_START_MS,
            attempt = { endpoint ->
                try {
                    openAndHello(endpoint, hostID).also { opened ->
                        try {
                            vet(opened)
                        } catch (e: Exception) {
                            discard(opened)
                            throw e
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!quiet) log("${endpoint.label}: ${e.message}")
                    throw e
                }
            },
            discard = ::discard,
        )
    }

    /**
     * Opens the socket and waits (10s) for the host's `hello`, which must
     * come from [hostID]. The socket is closed on any failure, cancellation
     * included — a race abandons attempts mid-flight.
     */
    private suspend fun openAndHello(endpoint: Endpoint, hostID: String): Attempt {
        val activePeer = connectWebSocketPeer(if (endpoint is Endpoint.Lan) lanHttp else http, endpoint.url)
        var handedOff = false
        try {
            val helloDeferred = CompletableDeferred<RemoteHello>()
            activePeer.onLog = { line -> log(line) }
            activePeer.onNotification = { method, params ->
                if (method == RemoteMethod.HELLO && params != null) {
                    try {
                        helloDeferred.complete(
                            SpettroJson.decodeFromJsonElement(RemoteHello.serializer(), params),
                        )
                    } catch (e: Exception) {
                        helloDeferred.completeExceptionally(
                            RpcException(RpcException.PARSE_ERROR, "bad hello: ${e.message}"),
                        )
                    }
                }
            }
            activePeer.onClose = { _, reason ->
                helloDeferred.completeExceptionally(
                    RpcException.closed(reason.ifEmpty { "connection closed" }),
                )
            }

            // A host that accepts the socket but never says hello would otherwise
            // hang forever — a real possibility with a half-open connection.
            val hello = withTimeoutOrNull(HELLO_TIMEOUT_MS) { helloDeferred.await() }
                ?: throw RpcException(RpcException.CONNECTION_CLOSED, "the host didn't respond")
            if (hello.hostID != hostID) throw WrongHostException()
            handedOff = true
            return Attempt(endpoint, activePeer, hello, connectionIds.incrementAndGet())
        } finally {
            if (!handedOff) {
                activePeer.onNotification = null
                activePeer.onClose = null
                activePeer.close()
            }
        }
    }

    /**
     * Makes [attempt] the active connection and routes its pushes — through
     * a gate that holds them until [finishConnected]. Installed *before*
     * `auth` goes out: the host sends `host/state` (and replays pending
     * prompts) the moment auth succeeds, possibly ahead of the reply being
     * processed here, and a hello-only handler would drop them.
     */
    private fun installHandlers(attempt: Attempt): NotificationGate<InboundNotification> {
        active = attempt
        val gate = NotificationGate<InboundNotification> { item -> dispatch(item) }
        val activePeer = attempt.peer
        activePeer.onNotification = { method, params ->
            val frame = activePeer.currentFrame
            val note = RemoteNotification.parse(method, params)
            if (note == null) {
                log("unhandled notification: $method")
            } else {
                gate.offer(InboundNotification(note, FrameMark(attempt.connection, frame)))
            }
        }
        activePeer.onClose = { _, reason ->
            scope.launch { handleConnectionLost(attempt, reason) }
        }
        return gate
    }

    private suspend fun authenticate(attempt: Attempt, mode: String, key: ByteArray): RemoteAuthResult {
        val challenge = Base64Url.decode(attempt.hello.challenge)
            ?: throw RpcException(RpcException.PARSE_ERROR, "bad handshake challenge")
        val request = RemoteAuthRequest(
            mode = mode,
            deviceID = store.deviceID(),
            deviceName = deviceName,
            platform = platform,
            proof = RemoteCrypto.proof(key = key, challenge = challenge, hostID = attempt.hello.hostID),
            protocolVersion = RemoteProtocolInfo.VERSION,
        )
        val params = SpettroJson.encodeToJsonElement(RemoteAuthRequest.serializer(), request) as JsonObject
        val result = attempt.peer.request(RemoteMethod.AUTH, params)
        return SpettroJson.decodeFromJsonElement(RemoteAuthResult.serializer(), result)
    }

    private fun finishConnected(
        attempt: Attempt,
        result: RemoteAuthResult,
        gate: NotificationGate<InboundNotification>,
    ) {
        // The auth result speaks for the authenticated session; the hello
        // went to an anonymous socket and may say less.
        val features = result.features.ifEmpty { attempt.hello.features }.toSet()
        _state.value = RemoteState.Connected(
            host = HostInfo(hostID = result.hostID, hostName = result.hostName, hostKind = result.hostKind),
            agentReady = result.agentReady,
            pairingOpen = attempt.hello.pairingOpen,
            via = attempt.endpoint.route,
            link = EndpointPlan.linkOf(attempt.endpoint, environment.localTransport),
            features = features,
        )
        backoffMs = INITIAL_BACKOFF_MS
        holdReason = null
        _refusals.value = 0
        _inbound.tryEmit(
            InboundNotification(RemoteNotification.Hello(attempt.hello), FrameMark(attempt.connection, 0)),
        )
        gate.open()
    }

    /** Every authenticated push passes here, in wire order. */
    private fun dispatch(item: InboundNotification) {
        val note = item.note
        if (note is RemoteNotification.HostState) applyHostState(note)
        _inbound.tryEmit(item)
    }

    private fun applyHostState(note: RemoteNotification.HostState) {
        val hostID = (_state.value as? RemoteState.Connected)?.host?.hostID
        if (hostID != null) note.remoteHost?.let { host -> persist { store.updateRemoteHost(hostID, host) } }
        if (note.shuttingDown) {
            // The first goodbye says which it was: a quitting desktop follows
            // its own with the sharing-off one as it takes the listener down.
            if (_state.value != RemoteState.Offline(OfflineReason.HostStopped)) {
                _hostQuit.value = isQuitGoodbye(note.message)
            }
            // Deliberate, so don't dress it up as a network fault — and don't
            // retry into a host that just told us it left. Rediscovery says
            // when it's back, but only where Bonjour gets through: not over
            // a tunnel, and not on the many networks that filter it. So keep
            // checking slowly whenever there is somewhere to check.
            teardown()
            connectJob?.cancel()
            connectJob = null
            _state.value = RemoteState.Offline(OfflineReason.HostStopped)
            log("host stopped sharing${note.message?.let { ": $it" } ?: ""}")
            if (keepsCheckingAfterGoodbye(store.credential.value, note.remoteHost)) {
                holdReason = OfflineReason.HostStopped
                goodbyeChecks = GoodbyeChecks(now = monotonicMs())
                connectJob = scope.launch {
                    // The same wait the loop uses, so rediscovery, coming to
                    // the foreground or Try again cut it short.
                    retrySignal.tryReceive()
                    val routes = store.credential.value?.let(::candidatesFor).orEmpty()
                    awaitRetry(slow = true, lanToCheck = hasQuickLanCheck(routes))
                    connectLoop()
                }
            }
            return
        }
        _state.update { current ->
            if (current is RemoteState.Connected) current.copy(agentReady = note.agentReady) else current
        }
    }

    private fun handleConnectionLost(attempt: Attempt, reason: String) {
        // A socket that lost a race, or one already replaced, isn't news.
        if (active !== attempt) return
        if (_state.value !is RemoteState.Connected) return
        teardown()
        val cred = store.credential.value
        // It was there a moment ago, by whichever route: a lost connection,
        // ridden out while the loop finds it again — not a verdict on where
        // the host is, or on a permission the working route didn't need.
        _state.value = RemoteState.Offline(OfflineReason.Transport)
        log("connection lost: ${reason.ifEmpty { "closed" }}")
        if (cred != null) {
            // A loop that just connected may not have returned yet; a fresh
            // one, rather than "already running", so the drop is retried.
            connectJob?.cancel()
            connectJob = null
            launchConnectLoop()
        }
    }

    // MARK: Discovery

    private fun observeDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch {
            discovery.hosts.collect { hosts ->
                val cred = store.credential.value ?: return@collect
                if (cred.hostID !in hosts) return@collect
                when (val current = _state.value) {
                    is RemoteState.Searching -> kickRetry()
                    is RemoteState.Offline -> when (current.reason) {
                        OfflineReason.HostNotFound,
                        OfflineReason.TunnelUnreachable,
                        OfflineReason.Transport,
                        OfflineReason.Rejected,
                        // Advertising again after a goodbye means sharing is
                        // back on — worth a quiet check.
                        OfflineReason.HostStopped,
                        -> kickRetry()
                        // NotRecognised / VersionMismatch / NoLocalNetwork
                        // need a user action, not a rediscovery.
                        else -> Unit
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * The host just (re)appeared — positive evidence, so retry immediately
     * rather than serving out a backoff earned while it was switched off.
     */
    private fun kickRetry() {
        backoffMs = INITIAL_BACKOFF_MS
        if (_state.value == RemoteState.Offline(OfflineReason.HostStopped)) {
            holdReason = OfflineReason.HostStopped
        }
        retrySignal.trySend(Unit)
        launchConnectLoop()
    }

    // MARK: Plumbing

    private fun persist(write: suspend () -> Unit) {
        persistQueue.trySend(write)
    }

    /** Closes [attempt], whether or not it got as far as being [active]. */
    private fun discard(attempt: Attempt) {
        if (active === attempt) {
            teardown()
        } else {
            attempt.peer.onNotification = null
            attempt.peer.onClose = null
            attempt.peer.close()
        }
    }

    private fun teardown() {
        val old = active
        active = null
        old?.peer?.onNotification = null
        old?.peer?.onClose = null
        old?.peer?.close()
    }

    @Synchronized
    private fun log(line: String) {
        android.util.Log.d("SpettroRemote", line)
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _diagnostics.update { existing ->
            val appended = existing + "$stamp  $line"
            if (appended.size > MAX_DIAGNOSTICS) appended.takeLast(MAX_DIAGNOSTICS) else appended
        }
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    companion object {
        /**
         * A goodbye `host/state` (`shuttingDown`) with no message is the
         * desktop app quitting; switching sharing off says why ("Remote
         * access was turned off."). Sharing stays on across a quit and comes
         * back with the next launch.
         */
        fun isQuitGoodbye(message: String?): Boolean = message.isNullOrBlank()

        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val HELLO_TIMEOUT_MS = 10_000L
        private const val DISCOVERY_WAIT_MS = 8_000L

        /** With a tunnel in the QR the race already tried the internet; Bonjour gets less patience. */
        private const val DISCOVERY_WAIT_WITH_TUNNEL_MS = 3_000L

        private const val LAN_CONNECT_TIMEOUT_MS = 3_000L

        /** How long each route gets to itself before the next one joins the race. */
        private const val RACE_HEAD_START_MS = 750L

        /**
         * Whether a host that said goodbye is checked on now and then: when
         * the phone knows an address to try — its public hostname, or the
         * LAN address it last reached it at. "This screen updates by itself
         * once it's on" can't wait on Bonjour alone, which a network that
         * filters mDNS never delivers.
         */
        internal fun keepsCheckingAfterGoodbye(cred: RemoteCredential?, announcedRemoteHost: String?): Boolean {
            val remoteHost = announcedRemoteHost ?: cred?.remoteHost
            return !remoteHost.isNullOrBlank() || !cred?.lastAddress.isNullOrBlank()
        }

        /** Between checks of every route (the tunnel too) on a host that said goodbye. */
        internal const val HOST_STOPPED_POLL_MS = 45_000L

        /** The first quick LAN check after a goodbye; each next one waits twice as long… */
        internal const val GOODBYE_LAN_FIRST_MS = 3_000L

        /** …up to this. */
        internal const val GOODBYE_LAN_MAX_MS = 15_000L

        /**
         * Between handshakes once the host has refused more than one in a
         * row: well clear of its limit of five failures in five minutes.
         */
        private const val REFUSED_RETRY_MS = 90_000L

        private const val MAX_DIAGNOSTICS = 40

        /** How long a backgrounded app keeps its connection; see [stopSoon]. */
        const val BACKGROUND_GRACE_MS = 60_000L
    }
}
