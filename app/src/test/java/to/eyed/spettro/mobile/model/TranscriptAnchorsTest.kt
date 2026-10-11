package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The desktop's transcriptAnchors (TranscriptActions.tsx). */
class TranscriptAnchorsTest {

    private fun user(id: String) = TranscriptItem.Message(ChatMessage(id = id, role = ChatMessage.Role.User, text = "go"))
    private fun answer(id: String) = TranscriptItem.Message(ChatMessage(id = id, role = ChatMessage.Role.Assistant, text = "ok"))
    private fun error(id: String, endsTurn: Boolean = true) =
        TranscriptItem.Message(ChatMessage(id = id, role = ChatMessage.Role.Notice(isError = true), text = "boom", endsTurn = endsTurn))
    private fun info(id: String) =
        TranscriptItem.Message(ChatMessage(id = id, role = ChatMessage.Role.Notice(isError = false), text = "fyi", endsTurn = true))

    @Test
    fun theNewestUserMessageAndTheErrorThatEndedItsTurn() {
        val a = TranscriptAnchors.of(listOf(user("u1"), answer("a1"), user("u2"), error("e2"), answer("a2")), busy = false)
        assertEquals("u2", a.lastUserMessageId)
        assertEquals("e2", a.retryNoticeId)
    }

    @Test
    fun anErrorFromAnEarlierTurnOffersNothing() {
        val a = TranscriptAnchors.of(listOf(user("u1"), error("e1"), user("u2"), answer("a2")), busy = false)
        assertNull(a.retryNoticeId)
    }

    @Test
    fun nothingToRetryWhileBusyOrWithoutAPrompt() {
        assertNull(TranscriptAnchors.of(listOf(user("u1"), error("e1")), busy = true).retryNoticeId)
        assertNull(TranscriptAnchors.of(listOf(error("e1")), busy = false).retryNoticeId)
    }

    @Test
    fun anErrorThatEndedNoTurnIsNotRetried() {
        val a = TranscriptAnchors.of(listOf(user("u1"), error("e1", endsTurn = false), answer("a1")), busy = false)
        assertNull(a.retryNoticeId)
        assertNull(TranscriptAnchors.of(listOf(user("u1"), info("i1")), busy = false).retryNoticeId)
    }

    @Test
    fun theErrorATranscriptEndsOnCountsWithoutTheFlag() {
        val a = TranscriptAnchors.of(listOf(user("u1"), answer("a1"), error("e1", endsTurn = false)), busy = false)
        assertEquals("e1", a.retryNoticeId)
    }
}
