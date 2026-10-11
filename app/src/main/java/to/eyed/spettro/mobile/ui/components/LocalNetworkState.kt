package to.eyed.spettro.mobile.ui.components

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import to.eyed.spettro.mobile.core.remote.AndroidNetworkEnvironment

/**
 * Whether the phone is on Wi-Fi or Ethernet right now — the only networks a
 * computer's LAN address can mean anything on — kept current as the phone
 * joins and leaves networks.
 */
@Composable
fun rememberOnLocalNetwork(): State<Boolean> {
    val context = LocalContext.current
    val environment = remember(context) { AndroidNetworkEnvironment(context) }
    val state = remember(environment) { mutableStateOf(environment.onLocalNetwork) }
    DisposableEffect(environment) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // Read the answer from what each callback hands over: asking the
        // manager for the active network from inside one can still return
        // the network that was just lost.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }

            // A default-network callback only hears onLost for the current
            // default, and only when nothing replaces it: no network at all.
            override fun onLost(network: Network) {
                state.value = false
            }
        }
        val registered = manager != null && runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        state.value = environment.onLocalNetwork
        onDispose {
            if (registered) runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }
    return state
}
