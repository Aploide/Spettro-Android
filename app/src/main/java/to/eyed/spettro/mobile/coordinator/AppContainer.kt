package to.eyed.spettro.mobile.coordinator

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import to.eyed.spettro.mobile.coordinator.headless.HeadlessStore
import to.eyed.spettro.mobile.coordinator.remote.MobileModel
import to.eyed.spettro.mobile.core.remote.RemoteClient
import to.eyed.spettro.mobile.core.remote.RemoteCredentialStore
import to.eyed.spettro.mobile.core.remote.RemoteDiscovery

/**
 * Process-wide singletons, created once (DataStore instances must be unique
 * per file, so this cannot live in an Activity).
 */
class AppContainer private constructor(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = AppPrefs(context)
    val headlessStore = HeadlessStore(context)

    val credentialStore = RemoteCredentialStore(context)
    val discovery = RemoteDiscovery(context)
    val client = RemoteClient(
        store = credentialStore,
        discovery = discovery,
        deviceName = deviceName(),
        platform = "Android ${Build.VERSION.RELEASE}",
    )
    val model = MobileModel(client, scope)

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
