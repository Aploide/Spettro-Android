package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.model.Todos.Status

/** Ported from the desktop's todoList tests (TodoList.tsx). */
class TodosTest {

    private fun entries(vararg plan: Pair<String, String>) = plan.map { (content, status) -> Todos.entry(AcpPlanEntry(content, status)) }

    @Test
    fun blockedIsSplitOffTheText() {
        val entry = Todos.entry(AcpPlanEntry("Wire the cache (blocked)", "pending"))
        assertEquals("Wire the cache", entry.text)
        assertEquals(true, entry.blocked)
        assertEquals(false, Todos.entry(AcpPlanEntry("Wire the cache", "pending")).blocked)
        // Only as a suffix.
        assertEquals(false, Todos.entry(AcpPlanEntry("Explain (blocked) states", "pending")).blocked)
    }

    @Test
    fun unknownStatusesArePending() {
        assertEquals(Status.PENDING, Todos.entry(AcpPlanEntry("a", "cancelled")).status)
        assertEquals(Status.IN_PROGRESS, Todos.entry(AcpPlanEntry("a", "in_progress")).status)
        assertEquals(Status.COMPLETED, Todos.entry(AcpPlanEntry("a", "completed")).status)
    }

    @Test
    fun summaryCountsWhatIsDone() {
        assertEquals("1 of 3 tasks done", Todos.summary(entries("a" to "completed", "b" to "in_progress", "c" to "pending")))
        assertEquals("All 2 tasks done", Todos.summary(entries("a" to "completed", "b" to "completed")))
        assertEquals("1 task done", Todos.summary(entries("a" to "completed")))
        assertEquals("0 of 1 task done", Todos.summary(entries("a" to "pending")))
    }

    @Test
    fun currentIsTheTaskUnderWayElseTheNextUnblocked() {
        assertEquals("Now: b", Todos.current(entries("a" to "completed", "b" to "in_progress", "c" to "pending")))
        assertEquals("Next: c", Todos.current(entries("a" to "completed", "b (blocked)" to "pending", "c" to "pending")))
        assertNull(Todos.current(entries("a" to "completed")))
        assertNull(Todos.current(entries("a (blocked)" to "pending")))
    }
}
