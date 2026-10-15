package to.eyed.spettro.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// The design tokens, ported from the desktop's design/theme.css: true neutral
// greys in both schemes, and one accent used sparingly — a soft lilac by
// default, or Monochrome, where the accent is simply the ink and nothing is
// tinted but the semantic colours. Every text token passes WCAG AA on the
// surfaces it is drawn on (ContrastTest holds them to it). Ultra's fire is
// the one warm colour and belongs to neither accent.
// ---------------------------------------------------------------------------

/** The accent setting. Lilac is the default; Monochrome makes the accent the ink. */
enum class Accent { Lilac, Mono }

/** The appearance setting. System follows the phone's light or dark setting. */
enum class Appearance { System, Light, Dark }

/**
 * The design tokens for one scheme and one accent, named after their CSS
 * custom properties (`--text-secondary` is [textSecondary]). Read via
 * [LocalSpettroColors]; provided by `SpettroTheme`.
 */
@Immutable
data class SpettroColors(
    val isDark: Boolean,
    val accentKind: Accent,

    // Neutrals.
    /** The screen background. */
    val canvas: Color,
    /** The tablet sidebar. */
    val sidebar: Color,
    /** Cards, menus, popovers, sheets, the composer. */
    val surfaceRaised: Color,
    /** Code and output blocks, segmented-control tracks. */
    val surfaceSunken: Color,
    /** The user's chat bubble: neutral, under [textPrimary]. */
    val userBubble: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** One-dp strokes: card borders, dividers, the composer outline. */
    val hairline: Color,
    /** A pressed or hovered row, an expanded chip. */
    val hoverFill: Color,
    /** The selected row; a disabled prominent button. */
    val selectedFill: Color,
    /** Behind a modal: dims the app without hiding where you were. */
    val scrim: Color,

    // The accent set — the only tokens Monochrome overrides.
    /** A fill: the prominent button, the slider, a dot. */
    val accent: Color,
    val accentHover: Color,
    /** Text on an [accent] fill (dark on lilac in the dark scheme). */
    val accentContrast: Color,
    /** The accent as text or an icon on a surface. */
    val accentText: Color,
    /** A tinted background. */
    val accentSoft: Color,
    val focusRing: Color,
    val selection: Color,
    /** The sub-agent rail: the accent, or grey in Monochrome. */
    val agentAccent: Color,
    /** A switch that is on, under an always-white thumb. */
    val switchOn: Color,
    /** Lilac itself, whatever the accent: the Accent picker's swatch. */
    val lilac: Color,
    val modePlan: Color,
    val modeCoding: Color,
    val modeAsk: Color,
    val planPro: Color,
    val planPlus: Color,

    // Semantic colours, shared by both accents.
    val success: Color,
    /** Danger as text or an icon. Too light to carry white text: see [dangerFill]. */
    val danger: Color,
    /** Amber: something is waiting on the user — an approval, an answer, "Needs you". */
    val warning: Color,
    /** Text on a filled warning surface. */
    val warningContrast: Color,
    /** A filled destructive control, under [dangerContrast]. */
    val dangerFill: Color,
    val dangerContrast: Color,
    val planMuted: Color,

    // The named agent colours (the TUI's), tuned per scheme. Information,
    // not decoration, so Monochrome keeps them.
    val modeBlue: Color,
    val modeGreen: Color,
    val modeCyan: Color,
    val modeYellow: Color,
    val modeMagenta: Color,
    val modePurple: Color,
    val modeRed: Color,

    // Ultra's fire, coolest to hottest, the same in both accents.
    val ultraEmber: Color,
    val ultraFlame: Color,
    val ultraSpark: Color,
    val ultraCore: Color,
    /** Fire as text, pulled into AA on the raised surfaces. */
    val ultraTextEmber: Color,
    val ultraTextFlame: Color,

    /** The activation phrase's six-stop glow, AA on the composer and the bubble. */
    val glowRamp: List<Color>,
) {
    /** Always white: QR codes must be dark-on-white, switch thumbs and the slider knob are paper. */
    val paper: Color get() = Color.White

    /** The added-line tint of a diff. */
    val diffAddedBg: Color get() = success.copy(alpha = 0.12f)

    /** The removed-line tint of a diff. */
    val diffRemovedBg: Color get() = danger.copy(alpha = 0.12f)

    /** In-chat primaries (send, a prompt's main answer): the ink, not the accent. */
    val ink: Color get() = textPrimary

    /** Text on an [ink] fill. */
    val onInk: Color get() = canvas

    /** The off track of a settings switch: text-secondary at 35%, flattened. */
    val switchOff: Color get() = textSecondary.copy(alpha = 0.35f).compositeOver(surfaceRaised)

    /**
     * The per-agent-mode tint, as the desktop's `modeColor()` resolves it:
     * a manifest colour name ("green", "cyan", …) or a mode id ("plan",
     * "coding", …). Plan, coding and ask are the mode chip's and follow the
     * accent; the named ones identify agents and stay in Monochrome. Null or
     * an unknown name falls back to [accentText].
     */
    fun modeColor(name: String?): Color = when (name?.lowercase()) {
        "blue" -> modeBlue
        "green" -> modeGreen
        "cyan" -> modeCyan
        "yellow" -> modeYellow
        "magenta" -> modeMagenta
        "purple" -> modePurple
        "red" -> modeRed
        "plan" -> modePlan
        "planning" -> modeBlue
        "coding", "code" -> modeCoding
        "chat", "ask" -> modeAsk
        else -> accentText
    }

    /**
     * [modeColor] as text on a tinted card. The desktop's values are already
     * AA on the canvas and the raised surface; this only nudges the few that
     * a card's tint wash pushes under the bar.
     */
    fun modeInk(name: String?): Color = readable(modeColor(name))

    // -----------------------------------------------------------------------
    // Legibility
    // -----------------------------------------------------------------------

    /**
     * [color] nudged away from [on] until it clears [minRatio], and returned
     * unchanged when it already does.
     *
     * Every token above is tuned by hand, so this is a safety net for the
     * grounds the tokens were not tuned for — chiefly an orchestration card,
     * which is the canvas under a 5–7% wash of its own tint. Blending toward
     * black on a light ground (or toward white on a dark one) moves contrast
     * monotonically, so a bisection finds the *smallest* adjustment that
     * clears the bar, and hue — the part carrying the meaning — is kept.
     *
     * @param minRatio 4.5 for text, 3.0 for the glyphs and cells that only
     *   have to be told apart from their background (WCAG 1.4.3 / 1.4.11).
     */
    fun readable(color: Color, on: Color = cardGround, minRatio: Float = 4.5f): Color {
        if (contrastRatio(color, on) >= minRatio) return color
        val target = if (on.luminance() > 0.5f) Color.Black else Color.White
        var low = 0f
        var high = 1f
        repeat(BISECTION_STEPS) {
            val mid = (low + high) / 2f
            if (contrastRatio(lerp(color, target, mid), on) >= minRatio) high = mid else low = mid
        }
        return lerp(color, target, high)
    }

    /**
     * The hardest ground these inks actually land on: the canvas under the
     * heaviest card wash, in whichever card tint pushes it *toward* the ink —
     * the darkest on a light theme, the lightest on a dark one.
     */
    val cardGround: Color
        get() {
            val tints = listOf(accent, agentAccent, danger)
            val worst = if (isDark) {
                tints.maxByOrNull { it.luminance() }
            } else {
                tints.minByOrNull { it.luminance() }
            } ?: accent
            return lerp(canvas, worst, CARD_WASH)
        }

    private companion object {
        /** A hair over the heaviest wash any card applies (7%), for margin. */
        const val CARD_WASH = 0.08f

        /** 12 halvings resolve the blend to ~0.02%, far finer than 8-bit
         *  channels can express, so the answer is exact in practice. */
        const val BISECTION_STEPS = 12
    }
}

