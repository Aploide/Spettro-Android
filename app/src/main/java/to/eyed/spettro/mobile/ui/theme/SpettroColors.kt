package to.eyed.spettro.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
     * The palette is the terminal's, and the terminal is always dark. Used as
     * ink on the light canvas these hues are far too pale — the "coding" green
     * lands around 1.8:1 against it, which is unreadable rather than merely
     * quiet — so on the light appearance each one is darkened toward black.
     * That preserves the hue exactly, which is the part that carries the
     * meaning: "coding" is still the green one, it is just a green you can
     * read. The accent is left alone; it is already chosen per appearance.
     */
    fun modeColor(name: String?): Color {
        val raw = when (name?.lowercase()) {
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
            else -> return accent
        }
        return if (isDark) raw else lerp(raw, Color.Black, LIGHT_MODE_DARKEN)
    }

    private companion object {
        /** Enough to clear 4.5:1 against the light canvas for every hue in the
         *  palette, and no more — the tints should still read as tints. */
        const val LIGHT_MODE_DARKEN = 0.42f
    }
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
