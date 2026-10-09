package to.eyed.spettro.mobile.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slider's moves as the CLI hears them, and when the meteor flies. Ported
 * from the desktop's thinkingSlider.test.tsx ("the Ultra stop", "the meteor"):
 * the queue and the trigger are pure here, so they are tested without a
 * screen.
 */
class ThinkingMovesTest {

    private val low = Thinking.LEVELS[0]
    private val medium = Thinking.LEVELS[1]
    private val high = Thinking.LEVELS[2]
    private val xHigh = Thinking.LEVELS[3]
    private val max = Thinking.LEVELS[4]

    /** A CLI that records every call, and can hold one open so ordering can be checked. */
    private class FakeCli(var ultraOn: Boolean = false) {
        val calls = mutableListOf<ConfigCall>()
        var holding = false
        var hold: CompletableDeferred<Unit>? = null
        var refuse: (ConfigCall) -> Boolean = { false }

        suspend fun send(call: ConfigCall) {
            calls += call
            if (holding) CompletableDeferred<Unit>().also { hold = it }.await()
            if (refuse(call)) throw IllegalStateException("Couldn't change Thinking.")
            if (call.configId == Thinking.ULTRA_ID) ultraOn = (call.value as ConfigValue.Bool).value
        }

        fun release() {
            holding = false
            hold?.complete(Unit)
        }
    }

    private fun CoroutineScope.committer(cli: FakeCli, spacingMs: Long = 0) =
        ThinkingCommitter(this, { cli.ultraOn }, cli::send, { spacingMs })

    /** Lets queued work run to completion. */
    private suspend fun settle() = repeat(10) { yield() }

    private fun thinking(value: String) = ConfigCall("thinking", ConfigValue.Str(value))
    private fun ultra(on: Boolean) = ConfigCall("ultra", ConfigValue.Bool(on))

    // MARK: The Ultra stop

    @Test
    fun `sets thinking high, then, once that is answered, ultracode on`() = runBlocking {
        val cli = FakeCli().apply { holding = true }
        val done = committer(cli).commit(Thinking.ULTRA)
        settle()
        // One call in flight; the second waits for its answer.
        assertEquals(listOf(thinking("high")), cli.calls)
        cli.release()
        assertTrue(done.await())
        assertEquals(listOf(thinking("high"), ultra(true)), cli.calls)
    }

    @Test
    fun `turns ultracode off first when leaving, then sets the level`() = runBlocking {
        val cli = FakeCli(ultraOn = true)
        assertTrue(committer(cli).commit(max).await())
        assertEquals(listOf(ultra(false), thinking("max")), cli.calls)
    }

    @Test
    fun `collapses moves made while one is in flight into the last`() = runBlocking {
        val cli = FakeCli().apply { holding = true }
        val queue = committer(cli)
        val first = queue.commit(medium) // sent, held
        settle()
        val second = queue.commit(high) // queued
        val third = queue.commit(xHigh) // replaces it
        // Only the run's own deferred lets the preview go.
        assertFalse(second.await())
        assertFalse(third.await())
        cli.release()
        assertTrue(first.await())
        assertEquals(listOf(thinking("medium"), thinking("x-high")), cli.calls)
    }

    @Test
    fun `reads Ultra from its own calls while running, and from the options once idle`() = runBlocking {
        val cli = FakeCli().apply { holding = true }
        val queue = committer(cli)
        val run = queue.commit(Thinking.ULTRA)
        settle()
        queue.commit(max) // queued behind Ultra's two calls
        cli.release()
        assertTrue(run.await())
        // Ultra went on in this run, so leaving it turns it off first.
        assertEquals(listOf(thinking("high"), ultra(true), ultra(false), thinking("max")), cli.calls)

        // Ultra turned on elsewhere (another device) while the queue was idle.
        cli.calls.clear()
        cli.ultraOn = true
        assertTrue(queue.commit(low).await())
        assertEquals(listOf(ultra(false), thinking("low")), cli.calls)
    }

    @Test
    fun `carries on after a refusal, which the host files as a notice`() = runBlocking {
        val cli = FakeCli().apply { refuse = { it.configId == Thinking.THINKING_ID } }
        assertTrue(committer(cli).commit(Thinking.ULTRA).await())
        assertEquals(listOf(thinking("high"), ultra(true)), cli.calls)
    }

