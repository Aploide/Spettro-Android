package to.eyed.spettro.mobile.core.headless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Collections

class HeadlessClientTest {

    private val token = "0123456789abcdef0123456789abcdef"
    private lateinit var server: MockWebServer
    private lateinit var client: HeadlessClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HeadlessClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            token = token,
            initialBackoffMs = 100,
            maxBackoffMs = 400,
        )
    }

    @After
    fun tearDown() {
        client.stop()
        server.close()
    }

    private fun json(code: Int, body: String): MockResponse =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    // ---- auth ---------------------------------------------------------------

    @Test
    fun bearerTokenHeader_isSentOnEveryRequest() = runBlocking {
        server.enqueue(
            json(
                200,
                """{"thinking":false,"mode":"plan","messages_count":2,"tokens_used":10,
                    "started_at":"2026-07-30T09:00:00Z","session_id":"headless-x"}"""
            )
        )
        val status = client.status()
        assertEquals("plan", status.mode)
        assertFalse(status.thinking)
        assertEquals(2, status.messagesCount)
        assertEquals(10L, status.tokensUsed)
        assertEquals("headless-x", status.sessionId)
        assertEquals("2026-07-30T09:00:00Z", status.startedAt)

        val recorded = server.takeRequest()
        assertEquals("Bearer $token", recorded.headers["Authorization"])
    }

    @Test
    fun http401_setsAuthFailedAndThrows() = runBlocking {
        server.enqueue(json(401, "unauthorized"))
        try {
            client.status()
            fail("expected HeadlessAuthException")
        } catch (_: HeadlessAuthException) {
            // expected
        }
        assertEquals(HeadlessConnState.AuthFailed, client.connection.value)
    }

    @Test
    fun sse401_setsAuthFailed_withoutRetrying() = runBlocking {
        server.enqueue(json(401, "unauthorized"))
        client.start()
        withTimeout(5_000) {
            while (client.connection.value != HeadlessConnState.AuthFailed) delay(20)
        }
        // Only the one request — no retry loop against a bad token.
        assertEquals(1, server.requestCount)
    }

    // ---- /messages ----------------------------------------------------------

    @Test
    fun sendMessage_acceptedAndAuthHeader() = runBlocking {
        server.enqueue(json(200, """{"accepted":true,"queued":false,"note":"running"}"""))
        val res = client.sendMessage("hello")
        assertTrue(res.accepted)
        assertFalse(res.queued)
        assertEquals("running", res.note)
        assertNull(res.error)

        val recorded = server.takeRequest()
        assertEquals("Bearer $token", recorded.headers["Authorization"])
        assertEquals("POST", recorded.method)
        assertTrue(recorded.body!!.utf8().contains("\"message\":\"hello\""))
    }

    @Test
    fun sendMessage_409WithBody_isRefusalNotError() = runBlocking {
        server.enqueue(
            json(
                409,
                """{"accepted":false,"queued":false,
                    "error":"commands cannot be queued while an agent is running"}"""
            )
        )
        val res = client.sendMessage("/mode")
        assertFalse(res.accepted)
        assertEquals("commands cannot be queued while an agent is running", res.error)
    }

    @Test
    fun sendMessage_unparseableFailure_throws() = runBlocking {
        server.enqueue(json(503, "remote server stopped"))
        try {
            client.sendMessage("hi")
            fail("expected HeadlessHttpException")
        } catch (e: HeadlessHttpException) {
            assertEquals(503, e.code)
        }
    }

    // ---- approval / ask-user replies ---------------------------------------

    @Test
    fun approve_mapsStatusCodes() = runBlocking {
        server.enqueue(json(200, """{"ok":true}"""))
        assertEquals(HeadlessReplyResult.Ok, client.approve("tool-1", "allow-once"))

        // 404 = nothing pending (TUI mode / expired) -> "answer on the Mac".
        server.enqueue(json(404, "no pending approval for tool_id"))
        assertEquals(
            HeadlessReplyResult.NotPending,
            client.approve("tool-2", "deny", instead = "use mv")
        )

        server.enqueue(json(409, "approval already answered"))
        assertEquals(HeadlessReplyResult.AlreadyAnswered, client.approve("tool-3", "allow-always"))

        server.takeRequest() // tool-1
        val denyReq = server.takeRequest()
        val body = denyReq.body!!.utf8()
        assertTrue(body.contains("\"tool_id\":\"tool-2\""))
        assertTrue(body.contains("\"decision\":\"deny\""))
        assertTrue(body.contains("\"instead\":\"use mv\""))
    }

    @Test
    fun answerAskUser_v2AndV1Bodies() = runBlocking {
        server.enqueue(json(200, """{"ok":true}"""))
        assertEquals(
            HeadlessReplyResult.Ok,
            client.answerAskUser("q-1", mapOf("Color" to "Blue", "Sizes" to "S, M"))
        )
        val v2 = server.takeRequest().body!!.utf8()
        assertTrue(v2.contains("\"question_id\":\"q-1\""))
        assertTrue(v2.contains("\"answers\""))
        assertTrue(v2.contains("\"Color\":\"Blue\""))
        assertTrue(v2.contains("\"Sizes\":\"S, M\""))

        server.enqueue(json(404, "no pending ask-user for question_id"))
        assertEquals(HeadlessReplyResult.NotPending, client.answerAskUser("q-2", "Yes"))
        val v1 = server.takeRequest().body!!.utf8()
        assertTrue(v1.contains("\"answer\":\"Yes\""))
        assertFalse(v1.contains("\"answers\""))
    }

    // ---- probe --------------------------------------------------------------

    @Test
    fun probe_parsesServiceInfo() = runBlocking {
        server.enqueue(
            json(
                200,
                """{"service":"spettro-remote","endpoints":
                    ["POST /messages","GET /events (text/event-stream)","GET /status",
                     "POST /interrupt","POST /approval","POST /ask-user"]}"""
            )
        )
        val info = client.probe()
        assertEquals("spettro-remote", info.service)
        assertEquals(6, info.endpoints.size)
        assertTrue(info.supports("/ask-user"))
        assertFalse(info.supports("/nonexistent"))
    }

    // ---- SSE stream ---------------------------------------------------------

    private fun sseFrame(seq: Long, kind: String, dataJson: String): String {
        // The server emits compact JSON — one data line per event.
        val compact = dataJson.replace(Regex("""\s*\n\s*"""), "")
        return "event: $kind\nid: $seq\ndata: {\"seq\":$seq,\"kind\":\"$kind\",\"at\":\"t\",\"data\":$compact}\n\n"
    }

    @Test
    fun sseStream_parsesEvents_skipsHeartbeats_andDedupesReplay() = runBlocking {
        // First connection: events 1 and 2, with a heartbeat comment between.
        val stream1 = StringBuilder()
            .append(sseFrame(1, "user_message", """{"content":"hi","mode":"plan"}"""))
            .append(": ping\n\n")
            .append(
                sseFrame(
                    2, "tool",
                    """{"name":"shell","status":"running","agent":"main",
                        "args":"{\"cmd\":\"ls\"}","mode":"coding"}"""
                )
            )
            .toString()
        // Second connection (after the stream ends): server replays 1 and 2,
        // then delivers new event 3. The replay must be deduped by seq.
        val stream2 = StringBuilder()
            .append(sseFrame(1, "user_message", """{"content":"hi","mode":"plan"}"""))
            .append(
                sseFrame(
                    2, "tool",
                    """{"name":"shell","status":"running","agent":"main",
                        "args":"{\"cmd\":\"ls\"}","mode":"coding"}"""
                )
            )
            .append(
                sseFrame(
                    3, "assistant_message",
                    """{"content":"done","tokens_used":42,"mode":"coding"}"""
                )
            )
            .toString()

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(stream1)
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(stream2)
                .build()
        )

        val received = Collections.synchronizedList(mutableListOf<HeadlessEvent>())
        val collector = launch(Dispatchers.Default) {
            client.events.collect { received.add(it) }
        }
        delay(100) // let the collector subscribe before events flow
        client.start()

        withTimeout(10_000) {
            while (received.size < 3) delay(25)
        }
        // Give any (buggy) duplicate a beat to arrive before asserting.
        delay(200)

        assertEquals(3, received.size)
        assertEquals(listOf(1L, 2L, 3L), received.map { it.seq })

        val user = received[0] as HeadlessEvent.UserMessage
        assertEquals("hi", user.content)
        assertEquals("plan", user.mode)

        val tool = received[1] as HeadlessEvent.Tool
        assertEquals("shell", tool.name)
        assertEquals("""{"cmd":"ls"}""", tool.argsRaw)
        assertNull(tool.argsJson)

        val done = received[2] as HeadlessEvent.AssistantMessage
        assertEquals("done", done.content)
        assertEquals(42L, done.tokensUsed)

        // Both SSE requests carried the bearer header.
        val first = server.takeRequest()
        assertEquals("Bearer $token", first.headers["Authorization"])

        client.stop()
        assertEquals(HeadlessConnState.Stopped, client.connection.value)
        collector.cancel()
    }
}
