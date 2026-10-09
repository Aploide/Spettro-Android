package to.eyed.spettro.mobile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The desktop's motion (design report §1.9): every transition is a short,
 * quiet ease-out — nothing springs or bounces.
 */
object SpettroMotion {
    /** Hover and press fills, chips, small fades: 120ms. */
    const val QUICK_MS = 120

    /** A disclosure chevron turning 90°: 140ms. */
    const val CHEVRON_MS = 140

    /** A switch's track and thumb: 150ms. */
    const val SWITCH_MS = 150

    /** A modal panel rising into place: 160ms. */
    const val MODAL_MS = 160

    /** A toast sliding in: 180ms. */
    const val TOAST_MS = 180

    /** A prompt card rising above the composer: 200ms. */
    const val PROMPT_MS = 200

    /** The thinking slider's thumb and fill: 180ms. */
    const val SLIDER_MS = 180

    /** The prompt card's rise curve, `cubic-bezier(0.2, 0.9, 0.3, 1)`. */
    val PromptEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

    /** The slider's move curve, `cubic-bezier(0.25, 1, 0.5, 1)`. */
    val SliderEasing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

    fun <T> quick(): TweenSpec<T> = tween(QUICK_MS, easing = FastOutSlowInEasing)
    fun <T> chevron(): TweenSpec<T> = tween(CHEVRON_MS, easing = LinearOutSlowInEasing)
    fun <T> modal(): TweenSpec<T> = tween(MODAL_MS, easing = LinearOutSlowInEasing)
    fun <T> toast(): TweenSpec<T> = tween(TOAST_MS, easing = LinearOutSlowInEasing)
    fun <T> prompt(): TweenSpec<T> = tween(PROMPT_MS, easing = PromptEasing)
    fun <T> slider(): TweenSpec<T> = tween(SLIDER_MS, easing = SliderEasing)
}

/** The desktop's floating-layer shadows, which cards never wear. */
enum class Elevation {
    /** `--shadow-sm`: a switch thumb, the active segment. */
    Small,

    /** `--shadow-md`: menus, popovers, toasts, prompt cards. */
    Medium,

    /** `--shadow-lg`: modals and confirm dialogs. */
    Large,

    /** `--shadow-composer`: the floating composer card. */
    Composer,
}

/**
 * A soft drop shadow for a floating layer, heavier on the dark scheme as on
 * the desktop (where a light-scheme shadow is a whisper at 6–16% black).
 */
@Composable
fun Modifier.floatingShadow(level: Elevation, shape: Shape = RoundedCornerShape(Dimens.radiusMd)): Modifier {
    val dark = LocalSpettroColors.current.isDark
    val (elevation, alpha) = when (level) {
        Elevation.Small -> 1.dp to if (dark) 0.3f else 0.08f
        Elevation.Medium -> 8.dp to if (dark) 0.5f else 0.14f
        Elevation.Large -> 16.dp to if (dark) 0.6f else 0.2f
        Elevation.Composer -> 6.dp to if (dark) 0.36f else 0.1f
    }
    val tint = Color.Black.copy(alpha = alpha)
    return shadow(elevation = elevation, shape = shape, clip = false, ambientColor = tint, spotColor = tint)
}