    @Test
    fun `spaces the calls out for a host that answers before it has set anything`() = runBlocking {
        val cli = FakeCli()
        val times = mutableListOf<Long>()
        val queue = ThinkingCommitter(this, { cli.ultraOn }, { times += System.nanoTime(); cli.send(it) }, { 40L })
        assertTrue(queue.commit(Thinking.ULTRA).await())
        assertEquals(listOf(thinking("high"), ultra(true)), cli.calls)
        assertTrue((times[1] - times[0]) / 1_000_000 >= 40)
    }

    // MARK: The meteor

    private val count = 6
    private val ultraAt = 5

    @Test
    fun `plays when Ultra is reached, from the stop the thumb stood on`() {
        val idle = MeteorTrigger(lit = false, shown = 4)
        val lit = idle.observe(lit = true, shown = 5, count = count, reduced = false)
        assertEquals(MeteorFlight(from = 4), lit.flight)
        assertEquals(1, lit.key)
        assertFalse(lit.landed)
    }

    @Test
    fun `plays when Ultra arrives from elsewhere, from where the thumb was`() {
        // /ultra, or another device: the options jump from High to Ultra.
        val lit = MeteorTrigger(lit = false, shown = 2).observe(true, ultraAt, count, reduced = false)
        assertEquals(MeteorFlight(from = 2), lit.flight)
    }

    @Test
    fun `sets out from Low for a press on Ultra, and from Max for a drag onto it`() {
        // A press over a not-lit Ultra keeps the thumb where it stood (Low);
        // the release lights Ultra and the meteor leaves from there.
        val pressed = MeteorTrigger(lit = false, shown = 0).observe(false, 0, count, reduced = false)
        assertEquals(MeteorFlight(from = 0), pressed.observe(true, ultraAt, count, reduced = false).flight)
        // A drag Low → Max → Ultra left the thumb on Max.
        val dragged = MeteorTrigger(lit = false, shown = 0).observe(false, 4, count, reduced = false)
        assertEquals(MeteorFlight(from = 4), dragged.observe(true, ultraAt, count, reduced = false).flight)
    }

    @Test
    fun `does not play for a slider drawn with Ultra already on, or redrawn there`() {
        val mounted = MeteorTrigger(lit = true, shown = ultraAt)
        val redrawn = mounted.observe(true, ultraAt, count, reduced = false)
        assertSame(mounted, redrawn)
        assertNull(redrawn.flight)
    }

    @Test
    fun `does not play for a paused Ultra, and plays once the pause lifts`() {
        val paused = MeteorTrigger(lit = false, shown = ultraAt)
        assertNull(paused.observe(false, ultraAt, count, reduced = false).flight)
        // The thumb is already on Ultra: it flies in from the stop before.
        val lifted = paused.observe(true, ultraAt, count, reduced = false)
        assertEquals(MeteorFlight(from = 4), lifted.flight)
    }

    @Test
    fun `flies once while the options catch up one at a time`() {
        // The preview says Ultra from the first frame; the options' halfway
        // values (thinking high, ultra still off) never reach the trigger.
        var run = MeteorTrigger(lit = false, shown = 4).observe(true, ultraAt, count, reduced = false)
        val flight = run.flight
        repeat(3) { run = run.observe(true, ultraAt, count, reduced = false) }
        assertSame(flight, run.flight)
        assertEquals(1, run.key)
    }

    @Test
    fun `a drag off a lit Ultra and back flies nothing`() {
        var run = MeteorTrigger(lit = true, shown = ultraAt)
        run = run.observe(true, 4, count, reduced = false)
        run = run.observe(true, ultraAt, count, reduced = false)
        assertNull(run.flight)
        assertEquals(0, run.key)
    }

    @Test
    fun `lands, burns out, and lets go if Ultra goes dark mid-flight`() {
        var run = MeteorTrigger(lit = false, shown = 0).observe(true, ultraAt, count, reduced = false)
        run = run.land()
        assertTrue(run.landed)
        assertNotNull(run.flight)
        run = run.done()
        assertNull(run.flight)
        // A refusal takes Ultra away mid-flight: the flight goes with it.
        val refused = MeteorTrigger(lit = false, shown = 0)
            .observe(true, ultraAt, count, reduced = false)
            .observe(false, 2, count, reduced = false)
        assertNull(refused.flight)
    }

    @Test
    fun `glows up instead under reduced motion`() {
        val run = MeteorTrigger(lit = false, shown = 4).observe(true, ultraAt, count, reduced = true)
        assertNull(run.flight)
        assertTrue(run.glowIn)
        assertFalse(run.observe(false, 4, count, reduced = true).glowIn)
    }
}
