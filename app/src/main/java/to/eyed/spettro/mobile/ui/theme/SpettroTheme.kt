package to.eyed.spettro.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * Spacing and radius tokens, 1:1 with the desktop's `--sp-*` and `--rad-*`
 * (a 4-point grid, plus the 2 and 32 ends).
 */
object Dimens {
    val spacing2xs = 2.dp
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val spacing2xl = 32.dp

    /** Rows, menu items, segmented-control buttons. */
    val radiusXs = 6.dp

    /** Chips, small cards, code-block strokes, attachment thumbnails. */
    val radiusSm = 8.dp

    /** The default card, popover, menu and dialog radius. */
    val radiusMd = 12.dp

    /** The composer and the top corners of a sheet. */
    val radiusLg = 20.dp

    /** The user chat bubble. */
    val radiusBubble = 18.dp

    /** An approval or question card. */
    val radiusPrompt = 16.dp

    /** An option inside a prompt card. */
    val radiusOption = 10.dp

    /** One-dp strokes everywhere a border appears. */
    val hairlineWidth = 1.dp

    /** The reading column on a wide screen (`--measure`). */
    val measure = 760.dp

    /** The phone's minimum touch target, the desktop's 40px rows grown to fit a finger. */
    val minTouch = 48.dp
}

// ---------------------------------------------------------------------------
// Material color schemes, derived from the tokens so Material components that
// are not given explicit colours still land on the palette: primary = accent,
// background/surface = canvas, the surfaceContainer ladder around
// surface-raised, error = danger, outlineVariant = hairline. Containers are
// the desktop's tinted fills flattened onto the canvas.
// ---------------------------------------------------------------------------

internal fun colorSchemeFor(c: SpettroColors): ColorScheme {
    val primaryContainer = c.accentSoft.compositeOver(c.canvas)
    val secondaryContainer = c.selectedFill.compositeOver(c.canvas)
    val errorContainer = c.danger.copy(alpha = 0.08f).compositeOver(c.canvas)
    return if (c.isDark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.accentContrast,
            primaryContainer = primaryContainer,
            onPrimaryContainer = c.accentText,
            inversePrimary = spettroColors(dark = false, accent = c.accentKind).accent,
            secondary = c.textSecondary,
            onSecondary = c.canvas,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = c.textPrimary,
            tertiary = c.agentAccent,
            onTertiary = c.accentContrast,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = c.textPrimary,
            error = c.danger,
            onError = c.canvas,
            errorContainer = errorContainer,
            onErrorContainer = c.textPrimary,
            background = c.canvas,
            onBackground = c.textPrimary,
            surface = c.canvas,
            onSurface = c.textPrimary,
            surfaceVariant = c.surfaceRaised,
            onSurfaceVariant = c.textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = c.textPrimary,
            inverseOnSurface = c.canvas,
            outline = Color(0x33FFFFFF),
            outlineVariant = c.hairline,
            scrim = c.scrim,
            surfaceBright = Color(0xFF3A3A3C),
            surfaceDim = c.sidebar,
            surfaceContainerLowest = c.sidebar,
            surfaceContainerLow = Color(0xFF232325),
            surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceRaised,
            surfaceContainerHighest = Color(0xFF3A3A3C),
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.accentContrast,
            primaryContainer = primaryContainer,
            onPrimaryContainer = c.accentText,
            inversePrimary = spettroColors(dark = true, accent = c.accentKind).accent,
            secondary = c.textSecondary,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = c.textPrimary,
            tertiary = c.agentAccent,
            onTertiary = Color.White,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = c.textPrimary,
            error = c.danger,
            onError = Color.White,
            errorContainer = errorContainer,
            onErrorContainer = c.textPrimary,
            background = c.canvas,
            onBackground = c.textPrimary,
            surface = c.canvas,
            onSurface = c.textPrimary,
            surfaceVariant = c.userBubble,
            onSurfaceVariant = c.textSecondary,
            surfaceTint = Color.Transparent,
            inverseSurface = c.textPrimary,
            inverseOnSurface = c.canvas,
            outline = Color(0x33000000),
            outlineVariant = c.hairline,
            scrim = c.scrim,
            surfaceBright = c.surfaceRaised,
            surfaceDim = c.surfaceSunken,
            surfaceContainerLowest = c.surfaceRaised,
            surfaceContainerLow = c.surfaceRaised,
            surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.sidebar,
            surfaceContainerHighest = c.userBubble,
        )
    }
}

/** Whether [appearance] resolves to the dark scheme right now. */
@Composable
fun Appearance.isDark(): Boolean = when (this) {
    Appearance.Dark -> true
    Appearance.Light -> false
    Appearance.System -> isSystemInDarkTheme()
}

/**
 * The app theme: the desktop's palette — neutral greys with a Lilac or
 * Monochrome accent — on Material 3 Expressive components, with the quiet
 * standard motion scheme (the desktop's motion is all short ease-outs, never
 * springy). The palette is fixed: no dynamic (Material You) colour, so the
 * phone reads as the same product as the computer it drives. The appearance
 * and accent are the phone's own settings (`ThemePrefs`).
 */
@Composable
fun SpettroTheme(
    appearance: Appearance = Appearance.System,
    accent: Accent = Accent.Lilac,
    content: @Composable () -> Unit,
) {
    SpettroTheme(darkTheme = appearance.isDark(), accent = accent, content = content)
}

/** [SpettroTheme] with the scheme decided by the caller (previews, the screenshot harness). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpettroTheme(
    darkTheme: Boolean,
    accent: Accent = Accent.Lilac,
    content: @Composable () -> Unit,
) {
    val spettroColors = spettroColors(darkTheme, accent)
    val colorScheme = remember(spettroColors) { colorSchemeFor(spettroColors) }
    CompositionLocalProvider(LocalSpettroColors provides spettroColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.standard(),
            typography = SpettroTypography,
        ) {
            // Without a root Surface, LocalContentColor stays at its default
            // (black), so any Text without an explicit color is invisible on
            // the dark canvas.
            androidx.compose.material3.Surface(
                color = colorScheme.background,
                contentColor = colorScheme.onBackground,
                content = content,
            )
        }
    }
}
