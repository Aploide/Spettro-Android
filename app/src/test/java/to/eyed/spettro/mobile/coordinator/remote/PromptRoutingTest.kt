package to.eyed.spettro.mobile.coordinator.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpQuestionItem
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest

/** Which prompts pop up where (desktop permissionCard.test.tsx "the prompt dock"). */
class PromptRoutingTest {

    private fun permission(id: String, chatID: String?) = PendingPermission(
        promptID = id,
        chatID = chatID,
        request = AcpPermissionRequest(sessionId = "s", title = "Run make"),
        questionForm = null,
    )

    private fun question(id: String, chatID: String?) = PendingQuestion(
        promptID = id,
        chatID = chatID,
        request = AcpQuestionRequest(
            sessionId = "s",
            questions = listOf(AcpQuestionItem(id = "q", header = "Q", question = "Which?")),
            transport = AcpQuestionRequest.Transport.ExtensionCall(2),
        ),
    )

    private val chats = listOf("C1", "c2")
    private val permissions = listOf(permission("other", "c2"), permission("mine", "c1"), permission("loose", null))
    private val questions = listOf(question("q-other", "c2"), question("q-mine", "C1"))

    @Test
    fun onlyTheOpenChatsPromptsAndThoseNamingNoChatAreShown() {
        val (p, q) = promptsShownWith("c1", chats, permissions, questions)
        assertEquals(listOf("mine", "loose"), p.map { it.promptID })
        assertEquals(listOf("q-mine"), q.map { it.promptID })
    }

    @Test
    fun theListShowsOnlyPromptsThatNameNoChat() {
        val (p, q) = promptsShownWith(null, chats, permissions, questions)
        assertEquals(listOf("loose"), p.map { it.promptID })
        assertEquals(emptyList<String>(), q.map { it.promptID })
    }

    @Test
    fun aPromptForAChatTheListDoesntKnowIsNeverLeftUnseen() {
        val (p, _) = promptsShownWith(null, listOf("c1"), permissions, questions)
        assertEquals(listOf("other", "loose"), p.map { it.promptID })
    }
}
