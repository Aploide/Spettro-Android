package to.eyed.spettro.mobile.coordinator.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSchedulerTest {

    /** A hand-wound clock: timers fire only when [advance] passes them. */
    private class FakeClock {
        var now = 0L
        private val timers = mutableListOf<Triple<Long, () -> Unit, BooleanArray>>()

        fun setTimer(delayMs: Long, run: () -> Unit): () -> Unit {
            val cancelled = booleanArrayOf(false)
            timers += Triple(now + delayMs, run, cancelled)
            return { cancelled[0] = true }
        }

        fun advance(ms: Long) {
            now += ms
            val due = timers.filter { it.first <= now }
            timers.removeAll(due)
            due.filterNot { it.third[0] }.forEach { it.second() }
        }
    }

    private val clock = FakeClock()
    private val scheduler = DeleteScheduler(setTimer = clock::setTimer)

    @Test
    fun `hides the chat at once and deletes it only when the window runs out`() {
        val committed = mutableListOf<String>()
        scheduler.schedule("c-1") { committed += "c-1" }
        assertEquals(setOf("c-1"), scheduler.pending.value)

        clock.advance(UNDO_WINDOW_MS - 1)
        assertTrue(committed.isEmpty())

        clock.advance(1)
        assertEquals(listOf("c-1"), committed)
        assertTrue(scheduler.pending.value.isEmpty())
    }

    @Test
    fun `undo puts the chat back and nothing is deleted`() {
        var committed = false
        scheduler.schedule("c-1") { committed = true }
        assertTrue(scheduler.undo("c-1"))
        assertTrue(scheduler.pending.value.isEmpty())

        clock.advance(UNDO_WINDOW_MS)
        assertFalse(committed)
    }

    @Test
    fun `undo after the delete ran says so`() {
        scheduler.schedule("c-1") {}
        clock.advance(UNDO_WINDOW_MS)
        assertFalse(scheduler.undo("c-1"))
    }

    @Test
    fun `flush deletes everything pending now, once`() {
        val committed = mutableListOf<String>()
        scheduler.schedule("c-1") { committed += "c-1" }
        scheduler.schedule("c-2") { committed += "c-2" }
        scheduler.flush()
        assertEquals(listOf("c-1", "c-2"), committed)

        clock.advance(UNDO_WINDOW_MS)
        assertEquals(2, committed.size)
    }

    @Test
    fun `cancelAll forgets pending deletes without running them`() {
        var committed = false
        scheduler.schedule("c-1") { committed = true }
        scheduler.cancelAll()
        clock.advance(UNDO_WINDOW_MS)
        assertFalse(committed)
        assertTrue(scheduler.pending.value.isEmpty())
    }

    @Test
    fun `scheduling the same chat again restarts its window`() {
        val committed = mutableListOf<Int>()
        scheduler.schedule("c-1") { committed += 1 }
        clock.advance(5_000)
        scheduler.schedule("c-1") { committed += 2 }
        clock.advance(5_000)
        assertTrue(committed.isEmpty())
        clock.advance(3_000)
        assertEquals(listOf(2), committed)
    }
}
