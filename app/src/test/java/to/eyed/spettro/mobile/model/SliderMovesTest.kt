package to.eyed.spettro.mobile.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpConfigOption

/**
 * The slider's moves without a screen: what a press, a drag, a key and
 * TalkBack select, and how long the preview stands in for the options.
 * Ported from the desktop's thinkingSlider.test.tsx ("holds a burst that
 * comes back…", "lets go where the CLI is when it refuses…", "sends a held
 * key's stop once…", "goes nowhere further left from Off", the press cases),
 * on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SliderMovesTest {

    // Options shaped like internal/acp/config_options.go builds them.
    private fun options(thinking: String, ultra: Boolean) = listOf(
        AcpConfigOption(
            id = "thinking",
            name = "Thinking",
            kind = AcpConfigOption.Kind.Select(
                current = thinking,
                groups = listOf(
                    AcpConfigOption.OptionGroup(
                        null,
                        listOf("off", "low", "medium", "high", "x-high", "max").map { AcpConfigOption.Opt(it, it) },
                    ),
                ),
            ),
        ),
        AcpConfigOption(id = "ultra", name = "Ultra", kind = AcpConfigOption.Kind.Bool(ultra)),
    )

    private fun base(thinking: String, ultra: Boolean = false) = Thinking.state(options(thinking, ultra))!!

    private val low = 0
    private val medium = 1
    private val high = 2
    private val max = 4
    private val ultra = 5

    /** The slider over a fake queue whose answers the test hands out. */
    private class Rig(scope: TestScope, base: ThinkingState, disabled: Boolean = false) {
        val commits = mutableListOf<Pair<ThinkingStop, CompletableDeferred<Boolean>>>()
        val previews = mutableListOf<ThinkingStop?>()
        val moves = SliderMoves(
            scope = scope.backgroundScope,
            commit = { stop -> CompletableDeferred<Boolean>().also { commits += stop to it } },
            onPreview = { previews += it },
            base = base,
            disabled = disabled,
        )

        val sentIds get() = commits.map { it.first.id }

        /** The queue drains on the last move: it settles, the ones before it don't. */
        fun drain() {
            commits.forEachIndexed { i, (_, done) -> done.complete(i == commits.lastIndex) }
        }
    }

    // MARK: The preview

    @Test
    fun `holds a burst that comes back to Ultra until its calls are through`() = runTest {
        val rig = Rig(this, base("high", ultra = true))
        rig.moves.select(max)
        rig.moves.select(ultra)
        runCurrent()
        // Back where the options already are — but the calls aren't through.
        rig.moves.update(base("high", ultra = true), disabled = false)
        assertEquals(ultra, rig.moves.state(base("high", ultra = true)).index)
        assertEquals(Thinking.ULTRA, rig.moves.pending)
        // The first call's update lands: Ultra off, on the way back. The
        // thumb stays on Ultra (no relaunched meteor from High).
        rig.moves.update(base("high", ultra = false), disabled = false)
        assertEquals(ultra, rig.moves.state(base("high", ultra = false)).index)
        advanceTimeBy(5_000)
        assertEquals(Thinking.ULTRA, rig.moves.pending)
        // The calls are through and the options are back on Ultra: it lets go.
        rig.drain()
        runCurrent()
        rig.moves.update(base("high", ultra = true), disabled = false)
        assertNull(rig.moves.pending)
        assertEquals(listOf(Thinking.LEVELS[max], Thinking.ULTRA, null), rig.previews)
    }

    @Test
    fun `lets go where the CLI is when it refuses, a beat after the calls`() = runTest {
        val rig = Rig(this, base("high"))
        rig.moves.select(ultra)
        rig.drain()
        runCurrent()
        assertTrue(rig.moves.sent)
        // Refused: the options stay on High.
        advanceTimeBy(Thinking.SETTLE_MS - 1)
        assertEquals(Thinking.ULTRA, rig.moves.pending)
        advanceTimeBy(2)
        assertNull(rig.moves.pending)
        assertEquals(high, rig.moves.state(base("high")).index)
    }

    @Test
    fun `lets go at once once the options have the move`() = runTest {
        val rig = Rig(this, base("high"))
        rig.moves.select(max)
        rig.moves.update(base("max"), disabled = false)
        // Caught up, but not through: still previewing.
        assertEquals(Thinking.LEVELS[max], rig.moves.pending)
        rig.drain()
        runCurrent()
        assertNull(rig.moves.pending)
    }

    @Test
    fun `only the move the queue drains on lets the preview go`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.select(medium)
        rig.moves.select(max)
        rig.drain()
        runCurrent()
        advanceTimeBy(Thinking.SETTLE_MS + 1)
        // Medium's answer said nothing; Max's let the preview go.
        assertNull(rig.moves.pending)
        assertEquals(listOf("medium", "max"), rig.sentIds)
    }

    @Test
    fun `an older move settling does not let a newer one's preview go`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.select(medium)
        rig.commits[0].second.complete(true) // Medium drained on its own…
        rig.moves.select(high, held = true) // …just as a held key moved on.
        runCurrent()
        advanceTimeBy(Thinking.HELD_KEY_MS - 1)
        assertEquals(Thinking.LEVELS[high], rig.moves.pending)
        assertFalse(rig.moves.sent)
    }

    // MARK: Keys

    @Test
    fun `sends a held key's stop once, when the repeats stop`() = runTest {
        val rig = Rig(this, base("low"))
        assertTrue(rig.moves.key(SliderKey.Forward, repeat = false))
        assertEquals(listOf("medium"), rig.sentIds)
        // Held: the thumb follows each repeat, the CLI hears where it stops.
        repeat(3) {
            rig.moves.key(SliderKey.Forward, repeat = true)
            advanceTimeBy(33)
        }
        assertEquals(Thinking.LEVELS[max], rig.moves.pending)
        assertEquals(listOf("medium"), rig.sentIds)
        advanceTimeBy(Thinking.HELD_KEY_MS)
        assertEquals(listOf("medium", "max"), rig.sentIds)
    }

    @Test
    fun `sends a held key's stop at once when the key comes up`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.key(SliderKey.Forward, repeat = true)
        rig.moves.key(SliderKey.Forward, repeat = true)
        assertEquals(emptyList<String>(), rig.sentIds)
        rig.moves.keyUp()
        assertEquals(listOf("high"), rig.sentIds)
        // The timer it replaced sends nothing more.
        advanceTimeBy(Thinking.HELD_KEY_MS * 2)
        assertEquals(listOf("high"), rig.sentIds)
    }

    @Test
    fun `goes nowhere further left from Off, and Low to the right`() = runTest {
        val rig = Rig(this, base("off"))
        assertEquals(-1, rig.moves.state().index)
        assertFalse(rig.moves.key(SliderKey.Back, repeat = false))
        assertNull(rig.moves.pending)
        assertTrue(rig.moves.key(SliderKey.Forward, repeat = false))
        assertEquals(listOf("low"), rig.sentIds)
    }

    @Test
    fun `goes Home and End`() = runTest {
        val rig = Rig(this, base("medium"))
        rig.moves.key(SliderKey.End, repeat = false)
        assertEquals(Thinking.ULTRA, rig.moves.pending)
        rig.moves.key(SliderKey.Home, repeat = false)
        assertEquals(listOf("ultra", "low"), rig.sentIds)
    }

    @Test
    fun `a closed slider still sends a held key's stop, and lets its preview go`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.key(SliderKey.Forward, repeat = true)
        rig.moves.dispose()
        assertEquals(listOf("medium"), rig.sentIds)
        assertNull(rig.previews.last())
    }

    // MARK: TalkBack

    @Test
    fun `TalkBack's step up from Off is Low, and down from it nothing`() {
        // Off reports 0, as Low does: a step up asks for 1, a step down for 0.
        assertEquals(0, Thinking.a11yTarget(index = -1, target = 1f, last = 5))
        assertNull(Thinking.a11yTarget(index = -1, target = 0f, last = 5))
        assertEquals(3, Thinking.a11yTarget(index = 2, target = 3f, last = 5))
        assertEquals(5, Thinking.a11yTarget(index = 4, target = 9f, last = 5))
        assertEquals(0, Thinking.a11yTarget(index = 1, target = -1f, last = 5))
    }

    @Test
    fun `TalkBack moves from Off to Low`() = runTest {
        val rig = Rig(this, base("off"))
        assertTrue(rig.moves.setProgress(1f))
        assertEquals(listOf("low"), rig.sentIds)
    }

    // MARK: Presses and drags

    @Test
    fun `selects the stop under the finger on release`() = runTest {
        val rig = Rig(this, base("low"))
        assertTrue(rig.moves.pressAt(high))
        assertEquals(SliderPress(at = high, rest = high, moved = false), rig.moves.press)
        assertEquals(emptyList<String>(), rig.sentIds)
        rig.moves.release()
        assertNull(rig.moves.press)
        assertEquals(listOf("high"), rig.sentIds)
    }

    @Test
    fun `a press on Ultra keeps the thumb where it stood until release`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.pressAt(ultra)
        // The words name Ultra; the thumb waits on Low for the meteor.
        assertEquals(SliderPress(at = ultra, rest = low, moved = false), rig.moves.press)
        rig.moves.release()
        assertEquals(listOf("ultra"), rig.sentIds)
    }

    @Test
    fun `a drag onto Ultra rests the thumb on Max`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.pressAt(low)
        assertTrue(rig.moves.dragTo(max))
        assertEquals(SliderPress(at = max, rest = max, moved = true), rig.moves.press)
        assertFalse(rig.moves.dragTo(max)) // same stop: no new frame
        assertTrue(rig.moves.dragTo(ultra))
        assertEquals(SliderPress(at = ultra, rest = max, moved = true), rig.moves.press)
        rig.moves.release()
        assertEquals(listOf("ultra"), rig.sentIds)
    }

    @Test
    fun `a drag off a lit Ultra and back follows the finger`() = runTest {
        val rig = Rig(this, base("high", ultra = true))
        rig.moves.pressAt(ultra)
        rig.moves.dragTo(max)
        rig.moves.dragTo(ultra)
        assertEquals(SliderPress(at = ultra, rest = ultra, moved = true), rig.moves.press)
        // Released where it started: nothing to send.
        rig.moves.release()
        assertEquals(emptyList<String>(), rig.sentIds)
    }

    @Test
    fun `a cancelled press selects nothing`() = runTest {
        val rig = Rig(this, base("low"))
        rig.moves.pressAt(max)
        rig.moves.cancelPress()
        rig.moves.release()
        assertNull(rig.moves.press)
        assertEquals(emptyList<String>(), rig.sentIds)
    }

    @Test
    fun `a slider for a model that doesn't think takes nothing`() = runTest {
        val rig = Rig(this, base("high"), disabled = true)
        assertFalse(rig.moves.pressAt(max))
        assertNull(rig.moves.press)
        assertFalse(rig.moves.key(SliderKey.Forward, repeat = false))
        assertFalse(rig.moves.select(low))
        assertEquals(emptyList<String>(), rig.sentIds)
    }
}
