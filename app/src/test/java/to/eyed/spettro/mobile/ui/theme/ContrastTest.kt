package to.eyed.spettro.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The palettes came from a terminal, which is always dark and always draws them
 * on near-black. Every reuse of them as ink is a contrast question, and the
 * answers are not guessable: "coding" green sits at 1.8:1 on the light canvas
 * while the same green is 15:1 on the dark one, and the *accent* — the one
 * colour that looked obviously fine — fails on both.
 *
 * A screenshot cannot catch any of this either. Unreadable text renders
 * perfectly; it is only unreadable to a person. So the ratios are asserted.
 */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Float {
        val hi = max(a.luminance(), b.luminance())
        val lo = min(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** The card grounds are the canvas under a 5–7% tint wash. */
    private fun wash(tint: Color, on: Color, alpha: Float) = Color(
        red = tint.red * alpha + on.red * (1 - alpha),
        green = tint.green * alpha + on.green * (1 - alpha),
        blue = tint.blue * alpha + on.blue * (1 - alpha),
    )

    private val themes = listOf("light" to LightSpettroColors, "dark" to DarkSpettroColors)

    private val modeNames = listOf(
        "blue", "green", "cyan", "yellow", "magenta", "purple", "red",
        "plan", "planning", "coding", "code", "chat", "ask",
        // The fallback: an agent spec the palette has never heard of, which is
        // the common case — "review", "verify", "general-purpose".
        null, "review", "verify", "general-purpose",
    )

    @Test
    fun everyModeInkIsReadableOnItsCanvas() {
        for ((name, colors) in themes) {
            for (mode in modeNames) {
                val ink = colors.modeInk(mode)
                val got = ratio(ink, colors.canvas)
                assertTrue(
                    "$name modeInk(${mode ?: "null"}) = $got:1, need 4.5",
                    got >= 4.5f,
                )
            }
        }
    }

    /** The tints are ink on a *tinted card*, not on the bare canvas. */
    @Test
    fun everyModeInkIsReadableOnTheCardGrounds() {
        for ((name, colors) in themes) {
            val grounds = listOf(
                "accent card" to wash(colors.accent, colors.canvas, 0.07f),
                "agent card" to wash(colors.agentAccent, colors.canvas, 0.07f),
            )
            for ((ground, bg) in grounds) {
                for (mode in modeNames) {
                    val got = ratio(colors.modeInk(mode), bg)
                    assertTrue(
                        "$name $ground modeInk(${mode ?: "null"}) = $got:1, need 4.5",
                        got >= 4.5f,
                    )
                }
            }
        }
    }

    @Test
    fun theSemanticInksAreReadable() {
        for ((name, colors) in themes) {
            val inks = listOf(
                "accentInk" to colors.accentInk,
                "agentInk" to colors.agentInk,
                "dangerInk" to colors.dangerInk,
                "successInk" to colors.successInk,
            )
            for ((label, ink) in inks) {
                val got = ratio(ink, colors.canvas)
                assertTrue("$name $label = $got:1, need 4.5", got >= 4.5f)
            }
        }
    }

    /** Glyphs and progress cells only have to be distinguishable, not read. */
    @Test
    fun theSemanticMarksClearTheNonTextBar() {
        for ((name, colors) in themes) {
            val marks = listOf(
                "successMark" to colors.successMark,
                "dangerMark" to colors.dangerMark,
                "accentMark" to colors.accentMark,
            )
            for ((label, mark) in marks) {
                val got = ratio(mark, colors.canvas)
                assertTrue("$name $label = $got:1, need 3.0", got >= 3.0f)
            }
        }
    }

    /**
     * The point of solving rather than guessing: a colour that already clears
     * the bar must come back untouched. The dark theme's whole palette does,
     * and adjusting it anyway would have quietly rewritten the app's look.
     */
    @Test
    fun aColourThatAlreadyPassesIsReturnedUnchanged() {
        val dark = DarkSpettroColors
        for (mode in listOf("green", "yellow", "purple", "red", "cyan", "magenta")) {
            assertEquals(
                "dark $mode should need no adjustment",
                dark.modeColor(mode),
                dark.modeInk(mode),
            )
        }
        // And the light theme leaves nothing alone, because nothing passes.
        val light = LightSpettroColors
        for (mode in listOf("green", "yellow", "purple")) {
            assertTrue(
                "light $mode should be adjusted",
                light.modeColor(mode) != light.modeInk(mode),
            )
        }
    }

    /** Hue is what carries the meaning — "coding" has to stay the green one. */
    @Test
    fun adjustingForContrastPreservesHue() {
        fun hue(c: Color): Float {
            val r = c.red
            val g = c.green
            val b = c.blue
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val d = maxC - minC
            if (d < 1e-6f) return 0f
            val h = when (maxC) {
                r -> ((g - b) / d + if (g < b) 6f else 0f)
                g -> ((b - r) / d + 2f)
                else -> ((r - g) / d + 4f)
            } * 60f
            return h
        }
        for ((name, colors) in themes) {
            for (mode in modeNames) {
                val raw = colors.modeColor(mode)
                val ink = colors.modeInk(mode)
                val drift = abs(hue(raw) - hue(ink))
                assertTrue(
                    "$name modeInk(${mode ?: "null"}) drifted ${drift}° in hue",
                    drift < 1f || drift > 359f,
                )
            }
        }
    }

    /** The solver must find the *smallest* adjustment that clears the bar on
     *  the ground it targets, not merely a safe one: over-darkening would
     *  flatten the whole palette toward black on the light theme. */
    @Test
    fun theAdjustmentIsMinimal() {
        val light = LightSpettroColors
        for (mode in modeNames) {
            val got = ratio(light.modeInk(mode), light.cardGround)
            assertTrue(
                "light modeInk(${mode ?: "null"}) overshot to $got:1",
                got < 4.5f * 1.05f,
            )
        }
    }

    @Test
    fun readableIsIdempotent() {
        for ((name, colors) in themes) {
            for (mode in modeNames) {
                val once = colors.modeInk(mode)
                assertEquals(
                    "$name modeInk(${mode ?: "null"}) is not stable under reapplication",
                    once,
                    colors.readable(once),
                )
            }
        }
    }
}
