package to.eyed.spettro.mobile.core.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    /** Attached and usable. */
    data class Connected(
        val host: HostInfo,
        val agentReady: Boolean,
        val pairingOpen: Boolean = false,
    ) : RemoteState

    /** Paired but unreachable. Carries what to tell the user. */
    data class Offline(val reason: OfflineReason) : RemoteState
}

enum class OfflineReason {
    /** The host isn't advertising — closed, asleep, or another network. */
    HostNotFound,

    /** Reached it but the handshake failed. Retried with backoff. */
    Rejected,

    /** The host said it does not know (or no longer allows) this device. Permanent. */
    NotRecognised,

    /** The host deliberately stopped sharing. */
    HostStopped,

    /** Local network discovery cannot run at all. */
    NoLocalNetwork,

    /** A plain transport problem. */
    Transport,
}

/** Why a QR scan didn't result in a pairing — always user-actionable. */
class RemotePairingException(message: String) : Exception(message)

// MARK: - Client

/**
 * The phone's end of the link: find the paired host, prove who we are, and
 * keep the connection alive.
 *
 * Design notes (mirroring the iOS `RemoteClient`):
 *  - The stored credential is the source of truth for "paired", never the
 *    socket. Losing the connection never unpairs anything; only [forget] does.
 *  - Reconnection is driven by discovery as well as a backoff timer: the host
 *    reappearing on Bonjour resets the backoff and retries immediately.
 *  - Auth verdicts -33001/-33004/-33005 are permanent: retrying cannot fix
 *    them, so the loop stops and the UI offers re-pairing instead.
 *  - `host/state` with `shuttingDown: true` is a deliberate goodbye, shown as
 *    [OfflineReason.HostStopped] with no retry.
 */
