package to.eyed.spettro.mobile.core.remote

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson

class RemoteNotificationTest {

    private fun parse(method: String, json: String): RemoteNotification? =
        RemoteNotification.parse(method, SpettroJson.parseToJsonElement(json))

    @Test
    fun `parses hello`() {
        val note = parse(
            RemoteMethod.HELLO,
            """
            {"protocolVersion":1,"hostID":"H1","hostName":"Carlo's MacBook Pro",
             "hostKind":"app","hostVersion":"2.6.6",
             "challenge":"AAAA","pairingOpen":true}
            """,
        )
        val hello = (note as RemoteNotification.Hello).hello
        assertEquals(1, hello.protocolVersion)
        assertEquals("H1", hello.hostID)
        assertEquals("Carlo's MacBook Pro", hello.hostName)
        assertEquals("app", hello.hostKind)
        assertEquals("AAAA", hello.challenge)
        assertTrue(hello.pairingOpen)
    }

    @Test
    fun `parses chat update with the ACP body verbatim`() {
        val note = parse(
            RemoteMethod.CHAT_UPDATE,
            """
            {"chatID":"c-1","update":{"sessionUpdate":"agent_message_chunk",
             "content":{"type":"text","text":"hi"},"unmodelledField":42}}
            """,
        )
        val update = (note as RemoteNotification.ChatUpdate)
        assertEquals("c-1", update.chatID)
        assertEquals("agent_message_chunk", update.update["sessionUpdate"]!!.jsonPrimitive.content)
        // Fields the client doesn't model still ride through.
        assertEquals("42", update.update["unmodelledField"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parses chat user message with attachments`() {
        val note = parse(
            RemoteMethod.CHAT_USER,
            """
            {"chatID":"c-1","text":"look at this","attachments":
             [{"base64":"aGk=","mimeType":"image/jpeg"}],
             "timestamp":"2026-07-30T12:00:00Z"}
            """,
        )
        val user = note as RemoteNotification.ChatUser
        assertEquals("c-1", user.chatID)
        assertEquals("look at this", user.text)
        assertEquals(1, user.attachments.size)
        assertEquals("image/jpeg", user.attachments[0].mimeType)
        assertEquals("2026-07-30T12:00:00Z", user.timestamp)
    }

    @Test
    fun `parses chat state with summary stop reason and notice`() {
        val note = parse(
            RemoteMethod.CHAT_STATE,
            """
            {"chat":{"id":"c-1","title":"Fix the tests","projectPath":"/Users/c/repo",
              "createdAt":"2026-07-29T10:00:00Z","updatedAt":"2026-07-30T09:30:00Z",
              "isPinned":true,"isArchived":false,"isBusy":false,
              "messageCount":12,"preview":"done."},
             "stopReason":"end_turn",
             "notice":{"text":"Usage limit reached","isError":true}}
            """,
        )
        val state = note as RemoteNotification.ChatState
        assertEquals("c-1", state.chat.id)
        assertEquals("Fix the tests", state.chat.title)
        assertEquals("/Users/c/repo", state.chat.projectPath)
        assertTrue(state.chat.isPinned)
        assertFalse(state.chat.isBusy)
        assertEquals(12, state.chat.messageCount)
        assertEquals("end_turn", state.stopReason)
        assertEquals("Usage limit reached", state.notice!!.text)
        assertTrue(state.notice!!.isError)
    }

    @Test
    fun `parses chat state without optional fields`() {
        val note = parse(
            RemoteMethod.CHAT_STATE,
            """{"chat":{"id":"c-2","title":"t","projectPath":"/p",
                "createdAt":"x","updatedAt":"x","isPinned":false,"isArchived":false,
                "isBusy":true,"messageCount":0,"preview":""}}""",
        )
        val state = note as RemoteNotification.ChatState
        assertNull(state.stopReason)
        assertNull(state.notice)
        assertTrue(state.chat.isBusy)
    }

    @Test
    fun `parses chat removed`() {
        val note = parse(RemoteMethod.CHAT_REMOVED, """{"chatID":"c-9"}""")
        assertEquals("c-9", (note as RemoteNotification.ChatRemoved).chatID)
    }

    @Test
    fun `parses host state`() {
        val note = parse(
            RemoteMethod.HOST_STATE,
            """{"agentReady":false,"shuttingDown":true,"message":"sharing was turned off"}""",
        )
        val host = note as RemoteNotification.HostState
        assertFalse(host.agentReady)
        assertTrue(host.shuttingDown)
        assertEquals("sharing was turned off", host.message)
    }

    @Test
    fun `parses permission ask with the request verbatim and optional chatID`() {
        val note = parse(
            RemoteMethod.PERMISSION_ASK,
            """
            {"promptID":"p-1","chatID":"c-1","request":
             {"sessionId":"s","toolCall":{"toolCallId":"t1"},
              "options":[{"optionId":"allow","name":"Allow","kind":"allow_once"}]}}
            """,
        )
        val ask = note as RemoteNotification.PermissionAsk
        assertEquals("p-1", ask.promptID)
        assertEquals("c-1", ask.chatID)
        assertEquals("s", ask.request["sessionId"]!!.jsonPrimitive.content)

        val noChat = parse(RemoteMethod.PERMISSION_ASK, """{"promptID":"p-2","request":{}}""")
        assertNull((noChat as RemoteNotification.PermissionAsk).chatID)
    }

    @Test
    fun `parses permission and question resolved`() {
        val p = parse(RemoteMethod.PERMISSION_RESOLVED, """{"promptID":"p-1","resolvedBy":"Mac"}""")
        assertEquals("Mac", (p as RemoteNotification.PermissionResolved).resolvedBy)

        val q = parse(RemoteMethod.QUESTION_RESOLVED, """{"promptID":"q-1"}""")
        val resolved = q as RemoteNotification.QuestionResolved
        assertEquals("q-1", resolved.promptID)
        assertNull(resolved.resolvedBy)
    }

    @Test
    fun `parses question ask`() {
        val note = parse(
            RemoteMethod.QUESTION_ASK,
            """{"promptID":"q-1","chatID":"c-1","request":{"questions":[{"questionId":"q"}]}}""",
        )
        val ask = note as RemoteNotification.QuestionAsk
        assertEquals("q-1", ask.promptID)
        assertTrue(ask.request.containsKey("questions"))
    }

    @Test
    fun `parses agent notification passthrough`() {
        val note = parse(
            RemoteMethod.AGENT_NOTIFICATION,
            """{"method":"_spettro/account/update","params":{"plan":"max"}}""",
        )
        val agent = note as RemoteNotification.AgentNotification
        assertEquals("_spettro/account/update", agent.method)
        assertEquals("max", agent.params!!.jsonObject["plan"]!!.jsonPrimitive.content)

        val noParams = parse(RemoteMethod.AGENT_NOTIFICATION, """{"method":"_spettro/ping"}""")
        assertNull((noParams as RemoteNotification.AgentNotification).params)
    }

    @Test
    fun `unknown methods and broken payloads return null`() {
        assertNull(parse("_spettro/remote/not/a/thing", "{}"))
        assertNull(parse(RemoteMethod.CHAT_UPDATE, """{"missing":"chatID"}"""))
        assertNull(RemoteNotification.parse(RemoteMethod.CHAT_UPDATE, null))
    }

    @Test
    fun `auth error classification`() {
        assertTrue(RemoteAuthError.isPermanent(RemoteAuthError.NOT_PAIRED))
        assertTrue(RemoteAuthError.isPermanent(RemoteAuthError.VERSION_MISMATCH))
        assertTrue(RemoteAuthError.isPermanent(RemoteAuthError.REVOKED))
        assertFalse(RemoteAuthError.isPermanent(RemoteAuthError.BAD_PROOF))
        assertFalse(RemoteAuthError.isPermanent(RemoteAuthError.PAIRING_EXPIRED))
        assertTrue(RemoteAuthError.isAuthCode(-33001))
        assertTrue(RemoteAuthError.isAuthCode(-33005))
        assertFalse(RemoteAuthError.isAuthCode(-32601))
    }
}
