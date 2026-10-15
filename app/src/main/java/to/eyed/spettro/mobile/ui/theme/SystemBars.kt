package to.eyed.spettro.mobile.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Draws edge to edge with the status- and navigation-bar icons following the
 * app's scheme rather than the phone's: with Appearance set to Light on a
 * phone in dark mode, the default would leave white icons on a light canvas.
 */
@Composable
fun ComponentActivity.SystemBarsFor(dark: Boolean) {
    LaunchedEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

/**
 * Tells the system which of the window themes (values / values-night) this
 * app wants, so the splash and the window under the first frame are already
 * in the chosen appearance on the next launch — not white on a phone set to
 * Dark. The system keeps the setting itself; the activity declares
 * `configChanges="uiMode"`, so applying it doesn't recreate anything.
 */
fun Context.applyWindowNightMode(appearance: Appearance) {
    val uiModes = getSystemService(UiModeManager::class.java) ?: return
    val mode = when (appearance) {
        Appearance.System -> UiModeManager.MODE_NIGHT_AUTO
        Appearance.Light -> UiModeManager.MODE_NIGHT_NO
        Appearance.Dark -> UiModeManager.MODE_NIGHT_YES
    }
    runCatching { uiModes.setApplicationNightMode(mode) }
}
