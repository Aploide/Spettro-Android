package to.eyed.spettro.mobile.coordinator.headless

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import to.eyed.spettro.mobile.core.acp.AcpToolCallEvent
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.headless.HeadlessAskUser
import to.eyed.spettro.mobile.core.headless.HeadlessClient
import to.eyed.spettro.mobile.core.headless.HeadlessConnState
import to.eyed.spettro.mobile.core.headless.HeadlessEvent
import to.eyed.spettro.mobile.core.headless.HeadlessReplyResult
import to.eyed.spettro.mobile.core.headless.SubmitResponse
import to.eyed.spettro.mobile.model.ChatSession

/**
 * A pending shell-approval prompt (`approval_request` event), answered via
 * [HeadlessConnection.approve].
 */
data class HeadlessApproval(
    val toolId: String,
    val command: String,
    val reason: String? = null,
    val segments: List<String> = emptyList(),
)

/**
 * Coordinator for one Protocol A (CLI HTTP+SSE) conversation: folds the flat
 * [HeadlessEvent] stream into a [ChatSession] transcript and surfaces pending
 * approval / ask-user prompts as state. Pure state holder — no UI; the
 * integrator binds [session] to the same transcript components Protocol B
 * uses, and the pending flows to the shared Permission/Question sheets via
 * the adapters in HeadlessAcpAdapters.kt.
 *
 * All event mutations run on [scope]'s dispatcher; hand it a main-thread
 * scope, matching [ChatSession]'s main-confined convention.
 */
