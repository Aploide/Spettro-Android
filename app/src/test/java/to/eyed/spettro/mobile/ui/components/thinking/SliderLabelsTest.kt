package to.eyed.spettro.mobile.ui.components.thinking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which stop names fit under the thinking slider, never shrunk, never overlapping. */
class SliderLabelsTest {

    private val names = listOf("Off", "Low", "Medium", "High", "Extra high", "Max", "Ultra")

    /** Roughly 11sp at [scale], in px at density 1: about 0.52 em a character. */
    private fun widths(scale: Float) = names.map { (it.length * 11f * scale * 0.52f).toInt() + 1 }

    /** Laid out as the slider does: centres on the rail between the two 8dp caps. */
    private fun centres(width: Int, count: Int = names.size): List<Float> {
        val cap = 8
        val span = width - 2 * cap
        return List(count) { i -> cap + span * (i.toFloat() / (count - 1)) }
    }

    private fun visible(width: Int, scale: Float, aim: Int, gap: Int = 4) =
        SliderLabels.visible(centres(width), widths(scale), width, aim, gap)

    private fun assertNoOverlap(width: Int, scale: Float, shown: Set<Int>, gap: Int = 4) {
        val spans = shown.map { SliderLabels.span(centres(width)[it], widths(scale)[it], width) }
        for (a in spans) for (b in spans) {
            if (a === b) continue
            assertTrue("$a and $b overlap", a.last + gap <= b.first || b.last + gap <= a.first)
        }
    }

    @Test
    fun everyNameFitsOnAWideSlider() {
        for (aim in -1..6) {
            assertEquals(names.indices.toSet(), visible(width = 600, scale = 1f, aim = aim))
        }
    }

    @Test
    fun atLargeTextOnANarrowPhoneTheMiddleNamesGiveWay() {
        // 360dp less the sheet's padding, at font scale 1.5: "High",
        // "Extra high" and "Max" ran into each other.
        val width = 328
        for (aim in -1..6) {
            val shown = visible(width, scale = 1.5f, aim = aim)
            assertNoOverlap(width, 1.5f, shown)
            if (aim >= 0) assertTrue("aim $aim shown", aim in shown)
            assertTrue(shown.size < names.size)
        }
        // Aimed at High: both ends stay, Extra high and Max give way.
        val high = visible(width, scale = 1.5f, aim = 3)
        assertTrue(0 in high && 6 in high && 3 in high)
        assertFalse(4 in high)
    }

    @Test
    fun theAimedNameWinsOverAnEndItRunsInto() {
        // So narrow that Max and Ultra collide: aimed at Max, Ultra goes.
        val width = 300
        val shown = visible(width, scale = 1.5f, aim = 5)
        assertTrue(5 in shown)
        assertFalse(6 in shown)
        assertTrue(0 in shown)
        assertNoOverlap(width, 1.5f, shown)
        // Aimed elsewhere, the end keeps its place and Max goes.
        val atLow = visible(width, scale = 1.5f, aim = 1)
        assertTrue(6 in atLow)
        assertFalse(5 in atLow)
    }

    @Test
    fun theNamesNearestTheAimAreTriedFirst() {
        // Low and Medium would run into each other: Medium, nearer the
        // aimed High, keeps its place; read left to right, Low would have.
        val centres = listOf(0f, 40f, 52f, 120f, 200f)
        val widths = List(5) { 20 }
        assertEquals(setOf(0, 2, 3, 4), SliderLabels.visible(centres, widths, width = 220, aim = 3, gapPx = 4))
        // With nothing aimed at they go left to right.
        assertEquals(setOf(0, 1, 3, 4), SliderLabels.visible(centres, widths, width = 220, aim = -1, gapPx = 4))
    }

    @Test
    fun neverEmptyAndNeverOverlapping() {
        for (width in listOf(40, 120, 200, 260, 328, 400, 800)) {
            for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                for (aim in -1..6) {
                    val shown = visible(width, scale, aim)
                    assertTrue("$width/$scale/$aim", shown.isNotEmpty())
                    if (aim >= 0) assertTrue(aim in shown)
                    assertNoOverlap(width, scale, shown)
                }
            }
        }
        assertEquals(emptySet<Int>(), SliderLabels.visible(emptyList(), emptyList(), 100, aim = -1, gapPx = 4))
    }

    @Test
    fun aNameIsKeptOnScreenAtTheEnds() {
        assertEquals(0..30, SliderLabels.span(centre = 8f, labelWidth = 30, width = 300))
        assertEquals(270..300, SliderLabels.span(centre = 292f, labelWidth = 30, width = 300))
        assertEquals(135..165, SliderLabels.span(centre = 150f, labelWidth = 30, width = 300))
    }
}
