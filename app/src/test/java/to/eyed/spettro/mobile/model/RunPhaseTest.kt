package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/** Ported from the desktop's runTicker tests (RunTicker.tsx `runPhase`). */
class RunPhaseTest {

    private fun user(text: String = "go", steering: ChatMessage.Steering? = null) =
        TranscriptItem.Message(ChatMessage(role = ChatMessage.Role.User, text = text, steering = steering))

    private fun assistant(text: String = "ok") =
        TranscriptItem.Message(ChatMessage(role = ChatMessage.Role.Assistant, text = text))

    private fun reasoning(streaming: Boolean) =
        TranscriptItem.Message(ChatMessage(role = ChatMessage.Role.Reasoning, text = "hm", isStreaming = streaming))

    private fun notice() =
        TranscriptItem.Message(ChatMessage(role = ChatMessage.Role.Notice(isError = false), text = "Mode changed"))

    private fun tool(id: String, status: AcpToolStatus) =
        TranscriptItem.Tool(ToolCallItem(id = id, title = "read", kind = "read", status = status))

    @Test
    fun waitingOnTheUserComesFirst() {
        val items = listOf(user(), tool("a", AcpToolStatus.IN_PROGRESS))
        assertEquals(RunPhase.AWAITING_APPROVAL, RunPhase.of(items, awaitingApproval = true))
        assertEquals(RunPhase.AWAITING_ANSWER, RunPhase.of(items, awaitingAnswer = true))
        // An approval outranks a question.
        assertEquals(RunPhase.AWAITING_APPROVAL, RunPhase.of(items, awaitingApproval = true, awaitingAnswer = true))
    }

    @Test
    fun aRunningToolIsWorking() {
        assertEquals(RunPhase.WORKING, RunPhase.of(listOf(user(), tool("a", AcpToolStatus.IN_PROGRESS))))
        assertEquals(RunPhase.WORKING, RunPhase.of(listOf(user(), tool("a", AcpToolStatus.PENDING), assistant())))
    }

    @Test
    fun streamingReasoningIsThinking() {
        assertEquals(RunPhase.THINKING, RunPhase.of(listOf(user(), reasoning(streaming = true))))
        // Finished reasoning leaves the model writing.
        assertEquals(RunPhase.WRITING, RunPhase.of(listOf(user(), reasoning(streaming = false))))
    }

    @Test
    fun everyToolSettledIsWritingTheAnswer() {
        val items = listOf(user(), tool("a", AcpToolStatus.COMPLETED), tool("b", AcpToolStatus.FAILED))
        assertEquals(RunPhase.WRITING, RunPhase.of(items))
        assertEquals(RunPhase.WRITING, RunPhase.of(listOf(user(), assistant())))
    }

    @Test
    fun aNoticeOrNothingYetIsWorking() {
        assertEquals(RunPhase.WORKING, RunPhase.of(listOf(user())))
        assertEquals(RunPhase.WORKING, RunPhase.of(listOf(user(), notice())))
        assertEquals(RunPhase.WORKING, RunPhase.of(emptyList()))
    }

    @Test
    fun onlyThisTurnCounts() {
        // A tool left running in an earlier turn says nothing about this one.
        val items = listOf(user("one"), tool("a", AcpToolStatus.IN_PROGRESS), assistant(), user("two"))
        assertEquals(RunPhase.WORKING, RunPhase.of(items))
        val settled = listOf(user("one"), tool("a", AcpToolStatus.IN_PROGRESS), user("two"), assistant())
        assertEquals(RunPhase.WRITING, RunPhase.of(settled))
    }

    @Test
    fun aSteerDoesNotStartATurn() {
        // The tool before the steer is still this turn's.
        val items = listOf(
            user("one"),
            tool("a", AcpToolStatus.IN_PROGRESS),
            user("also this", steering = ChatMessage.Steering.QUEUED),
        )
        assertEquals(RunPhase.WORKING, RunPhase.of(items))
        // And a steer is skipped when finding what came last.
        val writing = listOf(user("one"), assistant(), user("and", steering = ChatMessage.Steering.SENDING))
        assertEquals(RunPhase.WRITING, RunPhase.of(writing))
    }

    @Test
    fun elapsedReadsTheDesktopsWay() {
        assertEquals("0s", RunPhase.elapsed(0))
        assertEquals("0s", RunPhase.elapsed(-500))
        assertEquals("42s", RunPhase.elapsed(42_000))
        assertEquals("1m 0s", RunPhase.elapsed(59_600))
        assertEquals("3m 7s", RunPhase.elapsed(187_000))
        assertEquals("1h 12m", RunPhase.elapsed(4_320_000))
    }
}