class HeadlessConnection(
    private val scope: CoroutineScope,
    private val client: HeadlessClient,
    autoStart: Boolean = true,
) {
    /** The single conversation a headless CLI run exposes. */
    val session: ChatSession = ChatSession(chatId = "headless", projectPath = "")

    /** SSE connection lifecycle, straight from the client. */
    val connState: StateFlow<HeadlessConnState> get() = client.connection

    private val _pendingApproval = MutableStateFlow<HeadlessApproval?>(null)
    val pendingApproval: StateFlow<HeadlessApproval?> = _pendingApproval.asStateFlow()

    private val _pendingQuestion = MutableStateFlow<HeadlessAskUser?>(null)
    val pendingQuestion: StateFlow<HeadlessAskUser?> = _pendingQuestion.asStateFlow()

    private val _mode = MutableStateFlow<String?>(null)
    val mode: StateFlow<String?> = _mode.asStateFlow()

    private val _activeAgent = MutableStateFlow<String?>(null)
    val activeAgent: StateFlow<String?> = _activeAgent.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _messagesCount = MutableStateFlow(0)
    val messagesCount: StateFlow<Int> = _messagesCount.asStateFlow()

    private val _tokensUsed = MutableStateFlow(0L)
    val tokensUsed: StateFlow<Long> = _tokensUsed.asStateFlow()

    /** Convenience view of the run flag [ChatSession] keeps. */
    val isBusy: StateFlow<Boolean> get() = session.isBusy

    /**
     * Open (status "running") tool rows, keyed by "agent/name". Protocol A
     * tool events carry no tool ids, so a success/error is matched to the
     * running row with the same name from the same agent; concurrent
     * same-named calls from one agent merge into one row — acceptable for a
     * trace display.
     */
    private val openTools = mutableMapOf<String, String>()
    private var toolCounter = 0

    private val collector: Job = scope.launch {
        client.events.collect { applyEvent(it) }
    }

    init {
        if (autoStart) client.start()
    }

    /** Cancels the SSE reader. The transcript state stays as-is. */
    fun stop() {
        client.stop()
    }

    // ---- Outbound -----------------------------------------------------------

    /**
     * Submits a prompt (or slash command). Unlike Protocol B there is NO
     * optimistic local append: the server broadcasts a `user_message` event
     * for every accepted submission — ours included — so appending locally
     * as well would double the bubble. The echo arrives promptly over SSE.
     *
     * A refusal (`accepted = false`, e.g. slash command while a run is
     * active) is surfaced as an error notice; nothing was queued server-side.
     */
    suspend fun send(text: String): SubmitResponse {
        val response = client.sendMessage(text)
        if (!response.accepted) {
            val why = response.note ?: response.error ?: "rejected"
            session.appendNotice("Not accepted: $why", isError = true)
        }
        return response
    }

    /** Requests an interrupt; the `remote_interrupt` event closes the run. */
    suspend fun interrupt() {
        client.interrupt()
    }

    /**
     * Answers the pending approval. [decision] is "allow-once",
     * "allow-always" or "deny"; [instead] rides along with a deny as
     * "do this instead". Clears [pendingApproval] immediately. Returns null
     * when nothing was pending locally.
     */
    suspend fun approve(decision: String, instead: String? = null): HeadlessReplyResult? {
        val approval = _pendingApproval.value ?: return null
        _pendingApproval.value = null
        val result = client.approve(approval.toolId, decision, instead)
        noticeForReply(result)
        return result
    }

    /**
     * Answers the pending ask-user form with a v2 header→answer map (a
     * multi-select answer is comma-joined labels; unanswered questions are
     * omitted). A v1 single-question form falls back to the flat reply shape.
     * Clears [pendingQuestion] immediately. Returns null when nothing was
     * pending locally.
     */
    suspend fun answerQuestion(answers: Map<String, String>): HeadlessReplyResult? {
        val form = _pendingQuestion.value ?: return null
        _pendingQuestion.value = null
        val flatOnly = form.version < 2 &&
            form.questions.size <= 1 &&
            form.questions.firstOrNull()?.header.isNullOrEmpty()
        val result = if (flatOnly) {
            client.answerAskUser(form.questionId, answers.values.firstOrNull().orEmpty())
        } else {
            client.answerAskUser(form.questionId, answers)
        }
        noticeForReply(result)
        return result
    }

    /**
     * Answers [request] from raw sheet state: per-header selected option
     * labels, free text, and optional notes. Selections and free text are
     * combined into the wire's answer string (comma-joined labels, free text
     * appended); headers with nothing chosen are omitted (they come back
     * "skipped" to the model).
     */
    suspend fun answerQuestionFromForm(
        request: HeadlessAskUser,
        selections: Map<String, List<String>>,
        freeText: Map<String, String> = emptyMap(),
        notes: Map<String, String> = emptyMap(),
    ): HeadlessReplyResult? {
        val answers = mutableMapOf<String, String>()
        for (question in request.questions) {
            val header = question.header
            val parts = mutableListOf<String>()
            selections[header]?.forEach { label -> if (label.isNotBlank()) parts.add(label) }
            freeText[header]?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            if (parts.isEmpty()) continue
            var answer = parts.joinToString(", ")
            notes[header]?.takeIf { it.isNotBlank() }?.let { answer += " — $it" }
            answers[header] = answer
        }
        return answerQuestion(answers)
    }

    private fun noticeForReply(result: HeadlessReplyResult) {
        when (result) {
            HeadlessReplyResult.Ok -> Unit
            // TUI mode: /approval and /ask-user never resolve over HTTP.
            HeadlessReplyResult.NotPending ->
                session.appendNotice("Answer on the computer (TUI mode)", isError = false)
            HeadlessReplyResult.AlreadyAnswered ->
                session.appendNotice("Already answered elsewhere", isError = false)
        }
    }

    // ---- Event mapping ------------------------------------------------------

    /** Folds one event into the session/state. Internal for direct testing. */
    internal fun applyEvent(event: HeadlessEvent) {
        event.mode?.let { _mode.value = it }
        when (event) {
            is HeadlessEvent.State -> applyState(event)
            is HeadlessEvent.UserMessage -> session.appendUserMessage(event.content)
            is HeadlessEvent.SystemMessage -> session.appendNotice(event.content, isError = false)
            is HeadlessEvent.Comment -> session.appendNotice(event.message, isError = false)
            is HeadlessEvent.Banner -> applyBanner(event)
            is HeadlessEvent.AssistantMessage -> {
                event.thinking?.let { session.appendReasoning(it) }
                if (event.content.isNotEmpty()) session.appendAssistant(event.content)
                session.endStreaming()
                event.tokensUsed?.let { _tokensUsed.value = it }
            }
            // A completed plan-mode turn: the plan text IS the answer.
            is HeadlessEvent.Plan -> {
                if (event.plan.isNotEmpty()) session.appendAssistant(event.plan)
                session.endStreaming()
                event.tokensUsed?.let { _tokensUsed.value = it }
            }
            is HeadlessEvent.Commit -> {
                if (event.message.isNotEmpty()) session.appendAssistant(event.message)
                session.endStreaming()
            }
            is HeadlessEvent.Search -> {
                if (event.result.isNotEmpty()) session.appendAssistant(event.result)
                session.endStreaming()
            }
            is HeadlessEvent.AssistantError -> session.appendNotice(event.error, isError = true)
            is HeadlessEvent.PlanError -> session.appendNotice(event.error, isError = true)
            is HeadlessEvent.CommitError -> session.appendNotice(event.error, isError = true)
            is HeadlessEvent.SearchError -> session.appendNotice(event.error, isError = true)
            is HeadlessEvent.Tool -> applyTool(event)
            is HeadlessEvent.ApprovalRequest -> _pendingApproval.value = HeadlessApproval(
                toolId = event.toolId,
                command = event.command,
                reason = event.reason,
                segments = event.segments,
            )
            // The TUI republishes the form (same question_id, new seq) as the
            // user walks it — latest replaces, which dedupes by construction.
            is HeadlessEvent.AskUser -> _pendingQuestion.value = event.form
            is HeadlessEvent.RemoteInterrupt -> {
                session.endRun()
                session.appendNotice("Interrupted", isError = false)
            }
            is HeadlessEvent.Unknown -> Unit
        }
    }

    private fun applyState(event: HeadlessEvent.State) {
        val wasBusy = session.isBusy.value
        if (event.thinking && !wasBusy) {
            session.beginRun()
        } else if (!event.thinking && wasBusy) {
            session.endRun(stopReason = event.reason)
        }
        event.sessionId?.let { _sessionId.value = it }
        event.activeAgent?.let { _activeAgent.value = it }
        event.messagesCount?.let { _messagesCount.value = it }
        event.tokensUsed?.let { _tokensUsed.value = it }
    }

    private fun applyBanner(event: HeadlessEvent.Banner) {
        val text = if (event.level == "warn") "⚠ ${event.text}" else event.text
        session.appendNotice(text, isError = event.level == "error")
    }

    /**
     * Merges a tool trace event into the transcript, reusing
     * [ChatSession.applyToolEvent] with a synthetic tool-call id: `running`
     * opens (or refreshes) a row, `success`/`error` closes it with output.
     */
    private fun applyTool(event: HeadlessEvent.Tool) {
        val key = "${event.agent.orEmpty()}/${event.name}"
        val running = event.status == HeadlessEvent.Tool.STATUS_RUNNING
        val existingId = openTools[key]
        val id = existingId ?: "hl-tool-${++toolCounter}"
        if (running) openTools[key] = id else openTools.remove(key)

        val status = when (event.status) {
            HeadlessEvent.Tool.STATUS_RUNNING -> AcpToolStatus.IN_PROGRESS
            HeadlessEvent.Tool.STATUS_SUCCESS -> AcpToolStatus.COMPLETED
            HeadlessEvent.Tool.STATUS_ERROR -> AcpToolStatus.FAILED
            else -> AcpToolStatus.UNKNOWN
        }
        val title = buildString {
            event.agent?.let { append("[").append(it).append("] ") }
            append(event.name)
        }
        // argsJson serializes to single-line JSON; a raw string rides through
        // encodedJsonString() verbatim (string-primitive case).
        val rawInput = event.argsJson ?: event.argsRaw?.let(::JsonPrimitive)
        session.applyToolEvent(
            AcpToolCallEvent(
                toolCallId = id,
                title = title,
                kind = toolKind(event.name),
                status = status,
                rawInput = rawInput,
                content = event.output?.let { listOf(AcpToolContent.Text(it)) } ?: emptyList(),
            ),
            isStart = existingId == null,
        )
    }

    companion object {
        /** ACP tool kind guessed from a Protocol A tool name, for row icons. */
        internal fun toolKind(name: String): String? = when (name.lowercase()) {
            "bash", "shell", "exec", "run_command", "terminal" -> "execute"
            "read", "read_file", "cat", "view", "open" -> "read"
            "edit", "write", "write_file", "apply_patch", "patch", "create_file" -> "edit"
            "grep", "search", "glob", "ls", "find", "rg", "list" -> "search"
            "fetch", "web_fetch", "web_search", "http", "curl" -> "fetch"
            "agent", "plan", "think", "task" -> "think"
            else -> null
        }
    }
}
