package to.eyed.spettro.mobile.ui.theme

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