class RemoteClient(
    private val store: RemoteCredentialStore,
    val discovery: RemoteDiscovery,
    /** Shown in the host's paired-devices list, e.g. "Pixel 9". */
    private val deviceName: String,
    /** e.g. "Android 15". */
    private val platform: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    okHttpClient: OkHttpClient? = null,
) {
    private val http: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _state = MutableStateFlow<RemoteState>(RemoteState.Unpaired)
    val state: StateFlow<RemoteState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())

    /** Rolling connection log, last 40 lines, shown in Settings. */
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private val _notifications = MutableSharedFlow<RemoteNotification>(
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** Every host -> client notification, parsed. */
    val notifications: SharedFlow<RemoteNotification> = _notifications.asSharedFlow()

    /** The stored pairing, if any. */
    val credential: StateFlow<RemoteCredential?> get() = store.credential

    @Volatile
    private var peer: JsonRpcPeer? = null

    private var connectJob: Job? = null
    private var discoveryJob: Job? = null

    /** Doubles on each consecutive failure; reset the moment anything succeeds. */
    @Volatile
    private var backoffMs: Long = INITIAL_BACKOFF_MS

    /** Poked to cut a backoff wait short (foreground, host reappeared). */
    private val retrySignal = Channel<Unit>(Channel.CONFLATED)

    // MARK: Lifecycle

    /**
     * Starts discovery and, when a credential exists, the reconnect loop.
     * Call on foreground; each call is a fresh start with reset backoff.
     */
    fun start() {
        discovery.start()
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
                else -> {
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
        connectJob?.cancel()
        connectJob = null
        teardown()
        discovery.stop()
        _state.value = if (store.credential.value != null) RemoteState.Searching else RemoteState.Unpaired
    }

    /** Forgets the host entirely. The only path that unpairs. */
    fun forget() {
        connectJob?.cancel()
        connectJob = null
        teardown()
        _state.value = RemoteState.Unpaired
        scope.launch { store.clear() }
        log("credential forgotten")
    }

    // MARK: Pairing

    /**
     * Completes a QR scan: connects (address hint first, then the discovered
     * host), proves the pairing secret, stores the returned device key, and
     * discards the secret.
     *
     * @throws RemotePairingException with a user-actionable message.
     */
    suspend fun pair(payload: PairPayload) {
        if (payload.protocolVersion != RemoteProtocolInfo.VERSION) {
            throw RemotePairingException("That code is from a different version of Spettro.")
        }
        connectJob?.cancel()
        connectJob = null
        teardown()
        // Stay in Unpaired while pairing runs: the pairing screen owns the
        // progress and error presentation. Flipping to Connecting/Offline here
        // would route the UI to the disconnected screen mid-scan with copy
        // written for a *lost* pairing, not a failed one.
        log("pairing with ${payload.hostName} (${payload.hostID.take(8)}…)")

        // The QR's address hint is the fast path — the user is standing in
        // front of the screen. But it can be missing or stale, so the
        // discovered (Bonjour) route is the fallback. Trying costs one connect.
        val candidates = buildList {
            val address = payload.address
            val port = payload.port
            if (address != null && port != null) add(Endpoint(address, port))
            discovery.host(payload.hostID)?.let { found ->
                val ep = Endpoint(found.host, found.port)
                if (none { it == ep }) add(ep)
            }
        }

        var opened: Pair<JsonRpcPeer, RemoteHello>? = null
        var lastError: Exception = RemotePairingException("Couldn't reach that PC.")
        var usedEndpoint: Endpoint? = null
        for (endpoint in candidates) {
            try {
                val candidate = openAndHello(endpoint)
                opened = candidate
                usedEndpoint = endpoint
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                log("pairing via ${endpoint.host}:${endpoint.port} failed: ${e.message}")
                teardown()
            }
        }

        // The hint failed (or was missing). Android's NSD resolves slowly, so
        // give Bonjour a moment to find the host before declaring it gone —
        // the user just scanned a code off a PC that is clearly running.
        if (opened == null) {
            val discovered = withTimeoutOrNull(DISCOVERY_WAIT_MS) {
                discovery.hosts.first { payload.hostID in it }[payload.hostID]
            }
            val ep = discovered?.let { Endpoint(it.host, it.port) }
            if (ep != null && candidates.none { it == ep }) {
                try {
                    opened = openAndHello(ep)
                    usedEndpoint = ep
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    log("pairing via discovered ${ep.host}:${ep.port} failed: ${e.message}")
                    teardown()
                }
            }
        }

        val (activePeer, hello) = opened ?: run {
            _state.value = RemoteState.Unpaired
            log("pairing failed: no reachable endpoint (${lastError.message})")
            throw RemotePairingException(lastError.message ?: "Couldn't reach that PC.")
        }

        if (hello.hostID != payload.hostID) {
            teardown()
            _state.value = RemoteState.Unpaired
            throw RemotePairingException("That code belongs to a different PC.")
        }

        try {
            val result = authenticate(
                activePeer,
                mode = RemoteAuthRequest.MODE_PAIR,
                key = payload.secret,
                hello = hello,
            )
            val deviceKey = result.deviceKey?.let(Base64Url::decode)
            if (deviceKey == null || deviceKey.size != RemoteCrypto.KEY_LENGTH) {
                teardown()
                _state.value = RemoteState.Unpaired
                throw RemotePairingException("The PC didn't send a key back. Try showing a new code.")
            }
            val credential = RemoteCredential(
                hostID = result.hostID,
                hostName = result.hostName,
                hostKind = result.hostKind,
                deviceKey = deviceKey,
                pairedAt = isoNow(),
                lastAddress = usedEndpoint?.host ?: payload.address,
                lastPort = usedEndpoint?.port ?: payload.port,
            )
            store.save(credential)
            // The QR secret is never kept: the durable key replaces it.
            finishConnected(activePeer, hello, result)
            log("paired with ${result.hostName}")
        } catch (e: RpcException) {
            teardown()
            _state.value = RemoteState.Unpaired
            log("pairing auth failed (${e.code}): ${e.message}")
            throw RemotePairingException(e.message ?: "The PC refused the pairing.")
        }
    }

    // MARK: Requests

    /**
     * Sends a request to the host. Throws when not connected — callers
     * surface that rather than queue.
     */
    suspend fun request(method: String, params: JsonObject = JsonRpcPeer.EMPTY_PARAMS): JsonElement {
        val active = peer
        if (active == null || _state.value !is RemoteState.Connected) {
            throw RpcException.closed("not connected")
        }
        return active.request(method, params)
    }

    // MARK: Reconnect loop

    private fun launchConnectLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch { connectLoop() }
    }

    private suspend fun connectLoop() {
        while (coroutineContext.isActive) {
            val cred = store.credential.value ?: store.load() ?: return

            val endpoint = endpointFor(cred)
            if (endpoint == null) {
                _state.value = RemoteState.Searching
                awaitRetry()
                continue
            }

            _state.value = RemoteState.Connecting
            try {
                connectOnce(cred, endpoint)
                return // connected; the close handler restarts the loop
            } catch (e: CancellationException) {
                throw e
            } catch (e: RpcException) {
                teardown()
                if (RemoteAuthError.isPermanent(e.code)) {
                    // The stored credential is dead (or the version is wrong).
                    // Retrying every second would just spin.
                    _state.value = RemoteState.Offline(
                        if (e.code == RemoteAuthError.VERSION_MISMATCH) OfflineReason.Rejected
                        else OfflineReason.NotRecognised,
                    )
                    log("auth rejected permanently (${e.code}): ${e.message}")
                    return
                }
                _state.value = RemoteState.Offline(
                    if (RemoteAuthError.isAuthCode(e.code)) OfflineReason.Rejected
                    else offlineTransportReason(cred),
                )
                log("connect failed (${e.code}): ${e.message}")
            } catch (e: Exception) {
                teardown()
                _state.value = RemoteState.Offline(offlineTransportReason(cred))
                log("connect failed: ${e.message}")
            }

            awaitRetry()
        }
    }

    private fun offlineTransportReason(cred: RemoteCredential): OfflineReason =
        if (discovery.host(cred.hostID) == null) OfflineReason.HostNotFound else OfflineReason.Transport

    /** Sleeps out the backoff, or less if something pokes [retrySignal]. */
    private suspend fun awaitRetry() {
        withTimeoutOrNull(backoffMs) { retrySignal.receive() }
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun endpointFor(cred: RemoteCredential): Endpoint? {
        // Discovery first — it is the only path that survives the host
        // changing address. The stored endpoint is the fallback for networks
        // that filter mDNS.
        discovery.host(cred.hostID)?.let { return Endpoint(it.host, it.port) }
        val address = cred.lastAddress ?: return null
        val port = cred.lastPort ?: return null
        return Endpoint(address, port)
    }

    private suspend fun connectOnce(cred: RemoteCredential, endpoint: Endpoint) {
        teardown()
        val (activePeer, hello) = openAndHello(endpoint)
        if (hello.hostID != cred.hostID) {
            teardown()
            throw RpcException(RpcException.CONNECTION_CLOSED, "a different host answered")
        }
        val result = authenticate(
            activePeer,
            mode = RemoteAuthRequest.MODE_RESUME,
            key = cred.deviceKey,
            hello = hello,
        )
        finishConnected(activePeer, hello, result)
        store.updateEndpoint(endpoint.host, endpoint.port)
        log("connected to ${result.hostName} at ${endpoint.host}:${endpoint.port}")
    }

    // MARK: Handshake

    /** Opens the socket and waits (10s) for the host's `hello`. */
    private suspend fun openAndHello(endpoint: Endpoint): Pair<JsonRpcPeer, RemoteHello> {
        val activePeer = connectWebSocketPeer(http, endpoint.wsUrl())
        peer = activePeer

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
            ?: run {
                teardown()
                throw RpcException(RpcException.CONNECTION_CLOSED, "the host didn't respond")
            }
        return activePeer to hello
    }

    private suspend fun authenticate(
        activePeer: JsonRpcPeer,
        mode: String,
        key: ByteArray,
        hello: RemoteHello,
    ): RemoteAuthResult {
        val challenge = Base64Url.decode(hello.challenge)
            ?: throw RpcException(RpcException.PARSE_ERROR, "bad handshake challenge")
        val request = RemoteAuthRequest(
            mode = mode,
            deviceID = store.deviceID(),
            deviceName = deviceName,
            platform = platform,
            proof = RemoteCrypto.proof(key = key, challenge = challenge, hostID = hello.hostID),
            protocolVersion = RemoteProtocolInfo.VERSION,
        )
        val params = SpettroJson.encodeToJsonElement(RemoteAuthRequest.serializer(), request) as JsonObject
        val result = activePeer.request(RemoteMethod.AUTH, params)
        return SpettroJson.decodeFromJsonElement(RemoteAuthResult.serializer(), result)
    }

    private fun finishConnected(activePeer: JsonRpcPeer, hello: RemoteHello, result: RemoteAuthResult) {
        _state.value = RemoteState.Connected(
            host = HostInfo(hostID = result.hostID, hostName = result.hostName, hostKind = result.hostKind),
            agentReady = result.agentReady,
            pairingOpen = hello.pairingOpen,
        )
        backoffMs = INITIAL_BACKOFF_MS
        installHandlers(activePeer)
        _notifications.tryEmit(RemoteNotification.Hello(hello))
    }

    /** Routes host pushes once authentication is done. */
    private fun installHandlers(activePeer: JsonRpcPeer) {
        activePeer.onNotification = { method, params ->
            val note = RemoteNotification.parse(method, params)
            if (note == null) {
                log("unhandled notification: $method")
            } else {
                if (note is RemoteNotification.HostState) applyHostState(note)
                _notifications.tryEmit(note)
            }
        }
        activePeer.onClose = { _, reason ->
            scope.launch { handleConnectionLost(reason) }
        }
    }

    private fun applyHostState(note: RemoteNotification.HostState) {
        if (note.shuttingDown) {
            // Deliberate, so don't dress it up as a network fault — and don't
            // retry into a host that just told us it left.
            teardown()
            connectJob?.cancel()
            connectJob = null
            _state.value = RemoteState.Offline(OfflineReason.HostStopped)
            log("host stopped sharing${note.message?.let { ": $it" } ?: ""}")
            return
        }
        _state.update { current ->
            if (current is RemoteState.Connected) current.copy(agentReady = note.agentReady) else current
        }
    }

    private fun handleConnectionLost(reason: String) {
        if (_state.value !is RemoteState.Connected) return
        teardown()
        val cred = store.credential.value
        _state.value = RemoteState.Offline(
            if (cred != null && discovery.host(cred.hostID) != null) OfflineReason.Transport
            else OfflineReason.HostNotFound,
        )
        log("connection lost: ${reason.ifEmpty { "closed" }}")
        if (cred != null) launchConnectLoop()
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
                        OfflineReason.Transport,
                        OfflineReason.Rejected,
                        -> kickRetry()
                        // HostStopped / NotRecognised / NoLocalNetwork need a
                        // user action, not a rediscovery.
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
        retrySignal.trySend(Unit)
        launchConnectLoop()
    }

    // MARK: Plumbing

    private fun teardown() {
        val old = peer
        peer = null
        old?.onNotification = null
        old?.onClose = null
        old?.close()
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

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private data class Endpoint(val host: String, val port: Int) {
        /** IPv6 literals need brackets; scope ids do not survive URLs. */
        fun wsUrl(): String {
            val cleaned = host.substringBefore('%')
            return if (':' in cleaned) "ws://[$cleaned]:$port/" else "ws://$cleaned:$port/"
        }
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val HELLO_TIMEOUT_MS = 10_000L
        const val DISCOVERY_WAIT_MS = 8_000L
        const val MAX_DIAGNOSTICS = 40
    }
}
