package to.eyed.spettro.mobile.core.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Android 17 (API 37) local network protection.
 *
 * An app targeting API 37 needs the runtime permission
 * [Manifest.permission.ACCESS_LOCAL_NETWORK] (a "dangerous" permission in the
 * Nearby devices group) before it may open sockets to private addresses or
 * browse mDNS — without it `NsdManager` fails discovery with
 * `FAILURE_PERMISSION_DENIED` and LAN connects are refused. NsdManager's
 * per-service picker (`DiscoveryRequest.FLAG_SHOW_PICKER`) would avoid the
 * broad permission, but it can't vouch for the QR code's address hint or the
 * last address the host was reached at, which are how Spettro gets past
 * networks that filter mDNS — so Spettro asks for the permission, with its
 * own rationale first. `NEARBY_WIFI_DEVICES` is not involved: it covers
 * Wi-Fi Direct/Aware and scanning, none of which Spettro uses.
 *
 * Before API 37 there is nothing to grant, and the permission reads as held.
 */
object LocalNetworkAccess {
    /** Whether this Android version gates the local network behind a permission. */
    val isRequired: Boolean
        get() = Build.VERSION.SDK_INT >= API_LOCAL_NETWORK_PROTECTION

    /** The permission to request, or null when this Android doesn't have one. */
    val permission: String?
        get() = if (isRequired) Manifest.permission.ACCESS_LOCAL_NETWORK else null

    fun isGranted(context: Context): Boolean {
        val name = permission ?: return true
        return context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    }

    private const val API_LOCAL_NETWORK_PROTECTION = 37
}

/** [NetworkEnvironment] read live from the system on every access. */
class AndroidNetworkEnvironment(context: Context) : NetworkEnvironment {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    override val onLocalNetwork: Boolean
        get() {
            val manager = connectivity ?: return true
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            // A VPN reports its underlying transports too, so Wi-Fi behind a
            // VPN still counts — the LAN is usually still reachable there.
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

    override val localTransport: LocalTransport?
        get() {
            val manager = connectivity ?: return LocalTransport.WiFi
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return null
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LocalTransport.WiFi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> LocalTransport.Ethernet
                else -> null
            }
        }

    override val localNetworkAllowed: Boolean
        get() = LocalNetworkAccess.isGranted(appContext)
}
