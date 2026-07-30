package to.eyed.spettro.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import to.eyed.spettro.mobile.coordinator.AppContainer
import to.eyed.spettro.mobile.ui.AppRoot
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = AppContainer.get(this)
        setContent {
            SpettroTheme {
                AppRoot(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Foreground: reconnect immediately with fresh backoff (iOS parity).
        container.client.start()
    }

    override fun onStop() {
        super.onStop()
        // Background: drop the socket deliberately; the credential is the
        // durable thing, not the connection.
        container.client.stop()
    }
}
