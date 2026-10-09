package to.eyed.spettro.mobile.core.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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

    override val localNetworkAllowed: Boolean
        get() = true
}
