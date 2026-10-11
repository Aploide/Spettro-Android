package to.eyed.spettro.mobile.core.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The pieces of a connection attempt that don't touch a socket: where to
 * try, in what order, how to pick a winner, and how to hold notifications
 * that arrive before the client is ready for them. Kept free of Android and
 * OkHttp so they can be checked on the JVM.
 */

// MARK: - Route

/** How the phone reached the host. */
enum class RemoteRoute {
    /** Directly, on the same Wi-Fi or Ethernet network. */
    Lan,

    /** Through the host's public tunnel (`wss://<remoteHost>/`). */
    Internet,
}

// MARK: - Endpoints

/** One place the host might be listening. */
sealed interface Endpoint {
    val url: String
    val route: RemoteRoute

    /** A short label for the connection log. */
    val label: String

    /** The host's own listener on the LAN: plain `ws://`, auth is the HMAC proof. */
    data class Lan(val host: String, val port: Int) : Endpoint {
        override val route: RemoteRoute get() = RemoteRoute.Lan
        override val label: String get() = "$host:$port"

        /** IPv6 literals need brackets; scope ids do not survive URLs. */
        override val url: String
            get() {
                val cleaned = host.substringBefore('%')
                return if (':' in cleaned) "ws://[$cleaned]:$port/" else "ws://$cleaned:$port/"
            }
    }

    /**
     * The host's public tunnel. TLS ends at the tunnel's edge, which owns a
     * real certificate for the hostname, so this is `wss://` on the default
     * port with no path.
     */
    data class Tunnel(val hostname: String) : Endpoint {
        override val route: RemoteRoute get() = RemoteRoute.Internet
        override val label: String get() = hostname
        override val url: String get() = "wss://$hostname/"
    }
}

/** What the phone's network allows right now. */
interface NetworkEnvironment {
    /** On Wi-Fi or Ethernet — the only networks a LAN address can mean anything on. */
    val onLocalNetwork: Boolean

    /** Android 17's local-network permission is granted (always true before it). */
    val localNetworkAllowed: Boolean

    companion object {
        /** For tests and previews: a phone on an open home network. */
        val Unrestricted: NetworkEnvironment = object : NetworkEnvironment {
            override val onLocalNetwork = true
            override val localNetworkAllowed = true
        }
    }
}

object EndpointPlan {
    /**
     * Every endpoint worth trying for a paired host, best first:
     *  1. where Bonjour sees it right now (survives the host changing address),
     *  2. the last LAN address that worked — only on Wi-Fi or Ethernet, since
     *     on mobile data a `192.168.x` address is at best someone else's;
     *     a loopback address is this phone whatever the network (a USB
     *     cable's port forward), so it is always worth a try,
     *  3. its public tunnel, the one route that works from anywhere.
     *
     * LAN routes are dropped entirely while the local-network permission is
     * missing: the system would refuse the socket anyway.
     */
    fun candidates(
        discovered: DiscoveredHost?,
        lastAddress: String?,
        lastPort: Int?,
        remoteHost: String?,
        environment: NetworkEnvironment,
    ): List<Endpoint> = buildList {
        if (environment.localNetworkAllowed) {
            discovered?.let { add(Endpoint.Lan(it.host, it.port)) }
            if (lastAddress != null && lastPort != null &&
                (environment.onLocalNetwork || isLoopback(lastAddress))
            ) {
                add(Endpoint.Lan(lastAddress, lastPort))
            }
        }
        remoteHost?.takeIf { it.isNotBlank() }?.let { add(Endpoint.Tunnel(it)) }
    }.distinct()

    /** `127.x.x.x`, `::1` or `localhost`: an address on this phone itself. */
    fun isLoopback(address: String): Boolean {
        val plain = address.substringBefore('%').removePrefix("[").removeSuffix("]").lowercase()
        return plain == "localhost" || plain == "::1" ||
            Regex("""^(::ffff:)?127\.\d{1,3}\.\d{1,3}\.\d{1,3}$""").matches(plain)
    }

    /** The same order for a fresh QR scan: its address hint, Bonjour, then its tunnel. */
    fun pairingCandidates(
        payload: PairPayload,
        discovered: DiscoveredHost?,
        environment: NetworkEnvironment,
    ): List<Endpoint> = buildList {
        if (environment.localNetworkAllowed) {
            val address = payload.address
            val port = payload.port
            if (address != null && port != null) add(Endpoint.Lan(address, port))
            discovered?.let { add(Endpoint.Lan(it.host, it.port)) }
        }
        payload.remoteHost?.let { add(Endpoint.Tunnel(it)) }
    }.distinct()

    /**
     * Whether a `hello` on [endpoint] can lead to a pairing. Over the tunnel
     * `pairingOpen` is false while the host only pairs on its own network
     * (the default), and such a hello must not win the pairing race. On
     * the LAN it only means the window closed, and auth says so better.
     */
    fun canPairOver(endpoint: Endpoint, pairingOpen: Boolean): Boolean =
        endpoint !is Endpoint.Tunnel || pairingOpen

