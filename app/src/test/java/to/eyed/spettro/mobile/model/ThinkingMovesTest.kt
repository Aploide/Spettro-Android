package to.eyed.spettro.mobile.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    private fun level(id: String) = Thinking.LEVELS.first { it.id == id }

    private val off = level("off")
    private val low = level("low")
    private val medium = level("medium")
    private val high = level("high")
    private val xHigh = level("x-high")
    private val max = level("max")

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
    fun `Ultra to Off, ultracode off, then thinking off`() = runBlocking {
        val cli = FakeCli(ultraOn = true)
        assertTrue(committer(cli).commit(off).await())
        assertEquals(listOf(ultra(false), thinking("off")), cli.calls)
    }

    @Test
    fun `Off to Ultra, thinking high, then ultracode on`() = runBlocking {
        val cli = FakeCli(ultraOn = false)
        assertTrue(committer(cli).commit(Thinking.ULTRA).await())
        assertEquals(listOf(thinking("high"), ultra(true)), cli.calls)
    }

    @Test
    fun `collapses moves made while one is in flight into the last`() = runBlocking {
        val cli = FakeCli().apply { holding = true }
        val queue = committer(cli)
        val first = queue.commit(medium) // sent, held
        settle()
        val second = queue.commit(high) // queued
        val third = queue.commit(xHigh) // replaces it
        cli.release()
        // Only the move the queue drained on lets its preview go.
        assertFalse(first.await())
        assertFalse(second.await())
        assertTrue(third.await())
        assertEquals(listOf(thinking("medium"), thinking("x-high")), cli.calls)
    }

    @Test
    fun `reads Ultra from its own calls while running, and from the options once idle`() = runBlocking {
        val cli = FakeCli().apply { holding = true }
        val queue = committer(cli)
        val run = queue.commit(Thinking.ULTRA)
        settle()
        val leave = queue.commit(max) // queued behind Ultra's two calls
        cli.release()
        assertFalse(run.await())
        assertTrue(leave.await())
        // Ultra went on in this run, so leaving it turns it off first.
        assertEquals(listOf(thinking("high"), ultra(true), ultra(false), thinking("max")), cli.calls)

        // Ultra turned on elsewhere (another device) while the queue was idle.
        cli.calls.clear()
        cli.ultraOn = true
        assertTrue(queue.commit(low).await())
        assertEquals(listOf(ultra(false), thinking("low")), cli.calls)
    }

    @Test
    fun `a slider opened again mid-run joins the run, and its move lets go`() = runBlocking {
        // Ultra picked on a slow link, the chat left and reopened, Low picked
        // there: one queue for the chat, so Low goes after Ultra's pair, never
        // between or beside it, and the new slider's move is the one that
        // drains it — its preview isn't left waiting on the old slider.
        val cli = FakeCli().apply { holding = true }
        val queue = committer(cli)
        val fromClosedSheet = queue.commit(Thinking.ULTRA)
        settle()
        val fromNewSheet = queue.commit(low)
        cli.release()
        assertTrue(fromNewSheet.await())
        assertFalse(fromClosedSheet.await())
        assertEquals(listOf(thinking("high"), ultra(true), ultra(false), thinking("low")), cli.calls)
        assertFalse(cli.ultraOn)
    }

    @Test
    fun `lets every waiter go, unsettled, if the run is cancelled`() = runBlocking {
        val cli = FakeCli().apply { holding = true }
        val scope = CoroutineScope(coroutineContext + Job())
        val queue = ThinkingCommitter(scope, { cli.ultraOn }, cli::send)
        val first = queue.commit(medium)
        settle()
        val second = queue.commit(high)
        scope.coroutineContext[Job]!!.cancel()
        assertFalse(first.await())
        assertFalse(second.await())
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
