package to.eyed.spettro.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------
// Raw brand palette — ported verbatim from ../Spettro/Spettro/Design/Theme.swift.
// The palette follows the synara desktop theme: a near-black surface, a soft
// indigo accent, and dedicated diff colors, adapted for both appearances.
// ---------------------------------------------------------------------------

internal val AccentLight = Color(0xFF526FFF)
internal val AccentDark = Color(0xFF6073CC)

internal val CanvasLight = Color(0xFFF9F9F7)
internal val CanvasDark = Color(0xFF0E0E0E)

/** Raised card / composer surface. Dark is white @ 11% flattened on black. */
internal val SurfaceRaisedLight = Color(0xFFFFFFFF)
internal val SurfaceRaisedDark = Color(0xFF1C1C1C)

/** 1dp borders: 8% of the opposing tone. */
internal val HairlineLight = Color(0x14000000)
internal val HairlineDark = Color(0x14FFFFFF)

// Same in both themes.
internal val DiffAdded = Color(0xFF40C977)
internal val DiffRemoved = Color(0xFFFA423E)
internal val AgentAccent = Color(0xFFAD7BF9)

/**
 * The non-Material design tokens, resolved for one appearance. Read via
 * [LocalSpettroColors]; provided by `SpettroTheme`.
 */
@Immutable
data class SpettroColors(
    val isDark: Boolean,
    /** Brand indigo — user bubbles, focus rings, selection, enabled actions. */
    val accent: Color,
    /** The screen background. */
    val canvas: Color,
    /** Slightly raised surface for cards, the composer, and popovers. */
    val surfaceRaised: Color,
    /** One-dp strokes: card borders, composer outline, code-block borders. */
    val hairline: Color,
    /** Additions: `+N` stat labels, diff lines, completed status. */
    val diffAdded: Color,
    /** Removals: `-N` labels, diff lines, failures, context ring > 90%. */
    val diffRemoved: Color,
    /** Sub-agents and skills — synara's purple. */
    val agentAccent: Color,
    /** Fill of the user's chat bubble (white text on top). */
    val userBubble: Color,
) {
    /**
     * The per-agent-mode tint, matching `modeColor()` in the TUI's styles.go
     * so the same mode reads as the same color in every front-end. Accepts a
     * manifest color name ("green", "cyan", ...) or a mode id ("plan",
     * "coding", ...) — the same fallback chain the TUI applies. Null or an
     * unknown name falls back to the accent.
     *
     * The raw hue, unadjusted. Anything that draws it as *text* wants
     * [modeInk] instead: this palette is the terminal's, and the terminal is
     * always dark.
     */
    fun modeColor(name: String?): Color = when (name?.lowercase()) {
        "blue" -> Color(0xFFA78BFA)
        "green" -> Color(0xFF34D399)
        "cyan" -> Color(0xFF60A5FA)
        "yellow" -> Color(0xFFF59E0B)
        "magenta" -> Color(0xFFC084FC)
        "purple" -> Color(0xFFBD93F9)
        "red" -> Color(0xFFEF4444)
        // Mode-name fallbacks, as in the TUI.
        "plan" -> Color(0xFFBD93F9)
        "planning" -> Color(0xFFA78BFA)
        "coding", "code" -> Color(0xFF34D399)
        "chat", "ask" -> Color(0xFF60A5FA)
        else -> accent
    }

    /** [modeColor], made legible as small text on this appearance's canvas. */
    fun modeInk(name: String?): Color = readable(modeColor(name))

    // -----------------------------------------------------------------------
    // Legibility
    // -----------------------------------------------------------------------

    /**
     * [color] nudged away from [on] until it clears [minRatio], and returned
     * unchanged when it already does.
     *
     * The brand and mode palettes were drawn for a terminal, which is always
     * dark and always renders them on near-black. Reused as ink on the light
     * canvas they are far too pale — "coding" green lands at 1.8:1, which is
     * not quiet but unreadable — and even on the dark canvas the two accents
     * fall short of 4.5:1 for text. A fixed darkening factor per appearance
     * was the first fix and it was wrong twice over: it did nothing for the
     * accent, and it applied the same correction to hues that needed very
     * different amounts.
     *
     * So the amount is solved for instead of guessed. Blending toward black on
     * a light ground (or toward white on a dark one) moves contrast
     * monotonically, so a bisection finds the *smallest* adjustment that
     * clears the bar. Hue is preserved exactly, which is the part carrying the
     * meaning: "coding" stays the green one, it just becomes a green you can
     * read.
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
     * The hardest ground these inks actually land on.
     *
     * Solving against the bare canvas is not enough, and the amount it is short
     * by is not small: an orchestration card is the canvas under a 5–7% wash of
     * its own tint, and on the light theme that wash costs about 8% of the
     * ratio — an ink solved to exactly 4.5:1 on the canvas arrives at 4.16:1 on
     * the card. So the target is the washed ground, using whichever card tint
     * pushes it *toward* the ink and therefore hurts most: the darkest one on a
     * light theme, the lightest on a dark one. Everything then clears the bar
     * on the plain canvas too, with a little to spare.
     */
    val cardGround: Color
        get() {
            val tints = listOf(accent, agentAccent, diffRemoved)
            val worst = if (isDark) {
                tints.maxByOrNull { it.luminance() }
            } else {
                tints.minByOrNull { it.luminance() }
            } ?: accent
            return lerp(canvas, worst, CARD_WASH)
        }

    /** The accent as text. */
    val accentInk: Color get() = readable(accent)

    /** The sub-agent purple as text. */
    val agentInk: Color get() = readable(agentAccent)

    /** Failure red as text — reasons, "N failed", error rows. */
    val dangerInk: Color get() = readable(diffRemoved)

    /** Success green as text. */
    val successInk: Color get() = readable(diffAdded)

    /** Success green as a mark: a status glyph or a progress cell, which only
     *  has to be distinguishable rather than readable. */
    val successMark: Color get() = readable(diffAdded, minRatio = MARK_RATIO)

    /** Failure red as a mark. */
    val dangerMark: Color get() = readable(diffRemoved, minRatio = MARK_RATIO)

    /** The accent as a mark — a running cell, a spinner. */
    val accentMark: Color get() = readable(accent, minRatio = MARK_RATIO)

    private companion object {
        /** WCAG 1.4.11: non-text content that carries meaning. */
        const val MARK_RATIO = 3f

        /** A hair over the heaviest wash any card applies (7%), for margin. */
        const val CARD_WASH = 0.08f

        /** 12 halvings resolve the blend to ~0.02%, far finer than 8-bit
         *  channels can express, so the answer is exact in practice. */
        const val BISECTION_STEPS = 12
    }
}

/** WCAG 2.x relative-luminance contrast between two opaque colors. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

val LightSpettroColors = SpettroColors(
    isDark = false,
    accent = AccentLight,
    canvas = CanvasLight,
    surfaceRaised = SurfaceRaisedLight,
    hairline = HairlineLight,
    diffAdded = DiffAdded,
    diffRemoved = DiffRemoved,
    agentAccent = AgentAccent,
    userBubble = AccentLight,
)

val DarkSpettroColors = SpettroColors(
    isDark = true,
    accent = AccentDark,
    canvas = CanvasDark,
    surfaceRaised = SurfaceRaisedDark,
    hairline = HairlineDark,
    diffAdded = DiffAdded,
    diffRemoved = DiffRemoved,
    agentAccent = AgentAccent,
    userBubble = AccentDark,
)

/**
 * Spettro's non-Material tokens. Defaults to light so previews of leaf
 * composables work without a theme; `SpettroTheme` always provides the
 * appearance-correct set.
 */
val LocalSpettroColors = staticCompositionLocalOf { LightSpettroColors }
