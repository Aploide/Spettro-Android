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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Spacing and radius tokens, 1:1 with `Theme.Spacing` / `Theme.Radius` in the
 * iOS app (a 4-point grid).
 */
object Dimens {
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp

    /** Small cards, code-block strokes, attachment thumbnails. */
    val radiusSm = 8.dp

    /** The default card radius. */
    val radiusMd = 12.dp

    /** The composer's outer rounded rectangle. */
    val radiusLg = 18.dp

    /** The user chat bubble. */
    val radiusBubble = 16.dp

    /** Pill-shaped input fields. */
    val radiusInputPill = 20.dp

    /** One-dp strokes everywhere a border appears. */
    val hairlineWidth = 1.dp
}

// ---------------------------------------------------------------------------
// Material color schemes. The brand palette is fixed — no dynamic color.
// primary = accent, background/surface = canvas, the surfaceContainer ladder
// sits around surfaceRaised, error = diffRemoved, outlineVariant = hairline.
// ---------------------------------------------------------------------------

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF23348F),
    secondary = Color(0xFF5A5F73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E8F2),
    onSecondaryContainer = Color(0xFF2E3242),
    tertiary = AgentAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFE4FE),
    onTertiaryContainer = Color(0xFF4A2B85),
    error = DiffRemoved,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1DF),
    onErrorContainer = Color(0xFF8C1210),
    background = CanvasLight,
    onBackground = Color(0xFF1A1A1A),
    surface = CanvasLight,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEFEFEC),
    onSurfaceVariant = Color(0xFF63635F),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2C2C2C),
    inverseOnSurface = Color(0xFFF2F2F0),
    inversePrimary = AccentDark,
    outline = Color(0x33000000),
    outlineVariant = HairlineLight,
    scrim = Color(0x66000000),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEDEDEA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SurfaceRaisedLight,
    surfaceContainer = SurfaceRaisedLight,
    surfaceContainerHigh = Color(0xFFF3F3F0),
    surfaceContainerHighest = Color(0xFFEDEDEA),
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E3A78),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB9BDCC),
    onSecondary = Color(0xFF23273A),
    secondaryContainer = Color(0xFF2A2E3E),
    onSecondaryContainer = Color(0xFFDDE0EC),
    tertiary = AgentAccent,
    onTertiary = Color(0xFF2C0A5E),
    tertiaryContainer = Color(0xFF432579),
    onTertiaryContainer = Color(0xFFEADDFF),
    error = DiffRemoved,
    onError = Color.White,
    errorContainer = Color(0xFF6E100E),
    onErrorContainer = Color(0xFFFFDAD6),
    background = CanvasDark,
    onBackground = Color(0xFFECECEC),
    surface = CanvasDark,
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA7A7A3),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFECECEC),
    inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = AccentLight,
    outline = Color(0x33FFFFFF),
    outlineVariant = HairlineDark,
    scrim = Color(0x99000000),
    surfaceBright = Color(0xFF333333),
    surfaceDim = Color(0xFF0E0E0E),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = SurfaceRaisedDark,
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF262626),
)

/**
 * The app theme: Material 3 Expressive with **dynamic (Material You) color**
 * drawn from the user's wallpaper (minSdk 33, so always available), plus the
 * expressive motion scheme. The Spettro tokens in [LocalSpettroColors] are
 * derived from the dynamic scheme so the whole app follows the user's
 * palette; only the semantic colors (diff green/red, agent purple, per-mode
 * tints) stay fixed — they carry meaning, not branding.
 *
 * Previews (`LocalInspectionMode`) keep the static brand palette: dynamic
 * schemes need a real device context.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpettroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val useDynamic = !androidx.compose.ui.platform.LocalInspectionMode.current
    val colorScheme = if (useDynamic) {
        val context = androidx.compose.ui.platform.LocalContext.current
        if (darkTheme) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }
    val spettroColors = if (useDynamic) {
        SpettroColors(
            isDark = darkTheme,
            accent = colorScheme.primary,
            canvas = colorScheme.background,
            surfaceRaised = colorScheme.surfaceContainer,
            hairline = if (darkTheme) HairlineDark else HairlineLight,
            diffAdded = DiffAdded,
            diffRemoved = DiffRemoved,
            agentAccent = AgentAccent,
            userBubble = colorScheme.primary,
        )
    } else {
        if (darkTheme) DarkSpettroColors else LightSpettroColors
    }
    CompositionLocalProvider(LocalSpettroColors provides spettroColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = SpettroTypography,
            content = content,
        )
    }
}
