package to.eyed.spettro.mobile.coordinator

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import to.eyed.spettro.mobile.coordinator.remote.MobileModel
import to.eyed.spettro.mobile.core.remote.AndroidNetworkEnvironment
import to.eyed.spettro.mobile.core.remote.RemoteClient
import to.eyed.spettro.mobile.core.remote.RemoteCredentialStore
import to.eyed.spettro.mobile.core.remote.RemoteDiscovery
import to.eyed.spettro.mobile.model.Drafts
import to.eyed.spettro.mobile.ui.theme.ThemeStore

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

    /** The phone's appearance and accent settings. */
    val themeStore = ThemeStore(context)

    /** What was typed but not sent, per chat, kept across launches. */
    val drafts = Drafts(SharedPreferencesDraftStorage(context))

    /** A pairing link opened from outside the app, waiting for the UI to act on it. */
    val pendingPairingLink = MutableStateFlow<String?>(null)

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
                // connection — let it go, but not straight away. A delete
                // still in its Undo window goes now, while the socket is
                // up: the app may not come back to carry it out.
                model.flushDeletes()
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

/** [Drafts] in a SharedPreferences file; writes go to disk in the background. */
private class SharedPreferencesDraftStorage(context: Context) : Drafts.Storage {
    private val prefs = context.applicationContext.getSharedPreferences("spettro_drafts", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
