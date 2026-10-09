package to.eyed.spettro.mobile.coordinator

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import to.eyed.spettro.mobile.coordinator.remote.MobileModel
import to.eyed.spettro.mobile.core.remote.AndroidNetworkEnvironment
import to.eyed.spettro.mobile.core.remote.RemoteClient
import to.eyed.spettro.mobile.core.remote.RemoteCredentialStore
import to.eyed.spettro.mobile.core.remote.RemoteDiscovery

/**
 * Process-wide singletons, created once (DataStore instances must be unique
 * per file, so this cannot live in an Activity).
 */
class AppContainer private constructor(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val credentialStore = RemoteCredentialStore(context)
    val discovery = RemoteDiscovery(context)
    val client = RemoteClient(
        store = credentialStore,
        discovery = discovery,
        deviceName = deviceName(),
        platform = "Android ${Build.VERSION.RELEASE}",
        environment = AndroidNetworkEnvironment(context),
    )
    val model = MobileModel(client, scope)

    init {
        // The connection follows the app, not an activity: rotating, or a
        // second activity on top, must not count as leaving. Leaving itself
        // only drops the socket after a grace period — the system photo
        // picker runs in another process, so picking an image backgrounds
        // the whole app, and coming back to a reloaded chat would lose what
        // was in flight. Must be created on the main thread (it is: from
        // MainActivity.onCreate).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Foreground: reconnect immediately with fresh backoff (iOS parity).
                client.start()
            }

            override fun onStop(owner: LifecycleOwner) {
                // Background: the credential is the durable thing, not the
                // connection — let it go, but not straight away.
                client.stopSoon()
            }
        })
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }

        private fun deviceName(): String {
            val model = Build.MODEL ?: "Android"
            val manufacturer = Build.MANUFACTURER ?: ""
            return if (model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isBlank()) {
                model
            } else {
                "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
            }
        }
    }
}
