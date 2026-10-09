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

    // Starting and stopping the connection with the app's lifecycle lives
    // in AppContainer (ProcessLifecycleOwner), with a grace period on stop.
}