/** WCAG 2.x relative-luminance contrast between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

private fun rgba(hex: Long, alpha: Float) = Color(hex).copy(alpha = alpha)

/** Dark scheme, lilac accent (theme.css `:root`). */
private val LilacDark = SpettroColors(
    isDark = true,
    accentKind = Accent.Lilac,
    canvas = Color(0xFF1C1C1E),
    sidebar = Color(0xFF161618),
    surfaceRaised = Color(0xFF2C2C2E),
    surfaceSunken = Color(0xFF161618),
    userBubble = Color(0xFF2A2A2D),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFAEAEB2),
    textTertiary = Color(0xFF98989D),
    hairline = rgba(0xFFFFFFFF, 0.10f),
    hoverFill = rgba(0xFFFFFFFF, 0.055f),
    selectedFill = rgba(0xFFFFFFFF, 0.09f),
    scrim = rgba(0xFF000000, 0.5f),
    accent = Color(0xFFB9A3F5),
    accentHover = Color(0xFFA993EA),
    accentContrast = Color(0xFF1B1530),
    accentText = Color(0xFFB9A3F5),
    accentSoft = rgba(0xFFB9A3F5, 0.16f),
    focusRing = rgba(0xFFB9A3F5, 0.6f),
    selection = rgba(0xFFB9A3F5, 0.32f),
    agentAccent = Color(0xFFB9A3F5),
    switchOn = Color(0xFFB9A3F5),
    lilac = Color(0xFFB9A3F5),
    modePlan = Color(0xFFB9A3F5),
    modeCoding = Color(0xFF6CC79C),
    modeAsk = Color(0xFF82B4F2),
    planPro = Color(0xFFC4B5FD),
    planPlus = Color(0xFF86EFAC),
    success = Color(0xFF4FBF7A),
    danger = Color(0xFFEF6F65),
    warning = Color(0xFFE0A43F),
    warningContrast = Color(0xFF1A1205),
    dangerFill = Color(0xFFD0433A),
    dangerContrast = Color(0xFFFFFFFF),
    planMuted = Color(0xFF98989D),
    modeBlue = Color(0xFFA99AF2),
    modeGreen = Color(0xFF6CC79C),
    modeCyan = Color(0xFF82B4F2),
    modeYellow = Color(0xFFE0A43F),
    modeMagenta = Color(0xFFD08AE8),
    modePurple = Color(0xFFC4A1F5),
    modeRed = Color(0xFFEF6F65),
    ultraEmber = Color(0xFFE0452B),
    ultraFlame = Color(0xFFFF8A3D),
    ultraSpark = Color(0xFFFFD36B),
    ultraCore = Color(0xFFFFFBEA),
    ultraTextEmber = Color(0xFFFF8064),
    ultraTextFlame = Color(0xFFFFA060),
    glowRamp = listOf(
        Color(0xFFA78BFA), Color(0xFFC084FC), Color(0xFFE879F9),
        Color(0xFFECFEFF), Color(0xFF38BDF8), Color(0xFF22D3EE),
    ),
)

