package to.eyed.spettro.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The palette is the desktop's, and text has to pass WCAG AA on every ground
 * it is drawn on, in both accents and both schemes — the bare surfaces, and
 * the composite grounds the screens build on them (a selected sidebar row,
 * a pill's wash, the error card). A screenshot cannot check that —
 * unreadable text renders perfectly; it is only unreadable to a person — so
 * the ratios are asserted, over (Lilac | Monochrome) × (light | dark).
 *
 * Where a token falls short on a composite (the desktop has the same values),
 * the screen draws it through [SpettroColors.readable] over that ground, and
 * the test checks the ink the screen actually draws. A pressed row's ripple
 * (hoverFill) is momentary and is not held to the bar.
 */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Float {
        val hi = max(a.luminance(), b.luminance())
        val lo = min(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** The card grounds are the canvas under a 5–7% tint wash. */
    private fun wash(tint: Color, on: Color, alpha: Float) = tint.copy(alpha = alpha).compositeOver(on)

    private val themes = Accent.entries.flatMap { accent ->
        listOf(false, true).map { dark ->
            "${accent.name.lowercase()} ${if (dark) "dark" else "light"}" to spettroColors(dark, accent)
        }
    }

    /** The surfaces text is drawn on, per scheme. */
    private fun grounds(c: SpettroColors) = listOf(
        "canvas" to c.canvas,
        "raised" to c.surfaceRaised,
        "sidebar" to c.sidebar,
        "sunken" to c.surfaceSunken,
    )

    private fun assertAA(label: String, ink: Color, ground: Color, need: Float = 4.5f) {
        val got = ratio(ink, ground)
        assertTrue("$label = $got:1, need $need", got >= need)
    }

    private val modeNames = listOf(
        "blue", "green", "cyan", "yellow", "magenta", "purple", "red",
        "plan", "planning", "coding", "code", "chat", "ask",
        // The fallback: an agent spec the palette has never heard of, which is
        // the common case — "review", "verify", "general-purpose".
        null, "review", "verify", "general-purpose",
    )

    @Test
    fun fourDistinctTokenSets() {
        assertEquals(4, themes.map { it.second }.toSet().size)
        for ((name, c) in themes) {
            assertEquals(name, name.endsWith("dark"), c.isDark)
        }
    }

    @Test
    fun theTextTokensPassOnEverySurface() {
        for ((name, c) in themes) {
            for ((ground, bg) in grounds(c)) {
                assertAA("$name textPrimary on $ground", c.textPrimary, bg)
                assertAA("$name textSecondary on $ground", c.textSecondary, bg)
                assertAA("$name textTertiary on $ground", c.textTertiary, bg)
            }
            // The user bubble carries primary text.
            assertAA("$name textPrimary on the bubble", c.textPrimary, c.userBubble)
        }
    }

    /**
     * The grounds the screens composite: a selected chat row (the canvas on a
     * phone, the sidebar on a tablet) and the "Needs you" pill's 16% amber
     * over each, as ChatRow and Pill build them.
     */
    @Test
    fun theListInksPassOnTheirCompositeGrounds() {
        for ((name, c) in themes) {
            for ((label, list) in listOf("canvas" to c.canvas, "sidebar" to c.sidebar)) {
                for (selected in listOf(false, true)) {
                    val row = if (selected) c.selectedFill.compositeOver(list) else list
                    val where = "${if (selected) "selected" else "plain"} row on $label"
                    assertAA("$name textPrimary on $where", c.textPrimary, row)
                    assertAA("$name textSecondary on $where", c.textSecondary, row)
                    // What ChatRow draws its preview, time and count in.
                    assertAA("$name quiet ink on $where", c.readable(c.textTertiary, on = row), row)
                    // What Pill draws "Needs you" in.
                    val pill = c.warning.copy(alpha = 0.16f).compositeOver(row)
                    assertAA("$name Needs you on $where", c.readable(c.warning, on = pill), pill)
                }
            }
        }
    }

    /** The nudge is needed exactly where the raw tokens fall short, and only there. */
    @Test
    fun theLightSidebarIsWhereTheRawTokensFallShort() {
        val c = spettroColors(dark = false, accent = Accent.Lilac)
        val selectedRow = c.selectedFill.compositeOver(c.sidebar)
        assertTrue(ratio(c.textTertiary, selectedRow) < 4.5f)
        assertNotEquals(c.textTertiary, c.readable(c.textTertiary, on = selectedRow))
        val pill = c.warning.copy(alpha = 0.16f).compositeOver(c.canvas)
        assertTrue(ratio(c.warning, pill) < 4.5f)
        // On a bare canvas the tertiary ink is left alone.
        assertEquals(c.textTertiary, c.readable(c.textTertiary, on = c.canvas))
    }

    /**
     * The error card (SpettroCard's Error variant, 8% danger over the
     * canvas) carries its sentence in the primary ink and only its icon in
     * danger, which as a glyph needs 3:1.
     */
    @Test
    fun theErrorCardReads() {
        for ((name, c) in themes) {
            val card = wash(c.danger, c.canvas, 0.08f)
            assertAA("$name textPrimary on the error card", c.textPrimary, card)
            assertAA("$name textSecondary on the error card", c.textSecondary, card)
            assertAA("$name danger icon on the error card", c.danger, card, need = 3f)
        }
    }

    @Test
    fun accentContrastReadsOnTheAccentFill() {
        for ((name, c) in themes) {
            assertAA("$name accentContrast on accent", c.accentContrast, c.accent)
        }
    }

    /** White on lilac fails in the dark scheme — the reason accentContrast exists. */
    @Test
    fun whiteOnTheDarkLilacFillWouldFail() {
        val c = spettroColors(dark = true, accent = Accent.Lilac)
        assertTrue(ratio(Color.White, c.accent) < 4.5f)
        assertNotEquals(Color.White, c.accentContrast)
    }

    @Test
    fun theInkPrimaryReadsOnItself() {
        for ((name, c) in themes) {
            assertAA("$name onInk on ink", c.onInk, c.ink)
        }
    }

    @Test
    fun theSemanticInksAreReadable() {
        for ((name, c) in themes) {
            val inks = listOf(
                "accentText" to c.accentText,
                "agentAccent" to c.agentAccent,
                "danger" to c.danger,
                "success" to c.success,
                "warning" to c.warning,
                "ultraTextEmber" to c.ultraTextEmber,
                "ultraTextFlame" to c.ultraTextFlame,
            )
            for ((label, ink) in inks) {
                assertAA("$name $label on canvas", ink, c.canvas)
                assertAA("$name $label on raised", ink, c.surfaceRaised)
            }
        }
    }

    @Test
    fun filledControlsCarryTheirLabels() {
        for ((name, c) in themes) {
            assertAA("$name dangerContrast on dangerFill", c.dangerContrast, c.dangerFill)
            // The desktop only fills with amber in the dark scheme.
            if (c.isDark) assertAA("$name warningContrast on warning", c.warningContrast, c.warning)
        }
    }

    @Test
    fun everyModeColorIsReadableOnTheCanvas() {
        for ((name, c) in themes) {
            for (mode in modeNames) {
                assertAA("$name modeColor(${mode ?: "null"})", c.modeColor(mode), c.canvas)
            }
        }
    }

    /** The tints are ink on a *tinted card*, not on the bare canvas. */
    @Test
    fun everyModeInkIsReadableOnTheCardGrounds() {
        for ((name, c) in themes) {
            val grounds = listOf(
                "accent card" to wash(c.accent, c.canvas, 0.07f),
                "agent card" to wash(c.agentAccent, c.canvas, 0.07f),
                "danger card" to wash(c.danger, c.canvas, 0.07f),
            )
            for ((ground, bg) in grounds) {
                for (mode in modeNames) {
                    assertAA("$name $ground modeInk(${mode ?: "null"})", c.modeInk(mode), bg)
                }
            }
        }
    }

    /** Plan, coding and ask follow the accent: Monochrome turns them into greys. */
    @Test
    fun monochromeGreysTheModeChipButKeepsTheAgentColours() {
        for (dark in listOf(false, true)) {
            val lilac = spettroColors(dark, Accent.Lilac)
            val mono = spettroColors(dark, Accent.Mono)
            for (mode in listOf("plan", "coding", "ask")) {
                val c = mono.modeColor(mode)
                assertTrue("mono $mode should be grey", abs(c.red - c.green) < 0.02f && abs(c.green - c.blue) < 0.02f)
                assertNotEquals(lilac.modeColor(mode), c)
            }
            for (mode in listOf("green", "cyan", "magenta", "red")) {
                assertEquals(lilac.modeColor(mode), mono.modeColor(mode))
            }
            // The neutrals, the semantic colours and Ultra's fire are shared.
            assertEquals(lilac.canvas, mono.canvas)
            assertEquals(lilac.danger, mono.danger)
            assertEquals(lilac.ultraFlame, mono.ultraFlame)
            // The picker's lilac swatch is lilac whatever the accent.
            assertEquals(lilac.lilac, mono.lilac)
        }
    }

    /** The tokens are hand-tuned: a colour that already passes must come back untouched. */
    @Test
    fun aColourThatAlreadyPassesIsReturnedUnchanged() {
        for ((name, c) in themes) {
            for (token in listOf(c.textPrimary, c.textSecondary, c.success, c.accentText)) {
                assertEquals(name, token, c.readable(token, on = c.canvas))
            }
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
            return when (maxC) {
                r -> ((g - b) / d + if (g < b) 6f else 0f)
                g -> ((b - r) / d + 2f)
                else -> ((r - g) / d + 4f)
            } * 60f
        }
        for ((name, c) in themes) {
            for (mode in modeNames) {
                val drift = abs(hue(c.modeColor(mode)) - hue(c.modeInk(mode)))
                assertTrue(
                    "$name modeInk(${mode ?: "null"}) drifted ${drift}° in hue",
                    drift < 1f || drift > 359f,
                )
            }
        }
    }

    /** The solver finds the *smallest* adjustment, not merely a safe one. */
    @Test
    fun theAdjustmentIsMinimal() {
        for ((name, c) in themes) {
            for (mode in modeNames) {
                val raw = c.modeColor(mode)
                val ink = c.modeInk(mode)
                if (raw == ink) continue
                val got = ratio(ink, c.cardGround)
                assertTrue("$name modeInk(${mode ?: "null"}) overshot to $got:1", got < 4.5f * 1.05f)
            }
        }
    }

    @Test
    fun readableIsIdempotent() {
        for ((name, c) in themes) {
            for (mode in modeNames) {
                val once = c.modeInk(mode)
                assertEquals("$name modeInk(${mode ?: "null"}) is not stable", once, c.readable(once))
            }
        }
    }
}
