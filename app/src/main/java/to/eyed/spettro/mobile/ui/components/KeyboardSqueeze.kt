package to.eyed.spettro.mobile.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Below this much height left over by the keyboard, a screen with a text
 * field drops its chrome. A phone on its side has about 470dp; Gboard there
 * takes about 300 of them, which leaves room for the field and its buttons
 * but not for a top bar and status strip as well.
 */
val KeyboardSqueezeHeight: Dp = 300.dp

/**
 * Whether the keyboard is up and leaves the window less than
 * [KeyboardSqueezeHeight]. Measured against the whole window rather than
 * the space a layout got, so hiding chrome in answer can't flip it back.
 */
@Composable
fun keyboardSqueezed(): Boolean {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val ime = WindowInsets.ime
    // The inset changes on every frame of the keyboard's slide; only the
    // answer matters, so callers recompose when it flips and not before.
    val squeezed by remember(density, window, ime) {
        derivedStateOf {
            val height = window.containerSize.height
            val bottom = ime.getBottom(density)
            bottom > 0 && height > 0 && with(density) { (height - bottom).toDp() } < KeyboardSqueezeHeight
        }
    }
    return squeezed
}

/**
 * Hides the status bar while [hidden]: what the keyboard leaves of a phone
 * on its side is better spent on the text field. A swipe still shows it.
 */
@Composable
fun HideStatusBarWhile(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden, view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            if (hidden) controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