    /**
     * What to tell the user when nothing in [tried] answered:
     *  - Bonjour sees the host right now, so it's there and the socket
     *    failed — a plain transport problem;
     *  - the tunnel was tried and didn't answer — wherever the phone is,
     *    that's the route that failed (a passing blip, more often than not);
     *  - nothing at all could be tried because the local-network
     *    permission is missing (no tunnel to fall back on);
     *  - otherwise the host simply isn't on this network.
     *
     * A missing permission alone never wins over a tunnel that was tried:
     * a phone on mobile data has no LAN to allow, and "allow local network
     * access" there would be the wrong story — and one that can't be ridden
     * out like a dropped tunnel can.
     */
    fun offlineReason(
        tried: List<Endpoint>,
        hostDiscovered: Boolean,
        environment: NetworkEnvironment,
    ): OfflineReason = when {
        hostDiscovered && environment.localNetworkAllowed -> OfflineReason.Transport
        tried.any { it is Endpoint.Tunnel } -> OfflineReason.TunnelUnreachable
        !environment.localNetworkAllowed -> OfflineReason.NoLocalNetwork
        else -> OfflineReason.HostNotFound
    }
}

// MARK: - Racing

/** No candidate answered; [cause] is the last attempt's failure (null when there were none). */
class RaceFailure(cause: Exception?) : Exception(cause?.message ?: "nothing to try", cause)

/**
 * Tries [candidates] in order with a head start each — "happy eyeballs":
 * the first starts at once, each next one after [stagger] or as soon as the
 * one before it fails, whichever comes first. The first success wins.
 *
 * Every other attempt is cancelled, and any that succeeded anyway is handed
 * to [discard] before this returns. That matters here: the host closes an
 * older socket of the same device once a newer one authenticates, so a
 * losing socket must be gone before the winner sends `auth`.
 *
 * @throws RaceFailure when every candidate failed (or there were none).
 */
suspend fun <E, R> raceStaggered(
    candidates: List<E>,
    stagger: Long,
    attempt: suspend (E) -> R,
    discard: (R) -> Unit,
): R {
    if (candidates.isEmpty()) throw RaceFailure(null)
    val results = Channel<Result<R>>(Channel.UNLIMITED)
    // Outside the scope so a cancelled race can still give its winner back.
    var winner: R? = null
    var won = false
    try {
        return coroutineScope {
            val gates = List(candidates.size) { CompletableDeferred<Unit>() }
            gates[0].complete(Unit)
            val jobs = candidates.mapIndexed { index, candidate ->
                launch {
                    gates[index].await()
                    val next = gates.getOrNull(index + 1)
                    val headStart = next?.let { gate -> launch { delay(stagger); gate.complete(Unit) } }
                    val outcome = try {
                        Result.success(attempt(candidate))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    if (outcome.isFailure) {
                        headStart?.cancel()
                        next?.complete(Unit)
                    }
                    // Never suspends (the channel is unbounded), so a success
                    // can't be lost to a cancellation between here and there.
                    results.trySend(outcome)
                }
            }

            var lastError: Exception? = null
            for (i in candidates.indices) {
                val outcome = results.receive()
                if (outcome.isSuccess) {
                    winner = outcome.getOrNull()
                    won = true
                    break
                }
                lastError = outcome.exceptionOrNull() as? Exception
            }
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }
            // Anything that finished between the win and the cancellation.
            drainSuccesses(results, discard)
            @Suppress("UNCHECKED_CAST")
            if (won) winner as R else throw RaceFailure(lastError)
        }
    } catch (e: CancellationException) {
        // The caller went away mid-race. The scope has waited for every
        // attempt by now, so whatever succeeded is in hand: close it all
        // rather than leave an open socket nobody holds.
        @Suppress("UNCHECKED_CAST")
        if (won) discard(winner as R)
        drainSuccesses(results, discard)
        throw e
    } finally {
        results.close()
    }
}

private fun <R> drainSuccesses(results: Channel<Result<R>>, discard: (R) -> Unit) {
    while (true) {
        val late = results.tryReceive().getOrNull() ?: break
        late.getOrNull()?.let(discard)
    }
}

// MARK: - Notification ordering

/** Where a notification sat on the wire: which connection, which frame. */
data class FrameMark(val connection: Long, val frame: Long) {
    /** True when this frame came after [other] on the same connection. */
    fun isAfter(other: FrameMark): Boolean = connection == other.connection && frame > other.frame
}

/** A host notification with its place on the wire. */
data class InboundNotification(val note: RemoteNotification, val mark: FrameMark)

/**
 * Holds notifications until [open], then passes them (and everything after)
 * straight to [deliver], in arrival order.
 *
 * The host may push `host/state` — and, with pending-prompt replay, whole
 * permission asks — the instant auth succeeds, before the client has
 * processed the auth reply. Installing this before sending `auth` keeps
 * those frames instead of dropping them on the hello-only handler.
 */
class NotificationGate<T>(private val deliver: (T) -> Unit) {
    private val lock = Any()
    private var held: MutableList<T>? = mutableListOf()

    fun offer(item: T) {
        synchronized(lock) {
            val buffer = held
            if (buffer != null) {
                buffer.add(item)
                return
            }
            deliver(item)
        }
    }

    /**
     * Flushes the held items and lets later ones through. Delivery happens
     * under the lock, so a frame arriving mid-flush can't overtake one
     * that was already held.
     */
    fun open() {
        synchronized(lock) {
            val buffer = held ?: return
            held = null
            buffer.forEach(deliver)
        }
    }

    /** Drops everything held; the gate stays shut (the connection failed). */
    fun discard() {
        synchronized(lock) { held?.clear() }
    }
}
