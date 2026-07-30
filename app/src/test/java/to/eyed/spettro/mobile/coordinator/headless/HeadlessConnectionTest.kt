package to.eyed.spettro.mobile.coordinator.headless

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.headless.HeadlessAskUser
import to.eyed.spettro.mobile.core.headless.HeadlessAskUserOption
import to.eyed.spettro.mobile.core.headless.HeadlessAskUserQuestion
import to.eyed.spettro.mobile.core.headless.HeadlessClient
import to.eyed.spettro.mobile.core.headless.HeadlessEvent
import to.eyed.spettro.mobile.core.headless.HeadlessReplyResult
import to.eyed.spettro.mobile.model.ChatMessage
import to.eyed.spettro.mobile.model.TranscriptItem

class HeadlessConnectionTest {

    private val token = "0123456789abcdef0123456789abcdef"
    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        scope.cancel()
        server?.close()
    }

    /** A connection whose client never dials anything. */
    private fun offlineConnection(): HeadlessConnection =
        HeadlessConnection(scope, HeadlessClient("127.0.0.1:1", token), autoStart = false)

    /** A connection backed by a MockWebServer for the REST calls. */
    private fun servedConnection(): Pair<HeadlessConnection, MockWebServer> {
        val s = MockWebServer().also { it.start() }
        server = s
        val client = HeadlessClient(s.url("/").toString().trimEnd('/'), token)
        return HeadlessConnection(scope, client, autoStart = false) to s
    }

    private fun json(code: Int, body: String): MockResponse =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private var seq = 0L
    private fun nextSeq(): Long = ++seq

    private fun state(thinking: Boolean, mode: String? = "coding", tokens: Long? = null) =
        HeadlessEvent.State(
            nextSeq(), "2026-07-30T10:00:00Z", mode,
            thinking = thinking,
            sessionId = "headless-1",
            activeAgent = "main",
            messagesCount = 3,
            tokensUsed = tokens,
        )

    private fun tool(
        status: String,
        name: String = "bash",
        agent: String? = null,
        argsJson: JsonObject? = null,
        argsRaw: String? = null,
        output: String? = null,
    ) = HeadlessEvent.Tool(
        nextSeq(), "", null,
        name = name, status = status, agent = agent,
        argsJson = argsJson, argsRaw = argsRaw, output = output,
    )

    private fun messages(conn: HeadlessConnection): List<ChatMessage> =
        conn.session.items.value.filterIsInstance<TranscriptItem.Message>().map { it.message }

    private fun tools(conn: HeadlessConnection) =
        conn.session.items.value.filterIsInstance<TranscriptItem.Tool>().map { it.tool }

    // ---- messages and notices -----------------------------------------------

    @Test
    fun messageEvents_mapToTranscriptRoles() {
        val conn = offlineConnection()
        conn.applyEvent(HeadlessEvent.UserMessage(nextSeq(), "", null, content = "do the thing"))
        conn.applyEvent(HeadlessEvent.SystemMessage(nextSeq(), "", null, content = "session resumed"))
        conn.applyEvent(HeadlessEvent.Comment(nextSeq(), "", null, message = "/mode plan"))
        conn.applyEvent(HeadlessEvent.Banner(nextSeq(), "", null, text = "saved", level = "success"))
        conn.applyEvent(HeadlessEvent.Banner(nextSeq(), "", null, text = "careful", level = "warn"))
        conn.applyEvent(HeadlessEvent.Banner(nextSeq(), "", null, text = "boom", level = "error"))
        conn.applyEvent(HeadlessEvent.AssistantError(nextSeq(), "", null, error = "run failed"))

        val msgs = messages(conn)
        assertEquals(7, msgs.size)
        assertEquals(ChatMessage.Role.User, msgs[0].role)
        assertEquals("do the thing", msgs[0].text)
        assertEquals(ChatMessage.Role.Notice(false), msgs[1].role)
        assertEquals(ChatMessage.Role.Notice(false), msgs[2].role)
        assertEquals(ChatMessage.Role.Notice(false), msgs[3].role)
        assertEquals("⚠ careful", msgs[4].text)
        assertEquals(ChatMessage.Role.Notice(false), msgs[4].role)
        assertEquals(ChatMessage.Role.Notice(true), msgs[5].role)
        assertEquals(ChatMessage.Role.Notice(true), msgs[6].role)
        assertEquals("run failed", msgs[6].text)
    }

    @Test
    fun assistantMessage_withThinking_appendsReasoningThenAnswer_bothFinished() {
        val conn = offlineConnection()
        conn.applyEvent(
            HeadlessEvent.AssistantMessage(
                nextSeq(), "", "coding",
                content = "Done.", thinking = "Let me check.", tokensUsed = 42,
            )
        )

        val msgs = messages(conn)
        assertEquals(2, msgs.size)
        assertEquals(ChatMessage.Role.Reasoning, msgs[0].role)
        assertEquals("Let me check.", msgs[0].text)
        assertFalse(msgs[0].isStreaming)
        assertEquals(ChatMessage.Role.Assistant, msgs[1].role)
        assertEquals("Done.", msgs[1].text)
        assertFalse(msgs[1].isStreaming)
        assertEquals(42L, conn.tokensUsed.value)
        assertEquals("coding", conn.mode.value)
    }

    @Test
    fun planCommitSearch_renderAsAssistantMessages() {
        val conn = offlineConnection()
        conn.applyEvent(HeadlessEvent.Plan(nextSeq(), "", "plan", plan = "1. do X\n2. do Y"))
        conn.applyEvent(HeadlessEvent.Commit(nextSeq(), "", null, message = "committed abc123"))
        conn.applyEvent(HeadlessEvent.Search(nextSeq(), "", null, result = "found 3 matches"))

        val msgs = messages(conn)
        assertEquals(3, msgs.size)
        assertTrue(msgs.all { it.role == ChatMessage.Role.Assistant && !it.isStreaming })
        assertEquals("1. do X\n2. do Y", msgs[0].text)
    }

    // ---- tool merge lifecycle -----------------------------------------------

    @Test
    fun toolLifecycle_runningThenSuccess_mergesIntoOneRow() {
        val conn = offlineConnection()
        val args = buildJsonObject { put("command", "ls -la") }
        conn.applyEvent(tool("running", name = "bash", argsJson = args))

        var rows = tools(conn)
        assertEquals(1, rows.size)
        assertEquals(AcpToolStatus.IN_PROGRESS, rows[0].status)

        conn.applyEvent(tool("success", name = "bash", output = "total 8"))
        rows = tools(conn)
        assertEquals(1, rows.size)
        assertEquals(AcpToolStatus.COMPLETED, rows[0].status)
        assertEquals("total 8", rows[0].output)

        // The key is released: a new run of the same tool is a new row.
        conn.applyEvent(tool("running", name = "bash"))
        assertEquals(2, tools(conn).size)
    }

    @Test
    fun toolLifecycle_errorClosesRow_andAgentScopesTheMergeKey() {
        val conn = offlineConnection()
        conn.applyEvent(tool("running", name = "read_file", agent = "explore"))
        conn.applyEvent(tool("running", name = "read_file")) // main agent, separate row
        assertEquals(2, tools(conn).size)

        conn.applyEvent(tool("error", name = "read_file", agent = "explore", output = "no such file"))
        val rows = tools(conn)
        assertEquals(2, rows.size)
        val failed = rows.first { it.status == AcpToolStatus.FAILED }
        assertEquals("no such file", failed.output)
        assertTrue(failed.title.startsWith("[explore] "))
        val stillRunning = rows.first { it.status == AcpToolStatus.IN_PROGRESS }
        assertEquals("read_file", stillRunning.title)
    }

    @Test
    fun toolArgs_jsonObjectBecomesSingleLineJson_rawStringPassesThrough() {
        val conn = offlineConnection()
        conn.applyEvent(
            tool("running", name = "bash", argsJson = buildJsonObject { put("command", "echo hi") })
        )
        conn.applyEvent(tool("running", name = "mystery", argsRaw = "free-form args, not json"))

        val rows = tools(conn)
        assertEquals("""{"command":"echo hi"}""", rows[0].argsJSON)
        assertEquals("free-form args, not json", rows[1].argsJSON)
    }

    @Test
    fun toolSuccessWithoutPriorRunning_createsCompletedRow() {
        val conn = offlineConnection()
        conn.applyEvent(tool("success", name = "grep", output = "2 hits"))
        val rows = tools(conn)
        assertEquals(1, rows.size)
        assertEquals(AcpToolStatus.COMPLETED, rows[0].status)
        assertEquals("2 hits", rows[0].output)
    }

    // ---- state / run transitions --------------------------------------------

    @Test
    fun stateEvents_driveBusyAndCounters() {
        val conn = offlineConnection()
        assertFalse(conn.isBusy.value)

        conn.applyEvent(state(thinking = true, mode = "plan", tokens = 100))
        assertTrue(conn.isBusy.value)
        assertNotNull(conn.session.runStartedAt.value)
        assertEquals("plan", conn.mode.value)
        assertEquals("main", conn.activeAgent.value)
        assertEquals("headless-1", conn.sessionId.value)
        assertEquals(3, conn.messagesCount.value)
        assertEquals(100L, conn.tokensUsed.value)

        // Repeated thinking=true does not restart the run.
        val startedAt = conn.session.runStartedAt.value
        conn.applyEvent(state(thinking = true))
        assertEquals(startedAt, conn.session.runStartedAt.value)

        conn.applyEvent(state(thinking = false, tokens = 250))
        assertFalse(conn.isBusy.value)
        assertNull(conn.session.runStartedAt.value)
        assertEquals(250L, conn.tokensUsed.value)
    }

    @Test
    fun remoteInterrupt_endsRunAndAppendsNotice() {
        val conn = offlineConnection()
        conn.applyEvent(state(thinking = true))
        assertTrue(conn.isBusy.value)

        conn.applyEvent(HeadlessEvent.RemoteInterrupt(nextSeq(), "", null, thinking = false))
        assertFalse(conn.isBusy.value)
        val last = messages(conn).last()
        assertEquals("Interrupted", last.text)
        assertEquals(ChatMessage.Role.Notice(false), last.role)
    }

    // ---- approval -----------------------------------------------------------

    @Test
    fun approvalRequest_setsPending_andNextRequestReplacesIt() {
        val conn = offlineConnection()
        conn.applyEvent(
            HeadlessEvent.ApprovalRequest(
                nextSeq(), "", null,
                toolId = "tool-1", command = "rm -rf build", reason = "destructive",
                segments = listOf("rm -rf build"),
            )
        )
        assertEquals("tool-1", conn.pendingApproval.value?.toolId)
        assertEquals("rm -rf build", conn.pendingApproval.value?.command)
        assertEquals("destructive", conn.pendingApproval.value?.reason)

        conn.applyEvent(
            HeadlessEvent.ApprovalRequest(nextSeq(), "", null, toolId = "tool-2", command = "make")
        )
        assertEquals("tool-2", conn.pendingApproval.value?.toolId)
    }

    @Test
    fun approve_postsDecisionAndClearsPending() = runBlocking {
        val (conn, server) = servedConnection()
        server.enqueue(json(200, """{"ok":true}"""))
        conn.applyEvent(
            HeadlessEvent.ApprovalRequest(nextSeq(), "", null, toolId = "tool-9", command = "ls")
        )

        val result = conn.approve("allow-once")
        assertEquals(HeadlessReplyResult.Ok, result)
        assertNull(conn.pendingApproval.value)

        val recorded = server.takeRequest()
        assertEquals("/approval", recorded.url.encodedPath)
        val body = recorded.body!!.utf8()
        assertTrue(body.contains("\"tool_id\":\"tool-9\""))
        assertTrue(body.contains("\"decision\":\"allow-once\""))
    }

    @Test
    fun approve_notPending_appendsTuiModeNotice() = runBlocking {
        val (conn, server) = servedConnection()
        server.enqueue(json(404, """{"error":"no pending approval"}"""))
        conn.applyEvent(
            HeadlessEvent.ApprovalRequest(nextSeq(), "", null, toolId = "tool-9", command = "ls")
        )

        val result = conn.approve("deny", instead = "use git clean")
        assertEquals(HeadlessReplyResult.NotPending, result)
        assertNull(conn.pendingApproval.value)
        assertEquals("Answer on the computer (TUI mode)", messages(conn).last().text)

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"instead\":\"use git clean\""))
    }

    @Test
    fun approve_withNothingPending_returnsNullWithoutNetwork() = runBlocking {
        val conn = offlineConnection()
        assertNull(conn.approve("allow-once"))
    }

    // ---- ask-user -----------------------------------------------------------

    private fun askUserEvent(questionId: String, question: String = "Which database?") =
        HeadlessEvent.AskUser(
            nextSeq(), "", null,
            form = HeadlessAskUser(
                version = 2,
                questionId = questionId,
                questions = listOf(
                    HeadlessAskUserQuestion(
                        header = "Database",
                        question = question,
                        options = listOf(
                            HeadlessAskUserOption("Postgres", "already provisioned"),
                            HeadlessAskUserOption("SQLite", isRecommended = true),
                        ),
                        allowFreeResponse = true,
                    )
                ),
            ),
        )

    @Test
    fun askUser_replacedByQuestionId_latestWins() {
        val conn = offlineConnection()
        conn.applyEvent(askUserEvent("q-3", question = "Which database?"))
        conn.applyEvent(askUserEvent("q-3", question = "Which database? (updated)"))
        assertEquals("q-3", conn.pendingQuestion.value?.questionId)
        assertEquals(
            "Which database? (updated)",
            conn.pendingQuestion.value?.questions?.first()?.question,
        )

        conn.applyEvent(askUserEvent("q-4"))
        assertEquals("q-4", conn.pendingQuestion.value?.questionId)
    }

    @Test
    fun answerQuestion_postsV2HeaderMapAndClearsPending() = runBlocking {
        val (conn, server) = servedConnection()
        server.enqueue(json(200, """{"ok":true}"""))
        conn.applyEvent(askUserEvent("q-3"))

        val result = conn.answerQuestion(mapOf("Database" to "SQLite"))
        assertEquals(HeadlessReplyResult.Ok, result)
        assertNull(conn.pendingQuestion.value)

        val recorded = server.takeRequest()
        assertEquals("/ask-user", recorded.url.encodedPath)
        val body = recorded.body!!.utf8()
        assertTrue(body.contains("\"question_id\":\"q-3\""))
        assertTrue(body.contains("\"Database\":\"SQLite\""))
    }

    @Test
    fun answerQuestionFromForm_joinsSelectionsAndFreeText() = runBlocking {
        val (conn, server) = servedConnection()
        server.enqueue(json(200, """{"ok":true}"""))
        val form = HeadlessAskUser(
            version = 2,
            questionId = "q-7",
            questions = listOf(
                HeadlessAskUserQuestion(
                    header = "Checks",
                    question = "Which checks?",
                    options = listOf(
                        HeadlessAskUserOption("go vet"),
                        HeadlessAskUserOption("gofmt"),
                    ),
                    multiSelect = true,
                ),
                HeadlessAskUserQuestion(header = "Name", question = "Project name?", allowFreeResponse = true),
                HeadlessAskUserQuestion(header = "Skipped", question = "Left unanswered"),
            ),
        )
        conn.applyEvent(HeadlessEvent.AskUser(nextSeq(), "", null, form = form))

        conn.answerQuestionFromForm(
            request = form,
            selections = mapOf("Checks" to listOf("go vet", "gofmt")),
            freeText = mapOf("Name" to "spettro"),
        )
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"Checks\":\"go vet, gofmt\""))
        assertTrue(body.contains("\"Name\":\"spettro\""))
        assertFalse(body.contains("Skipped"))
    }

    // ---- ACP adapters --------------------------------------------------------

    private fun sampleForm() = HeadlessAskUser(
        version = 2,
        questionId = "q-3",
        context = "both are already provisioned",
        questions = listOf(
            HeadlessAskUserQuestion(
                header = "Database",
                question = "Which database?",
                options = listOf(
                    HeadlessAskUserOption("Postgres", description = "already provisioned"),
                    HeadlessAskUserOption("SQLite", isRecommended = true),
                ),
                multiSelect = false,
                allowFreeResponse = true,
            ),
            HeadlessAskUserQuestion(
                header = "Checks",
                question = "Which checks run before commits?",
                options = listOf(
                    HeadlessAskUserOption("go vet"),
                    HeadlessAskUserOption("gofmt"),
                    HeadlessAskUserOption("staticcheck"),
                ),
                multiSelect = true,
            ),
        ),
    )

    @Test
    fun askUserToAcpQuestion_mapsFormForTheSharedSheet() {
        val request = askUserToAcpQuestion(sampleForm())

        assertEquals("both are already provisioned", request.context)
        assertEquals(2, request.questions.size)
        val db = request.questions[0]
        assertEquals("q-0", db.id)
        assertEquals("Database", db.header)
        assertEquals("Which database?", db.question)
        assertTrue(db.allowCustomInput)
        assertFalse(db.multiSelect)
        assertEquals(listOf("opt-0", "opt-1"), db.options.map { it.id })
        assertEquals("already provisioned", db.options[0].description)
        assertTrue(db.options[1].isRecommended)
        val checks = request.questions[1]
        assertTrue(checks.multiSelect)
        assertFalse(checks.allowCustomInput)
        val transport = request.transport as AcpQuestionRequest.Transport.ExtensionCall
        assertEquals(2, transport.version)
    }

    @Test
    fun acpAnswers_roundTripBackToHeaderLabelMap() {
        val form = sampleForm()
        // Labels → ids (via the adapter) → labels again.
        val request = askUserToAcpQuestion(form)
        val sqliteId = request.questions[0].options.first { it.label == "SQLite" }.id
        val answers = listOf(
            AcpQuestionAnswer(questionId = request.questions[0].id, optionIds = listOf(sqliteId)),
            AcpQuestionAnswer(
                questionId = request.questions[1].id,
                optionIds = listOf("opt-0", "opt-2"),
            ),
        )
        val map = acpAnswersToHeaderMap(form, answers)
        assertEquals("SQLite", map["Database"])
        assertEquals("go vet, staticcheck", map["Checks"]) // multi-select comma join
    }

    @Test
    fun acpAnswers_freeTextPassesThrough_unansweredOmitted() {
        val form = sampleForm()
        val map = acpAnswersToHeaderMap(
            form,
            listOf(AcpQuestionAnswer(questionId = "q-0", text = "DuckDB actually")),
        )
        assertEquals(mapOf("Database" to "DuckDB actually"), map)
    }

    @Test
    fun approvalToAcpPermission_synthesizesTheThreeDecisions() {
        val request = approvalToAcpPermission(
            HeadlessApproval(toolId = "tool-1", command = "rm -rf build", reason = "destructive")
        )
        assertEquals("rm -rf build", request.title)
        assertEquals("execute", request.toolKind)
        assertEquals(
            listOf("allow-once", "allow-always", "deny"),
            request.options.map { it.optionId },
        )
        assertEquals(
            listOf("allow_once", "allow_always", "reject_once"),
            request.options.map { it.kind },
        )
        assertEquals(
            JsonPrimitive(true),
            request.meta?.get(HEADLESS_META_ALLOWS_INSTEAD),
        )
        assertFalse(request.isQuestion)
    }

    // ---- send ---------------------------------------------------------------

    @Test
    fun send_doesNotAppendLocally_refusalBecomesErrorNotice() = runBlocking {
        val (conn, server) = servedConnection()
        server.enqueue(json(200, """{"accepted":true,"queued":false}"""))
        val ok = conn.send("hello")
        assertTrue(ok.accepted)
        // No optimistic bubble: the SSE user_message echo is the only source.
        assertTrue(conn.session.items.value.isEmpty())

        server.enqueue(json(409, """{"accepted":false,"error":"agent is busy"}"""))
        val refused = conn.send("/model")
        assertFalse(refused.accepted)
        val last = messages(conn).last()
        assertEquals(ChatMessage.Role.Notice(true), last.role)
        assertEquals("Not accepted: agent is busy", last.text)
    }
}