/** Light scheme, lilac accent (theme.css `prefers-color-scheme: light`). */
private val LilacLight = SpettroColors(
    isDark = false,
    accentKind = Accent.Lilac,
    canvas = Color(0xFFFAFAFA),
    sidebar = Color(0xFFF4F4F5),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFF2F2F3),
    userBubble = Color(0xFFF0F0F2),
    textPrimary = Color(0xFF1D1D1F),
    textSecondary = Color(0xFF5C5C61),
    textTertiary = Color(0xFF6E6E73),
    hairline = rgba(0xFF000000, 0.10f),
    hoverFill = rgba(0xFF000000, 0.045f),
    selectedFill = rgba(0xFF000000, 0.07f),
    scrim = rgba(0xFF000000, 0.25f),
    accent = Color(0xFF7A66C6),
    accentHover = Color(0xFF6D59BA),
    accentContrast = Color(0xFFFFFFFF),
    accentText = Color(0xFF6852B8),
    accentSoft = rgba(0xFF7A66C6, 0.12f),
    focusRing = rgba(0xFF7A66C6, 0.5f),
    selection = rgba(0xFF7A66C6, 0.2f),
    agentAccent = Color(0xFF6852B8),
    switchOn = Color(0xFF7A66C6),
    lilac = Color(0xFF7A66C6),
    modePlan = Color(0xFF6852B8),
    modeCoding = Color(0xFF2A8058),
    modeAsk = Color(0xFF2F6DBF),
    planPro = Color(0xFF6852B8),
    planPlus = Color(0xFF16A34A),
    success = Color(0xFF237A48),
    danger = Color(0xFFC93A30),
    warning = Color(0xFF9A6210),
    warningContrast = Color(0xFF1A1205),
    dangerFill = Color(0xFFC93A30),
    dangerContrast = Color(0xFFFFFFFF),
    planMuted = Color(0xFF6E6E73),
    modeBlue = Color(0xFF6150C8),
    modeGreen = Color(0xFF2A8058),
    modeCyan = Color(0xFF2F6DBF),
    modeYellow = Color(0xFF9A6210),
    modeMagenta = Color(0xFFA3449C),
    modePurple = Color(0xFF7B4FC9),
    modeRed = Color(0xFFC93A30),
    ultraEmber = Color(0xFFC2341D),
    ultraFlame = Color(0xFFE8641C),
    ultraSpark = Color(0xFFF2A516),
    ultraCore = Color(0xFFFFF1C2),
    ultraTextEmber = Color(0xFFC2341D),
    ultraTextFlame = Color(0xFFB5470F),
    glowRamp = listOf(
        Color(0xFF6D28D9), Color(0xFF9333EA), Color(0xFFA21CAF),
        Color(0xFFBE185D), Color(0xFF0369A1), Color(0xFF0E7490),
    ),
)

