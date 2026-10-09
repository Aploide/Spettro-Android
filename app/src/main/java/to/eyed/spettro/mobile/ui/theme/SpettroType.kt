package to.eyed.spettro.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale, after the desktop's `--fs-*` and the sizes its views use. The
 * system face (Roboto here, `system-ui` there), nothing bundled. Weights:
 * 400 for prose, 500 for labels, chips and buttons, 600 for titles and
 * section eyebrows.
 */
val SpettroTypography = Typography(
    // The greeting ("What should we build?"): 28 / 500 / −0.01em.
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.28).sp,
    ),
    // Onboarding and setup titles: 24 / 600 / −0.01em.
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.24).sp,
    ),
    // Screen, sheet and modal titles: 17 / 600.
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    // Card, prompt and confirm-dialog titles: 15 / 600.
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    // Transcript prose, the user bubble, the composer: 15 at 1.6.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    // UI body: rows, captions, values (the desktop's 13px default).
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    // Buttons and chips.
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    // Meta: times, counts, tags.
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    ),
)

/** The composer's input: prose size on a tighter 22sp line. */
val ComposerText = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

/**
 * A section eyebrow above a grouped card: 11 / 600 / 0.05em. Callers pass
 * sentence case ("Paired devices"); the uppercasing happens at render time,
 * as the desktop does it in CSS.
 */
val Eyebrow = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.55.sp,
)

/** Code blocks: mono 13 / 1.55. */
val MonoBody = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

/** A tool call's argument: mono 12.5. */
val MonoTool = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
)

/** Output blocks, file paths in chips, slash-command rows: mono 12 / 1.5. */
val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)

/**
 * OpenType tabular-numerals feature for numeric readouts (elapsed time, token
 * counts, context %, git stat). Apply as
 * `TextStyle(fontFeatureSettings = TabularNums)`.
 */
const val TabularNums = "tnum"
