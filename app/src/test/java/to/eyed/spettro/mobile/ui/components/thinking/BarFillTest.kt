package to.eyed.spettro.mobile.ui.components.thinking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.model.ThinkingStop

/**
 * How the bar is drawn at Off and around it, as numbers: the rules the
 * desktop's slider test asserts as classes (thinking-slider--off against
 * --unset, no thinking-tick--passed at Off, the fill's end).
 */
class BarFillTest {

    private val withOff = listOf("off", "low", "medium", "high", "x-high", "max", "ultra")
        .map { ThinkingStop(it, it, "", ultra = it == "ultra") }
    private val noOff = withOff.drop(1)

    /** The bar's own numbers: an 8dp cap, a 300dp span, seven stops. */
    private val cap = 8f
    private val span = 300f
    private val last = withOff.size - 1
    private fun frac(i: Int) = maxOf(0, i).toFloat() / last

    /** The stops lit as passed with the bar drawn at [at] and the thumb shown on [shown]. */
    private fun passed(at: Int, shown: Int = at, stops: List<ThinkingStop> = withOff): List<Int> {
        val empty = BarFill.isEmpty(at, stops)
        return stops.indices.filter { BarFill.passed(it, shown, frac(it), frac(at), empty) }
    }

    @Test
    fun `at Off the bar is empty, not even the cap filled, and no stop is passed`() {
        assertTrue(BarFill.isEmpty(0, withOff))
        assertEquals(0f, BarFill.end(cap, span, frac(0), emptied = 1f))
        assertEquals(emptyList<Int>(), passed(0))
    }

    @Test
    fun `at Low the fill covers the cap and the Off and Low stops are passed`() {
        assertFalse(BarFill.isEmpty(1, withOff))
        assertEquals(cap + span / last, BarFill.end(cap, span, frac(1), emptied = 0f))
        assertEquals(listOf(0, 1), passed(1))
    }

    @Test
    fun `sliding to Off, the cap retracts with the knob`() {
        // Halfway through the slide from Low: half the step and half the cap left.
        assertEquals(cap / 2 + span / last / 2, BarFill.end(cap, span, frac(1) / 2, emptied = 0.5f))
    }

    @Test
    fun `an unset level is empty too, with the hollow knob, while the Off stop wears the normal one`() {
        assertTrue(BarFill.isEmpty(-1, noOff))
        assertEquals(emptyList<Int>(), passed(-1, stops = noOff))
        assertEquals(ThumbLook.Unset, BarFill.thumbLook(-1, pausedLook = false, litLook = false))
        assertEquals(ThumbLook.Normal, BarFill.thumbLook(0, pausedLook = false, litLook = false))
        // Low first, from a CLI that lists no Off, is filled like any level.
        assertFalse(BarFill.isEmpty(0, noOff))
    }

    @Test
    fun `in flight from Off the bar stays empty until impact, so nothing ahead of the meteor is lit`() {
        // The bar is drawn at the stop the meteor left; the thumb is already on Ultra.
        assertEquals(emptyList<Int>(), passed(at = 0, shown = 6))
        // From Max, everything up to Max stays lit under the streak.
        assertEquals(listOf(0, 1, 2, 3, 4, 5), passed(at = 5, shown = 6))
    }

    @Test
    fun `lit and paused Ultra keep their knobs`() {
        assertEquals(ThumbLook.Lit, BarFill.thumbLook(6, pausedLook = false, litLook = true))
        assertEquals(ThumbLook.Paused, BarFill.thumbLook(6, pausedLook = true, litLook = false))
    }
}