/** Monochrome over the dark scheme: only the accent tokens change. */
private val MonoDark = LilacDark.copy(
    accentKind = Accent.Mono,
    accent = Color(0xFFF5F5F7),
    accentHover = Color(0xFFDCDCE0),
    accentContrast = Color(0xFF1D1D1F),
    accentText = Color(0xFFF5F5F7),
    accentSoft = rgba(0xFFFFFFFF, 0.10f),
    focusRing = rgba(0xFFFFFFFF, 0.5f),
    selection = rgba(0xFFFFFFFF, 0.22f),
    agentAccent = Color(0xFFAEAEB2),
    // Grey, not white: the switch's white thumb has to show on it.
    switchOn = Color(0xFF7C7C80),
    modePlan = Color(0xFFF5F5F7),
    modeCoding = Color(0xFFC7C7CC),
    modeAsk = Color(0xFFAEAEB2),
    planPro = Color(0xFFF5F5F7),
    planPlus = Color(0xFFF5F5F7),
    glowRamp = listOf(
        Color(0xFF98989D), Color(0xFFC7C7CC), Color(0xFFFFFFFF),
        Color(0xFFE5E5EA), Color(0xFFAEAEB2), Color(0xFF98989D),
    ),
)

/** Monochrome over the light scheme. */
private val MonoLight = LilacLight.copy(
    accentKind = Accent.Mono,
    accent = Color(0xFF1D1D1F),
    accentHover = Color(0xFF3A3A3C),
    accentContrast = Color(0xFFFFFFFF),
    accentText = Color(0xFF1D1D1F),
    accentSoft = rgba(0xFF000000, 0.065f),
    focusRing = rgba(0xFF000000, 0.4f),
    selection = rgba(0xFF000000, 0.13f),
    agentAccent = Color(0xFF5C5C61),
    switchOn = Color(0xFF1D1D1F),
    modePlan = Color(0xFF1D1D1F),
    modeCoding = Color(0xFF48484A),
    modeAsk = Color(0xFF636366),
    planPro = Color(0xFF1D1D1F),
    planPlus = Color(0xFF1D1D1F),
    glowRamp = listOf(
        Color(0xFF1D1D1F), Color(0xFF48484A), Color(0xFF6C6C70),
        Color(0xFF58585C), Color(0xFF2C2C2E), Color(0xFF1D1D1F),
    ),
)

/** The token set for one scheme and one accent. */
fun spettroColors(dark: Boolean, accent: Accent = Accent.Lilac): SpettroColors = when (accent) {
    Accent.Lilac -> if (dark) LilacDark else LilacLight
    Accent.Mono -> if (dark) MonoDark else MonoLight
}

/**
 * Spettro's design tokens. Defaults to light lilac so previews of leaf
 * composables work without a theme; `SpettroTheme` always provides the
 * set for the current appearance and accent.
 */
val LocalSpettroColors = staticCompositionLocalOf { LilacLight }
