package to.eyed.spettro.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import to.eyed.spettro.mobile.coordinator.AppContainer
import to.eyed.spettro.mobile.core.remote.RemotePairing
import to.eyed.spettro.mobile.ui.AppRoot
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = AppContainer.get(this)
        // A recreated activity has already handed its link over.
        if (savedInstanceState == null) takePairingLink(intent)
        setContent {
            SpettroTheme {
                AppRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takePairingLink(intent)
    }

    /** A `spettro-pair://pair?…` link opened from elsewhere: AppRoot takes it from here. */
    private fun takePairingLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        // Reopened from Recents, the task replays the intent that started
        // it: that link was already dealt with.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val link = intent.dataString ?: return
        if (link.startsWith("${RemotePairing.SCHEME}://", ignoreCase = true)) {
            container.pendingPairingLink.value = link
        }
    }

    // Starting and stopping the connection with the app's lifecycle lives
    // in AppContainer (ProcessLifecycleOwner), with a grace period on stop.
}
