package to.eyed.spettro.mobile.core.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A Spettro host seen on the network right now. */
data class DiscoveredHost(
    /** The stable host id from the TXT record — what a stored credential names. */
    val hostID: String,
    val name: String,
    val kind: String,
    /** Resolved address, usable right now. Re-resolved on every reappearance. */
    val host: String,
    val port: Int,
    val protocolVersion: Int = RemoteProtocolInfo.VERSION,
)

/**
 * Browses for `_spettro-remote._tcp` via NsdManager and publishes what is
 * currently up, keyed by host id.
 *
 * This is what makes pairing a one-time act: the phone stores a host *id*,
 * never an address, and this answers "is that host here right now, and where".
 * Services whose TXT record lacks `hostid` are ignored — without it there is
 * no way to know which paired host answered.
 *
 * Android NSD quirks handled here:
 *  - Only one resolve may be in flight at a time (FAILURE_ALREADY_ACTIVE),
 *    so resolves are serialized through a queue.
 *  - Resolve failures are retried a couple of times, then dropped.
 *  - A multicast lock is held while browsing, or mDNS answers may never
 *    reach us on some devices.
 */
class RemoteDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _hosts = MutableStateFlow<Map<String, DiscoveredHost>>(emptyMap())

    /** Hosts currently advertising, keyed by host id. */
    val hosts: StateFlow<Map<String, DiscoveredHost>> = _hosts.asStateFlow()

    private val _isBrowsing = MutableStateFlow(false)
    val isBrowsing: StateFlow<Boolean> = _isBrowsing.asStateFlow()

    /** Set when the browser itself cannot run; null while healthy. */
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    private val lock = Any()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** serviceName -> attempts; the resolve pipeline. */
    private val resolveQueue = ArrayDeque<Pair<NsdServiceInfo, Int>>()
    private var resolving = false

    /** serviceName -> hostID, so a lost service can be removed from [hosts]. */
    private val serviceToHost = HashMap<String, String>()

    fun start() {
        synchronized(lock) {
            if (discoveryListener != null) return
            _failure.value = null
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("spettro-remote-discovery").apply {
                    setReferenceCounted(false)
                    try {
                        acquire()
                    } catch (_: Exception) {
                    }
                }
            }
            val listener = makeDiscoveryListener()
            discoveryListener = listener
            try {
                nsdManager.discoverServices(
                    RemoteProtocolInfo.BONJOUR_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
            } catch (e: Exception) {
                discoveryListener = null
                _failure.value = e.message ?: "discovery failed"
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            discoveryListener?.let {
                try {
                    nsdManager.stopServiceDiscovery(it)
                } catch (_: Exception) {
                }
            }
            discoveryListener = null
            multicastLock?.let {
                try {
                    if (it.isHeld) it.release()
                } catch (_: Exception) {
                }
            }
            multicastLock = null
            resolveQueue.clear()
            resolving = false
            serviceToHost.clear()
        }
        _isBrowsing.value = false
        _hosts.value = emptyMap()
    }

    fun host(id: String): DiscoveredHost? = _hosts.value[id]

    // MARK: Listeners

    private fun makeDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            _isBrowsing.value = true
        }

        override fun onDiscoveryStopped(serviceType: String) {
            _isBrowsing.value = false
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            synchronized(lock) { discoveryListener = null }
            _isBrowsing.value = false
            _failure.value = "discovery failed to start (code $errorCode)"
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            _isBrowsing.value = false
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!serviceInfo.serviceType.contains("_spettro-remote._tcp")) return
            enqueueResolve(serviceInfo, attempts = 0)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val hostID = synchronized(lock) { serviceToHost.remove(serviceInfo.serviceName) } ?: return
            _hosts.update { it - hostID }
        }
    }

    private fun enqueueResolve(info: NsdServiceInfo, attempts: Int) {
        val startNow = synchronized(lock) {
            resolveQueue.addLast(info to attempts)
            if (resolving) {
                false
            } else {
                resolving = true
                true
            }
        }
        if (startNow) resolveNext()
    }

    private fun resolveNext() {
        val next = synchronized(lock) {
            val n = resolveQueue.removeFirstOrNull()
            if (n == null) resolving = false
            n
        } ?: return
        val (info, attempts) = next
        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(info, makeResolveListener(attempts))
        } catch (_: Exception) {
            resolveNext()
        }
    }

    private fun makeResolveListener(attempts: Int) = object : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            addResolved(serviceInfo)
            resolveNext()
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // FAILURE_ALREADY_ACTIVE and transient failures: retry, bounded.
            if (attempts < MAX_RESOLVE_ATTEMPTS) {
                synchronized(lock) { resolveQueue.addLast(serviceInfo to (attempts + 1)) }
            }
            resolveNext()
        }
    }

    private fun addResolved(serviceInfo: NsdServiceInfo) {
        val attributes: Map<String, ByteArray?> = try {
            serviceInfo.attributes
        } catch (_: Exception) {
            emptyMap()
        }

        fun txt(key: String): String? = attributes[key]?.let { String(it, Charsets.UTF_8) }

        // No hostid, not one of ours — matching by display name would attach
        // to the wrong machine in any office with two identical laptops.
        val hostID = txt(RemoteProtocolInfo.TxtKey.HOST_ID)?.takeIf { it.isNotEmpty() } ?: return
        val address = serviceInfo.host?.hostAddress ?: return
        val port = serviceInfo.port
        if (port <= 0) return

        val discovered = DiscoveredHost(
            hostID = hostID,
            name = txt(RemoteProtocolInfo.TxtKey.HOST_NAME) ?: serviceInfo.serviceName,
            kind = txt(RemoteProtocolInfo.TxtKey.HOST_KIND) ?: "app",
            host = address,
            port = port,
            protocolVersion = txt(RemoteProtocolInfo.TxtKey.PROTOCOL_VERSION)?.toIntOrNull()
                ?: RemoteProtocolInfo.VERSION,
        )
        synchronized(lock) { serviceToHost[serviceInfo.serviceName] = hostID }
        _hosts.update { it + (hostID to discovered) }
    }

    private companion object {
        const val MAX_RESOLVE_ATTEMPTS = 2
    }
}
