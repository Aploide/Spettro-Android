package to.eyed.spettro.mobile.core.headless

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * One event from the CLI remote control plane (`GET /events` SSE stream).
 *
 * Envelope on the wire: `{"seq":N,"kind":"...","at":"RFC3339Nano","data":{...}}`.
 * The TUI injects `"mode"` into every event's data; headless mode includes it on
 * most events — it is surfaced here as the envelope-level [mode].
 *
 * Unknown kinds parse to [Unknown] rather than failing: the protocol grows.
 */
sealed class HeadlessEvent {
    /** Monotonic sequence number (gaps possible — slow-subscriber drops). */
    abstract val seq: Long

    /** RFC3339Nano timestamp, kept as a string and formatted lazily. */
    abstract val at: String

    /** Agent mode ("plan" | "coding" | "ask" | custom), when the event carries one. */
    abstract val mode: String?

    /** `state` — thinking flag + session counters, published at run boundaries. */
    data class State(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val thinking: Boolean,
        val sessionId: String? = null,
        val activeAgent: String? = null,
        val messagesCount: Int? = null,
        val tokensUsed: Long? = null,
        val reason: String? = null,
    ) : HeadlessEvent()

    /** `user_message` — echo of a submitted prompt. */
    data class UserMessage(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val content: String,
        val mentionedFiles: List<String> = emptyList(),
    ) : HeadlessEvent()

    /** `system_message` — informational text pushed by the host. */
    data class SystemMessage(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val content: String,
    ) : HeadlessEvent()

    /** `assistant_message` — a completed assistant turn. */
    data class AssistantMessage(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val content: String,
        val thinking: String? = null,
        val meta: String? = null,
        val toolsCount: Int? = null,
        val tokensUsed: Long? = null,
    ) : HeadlessEvent()

    /** `assistant_error` — the run failed. */
    data class AssistantError(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val error: String,
    ) : HeadlessEvent()

    /** `plan` — a completed plan-mode turn. */
    data class Plan(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val plan: String,
        val toolsCount: Int? = null,
        val tokensUsed: Long? = null,
    ) : HeadlessEvent()

    /** `plan_error`. */
    data class PlanError(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val error: String,
    ) : HeadlessEvent()

    /** `comment` — free-form host commentary (slash-command replies, notices). */
    data class Comment(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val message: String,
    ) : HeadlessEvent()

    /**
     * `tool` — a tool call trace.
     *
     * `args` on the wire may be a JSON object (TUI, when the raw args string is
     * valid JSON) OR a plain string (headless always publishes the raw string);
     * the TUI additionally sends `args_raw` when the string was not JSON. Here
     * they are normalized: [argsJson] holds the structured form when the wire
     * carried one, [argsRaw] holds the string form when the wire carried one.
     */
    data class Tool(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val name: String,
        val status: String,
        val agent: String? = null,
        val argsJson: JsonObject? = null,
        val argsRaw: String? = null,
        val output: String? = null,
    ) : HeadlessEvent() {
        companion object {
            const val STATUS_RUNNING = "running"
            const val STATUS_SUCCESS = "success"
            const val STATUS_ERROR = "error"
        }
    }

    /** `banner` — transient host banner. level: info | warn | error | success. */
    data class Banner(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val text: String,
        val level: String,
    ) : HeadlessEvent()

    /**
     * `approval_request` — a shell command awaits approval via `POST /approval`.
     * Only answerable over HTTP in headless mode; in TUI mode the reply endpoint
     * returns 404 ("answer on the Mac").
     */
    data class ApprovalRequest(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val toolId: String,
        val command: String,
        val reason: String? = null,
        val segments: List<String> = emptyList(),
    ) : HeadlessEvent()

    /**
     * `ask_user` — the agent asked the user a question (form). Parsed from both
     * the v1 flat shape and the v2 `questions[]` shape into one model.
     *
     * The TUI republishes this event (same [HeadlessAskUser.questionId], new
     * seq) as the user walks the form — consumers should dedupe/replace by
     * `questionId`, keeping the latest.
     */
    data class AskUser(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val form: HeadlessAskUser,
    ) : HeadlessEvent()

    /** `commit`. */
    data class Commit(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val message: String,
    ) : HeadlessEvent()

    /** `commit_error`. */
    data class CommitError(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val error: String,
    ) : HeadlessEvent()

    /** `search`. */
    data class Search(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val result: String,
    ) : HeadlessEvent()

    /** `search_error`. */
    data class SearchError(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val error: String,
    ) : HeadlessEvent()

    /** `remote_interrupt` — an interrupt request was received by the host. */
    data class RemoteInterrupt(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val thinking: Boolean? = null,
    ) : HeadlessEvent()

    /**
     * Any kind without a dedicated case (remote_started, remote_stopped,
     * remote_command, remote_prompt, telegram_*, future kinds). Never dropped.
     */
    data class Unknown(
        override val seq: Long,
        override val at: String,
        override val mode: String?,
        val kind: String,
        val data: JsonObject,
    ) : HeadlessEvent()

    companion object {
        /**
         * Parses one event envelope. Returns null only when the payload is not
         * an event at all (no "kind"); unknown kinds map to [Unknown].
         */
        fun parse(envelope: JsonObject): HeadlessEvent? {
            val kind = envelope.str("kind") ?: return null
            val seq = envelope.long("seq") ?: 0L
            val at = envelope.str("at") ?: ""
            val data = envelope["data"] as? JsonObject ?: JsonObject(emptyMap())
            val mode = data.str("mode")

            return when (kind) {
                "state" -> State(
                    seq, at, mode,
                    thinking = data.bool("thinking") ?: false,
                    sessionId = data.str("session_id"),
                    activeAgent = data.str("active_agent"),
                    messagesCount = data.int("messages_count"),
                    tokensUsed = data.long("tokens_used"),
                    reason = data.str("reason"),
                )
                "user_message" -> UserMessage(
                    seq, at, mode,
                    content = data.str("content") ?: "",
                    mentionedFiles = data.strList("mentioned_files"),
                )
                "system_message" -> SystemMessage(seq, at, mode, content = data.str("content") ?: "")
                "assistant_message" -> AssistantMessage(
                    seq, at, mode,
                    content = data.str("content") ?: "",
                    thinking = data.str("thinking")?.takeIf { it.isNotEmpty() },
                    meta = data.str("meta")?.takeIf { it.isNotEmpty() },
                    toolsCount = data.int("tools_count"),
                    tokensUsed = data.long("tokens_used"),
                )
                "assistant_error" -> AssistantError(seq, at, mode, error = data.str("error") ?: "")
                "plan" -> Plan(
                    seq, at, mode,
                    plan = data.str("plan") ?: "",
                    toolsCount = data.int("tools_count"),
                    tokensUsed = data.long("tokens_used"),
                )
                "plan_error" -> PlanError(seq, at, mode, error = data.str("error") ?: "")
                "comment" -> Comment(seq, at, mode, message = data.str("message") ?: "")
                "tool" -> parseTool(seq, at, mode, data)
                "banner" -> Banner(
                    seq, at, mode,
                    text = data.str("text") ?: "",
                    level = data.str("level") ?: "info",
                )
                "approval_request" -> ApprovalRequest(
                    seq, at, mode,
                    toolId = data.str("tool_id") ?: "",
                    command = data.str("command") ?: "",
                    reason = data.str("reason")?.takeIf { it.isNotEmpty() },
                    segments = data.strList("segments"),
                )
                "ask_user" -> AskUser(seq, at, mode, form = HeadlessAskUser.parse(data))
                "commit" -> Commit(seq, at, mode, message = data.str("message") ?: "")
                "commit_error" -> CommitError(seq, at, mode, error = data.str("error") ?: "")
                "search" -> Search(seq, at, mode, result = data.str("result") ?: "")
                "search_error" -> SearchError(seq, at, mode, error = data.str("error") ?: "")
                "remote_interrupt" -> RemoteInterrupt(seq, at, mode, thinking = data.bool("thinking"))
                else -> Unknown(seq, at, mode, kind = kind, data = data)
            }
        }

        private fun parseTool(seq: Long, at: String, mode: String?, data: JsonObject): Tool {
            var argsJson: JsonObject? = null
            var argsRaw: String? = data.str("args_raw")
            when (val args = data["args"]) {
                null -> {}
                is JsonObject -> argsJson = args
                is JsonPrimitive -> if (argsRaw == null) argsRaw = args.contentOrNull
                else -> if (argsRaw == null) argsRaw = args.toString()
            }
            return Tool(
                seq, at, mode,
                name = data.str("name") ?: "",
                status = data.str("status") ?: "",
                agent = data.str("agent")?.takeIf { it.isNotEmpty() },
                argsJson = argsJson,
                argsRaw = argsRaw?.takeIf { it.isNotEmpty() },
                output = data.str("output")?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** One option of an ask-user question. */
data class HeadlessAskUserOption(
    val label: String,
    val description: String? = null,
    val isRecommended: Boolean = false,
)

/** One question of an ask-user form. */
data class HeadlessAskUserQuestion(
    /** Answers are keyed by this header when replying (`POST /ask-user` v2). */
    val header: String,
    val question: String,
    val options: List<HeadlessAskUserOption> = emptyList(),
    val multiSelect: Boolean = false,
    val allowFreeResponse: Boolean = false,
)

/**
 * The ask-user form carried by the `ask_user` event, normalized across payload
 * versions: v1 was one flat question per event; v2 adds `questions[]` for the
 * whole form while keeping the flat fields pointed at the active question.
 *
 * Reply via [HeadlessClient.answerAskUser]: answers are keyed by question
 * header (v2), a multi-select answer is comma-separated labels; there are no
 * option ids on this wire.
 */
data class HeadlessAskUser(
    val version: Int,
    val questionId: String,
    /** All questions of the form (synthesized from flat fields for v1). */
    val questions: List<HeadlessAskUserQuestion>,
    /** Index of the question the flat fields describe. */
    val active: Int = 0,
    val context: String? = null,
    /** v1 flat fields, kept verbatim for single-question clients. */
    val flatQuestion: String? = null,
    /** Display strings "label — description" as published on the wire. */
    val flatOptions: List<String> = emptyList(),
    val default: String? = null,
    val allowFreeResponse: Boolean = false,
) {
    val count: Int get() = questions.size

    companion object {
        /** Parses the `ask_user` event data (v1 or v2). */
        fun parse(data: JsonObject): HeadlessAskUser {
            val version = data.int("version") ?: 1
            val questionId = data.str("question_id") ?: ""
            val context = data.str("context")?.takeIf { it.isNotEmpty() }
            val flatQuestion = data.str("question")
            val flatOptions = data.strList("options")
            val default = data.str("default")?.takeIf { it.isNotEmpty() }
            val allowFree = data.bool("allow_free_response") ?: false

            val questionsArr = data["questions"] as? JsonArray
            val questions = if (questionsArr != null && questionsArr.isNotEmpty()) {
                questionsArr.mapNotNull { el ->
                    val q = el as? JsonObject ?: return@mapNotNull null
                    HeadlessAskUserQuestion(
                        header = q.str("header") ?: "",
                        question = q.str("question") ?: "",
                        options = (q["options"] as? JsonArray)?.mapNotNull { opt ->
                            val o = opt as? JsonObject ?: return@mapNotNull null
                            HeadlessAskUserOption(
                                label = o.str("label") ?: return@mapNotNull null,
                                description = o.str("description")?.takeIf { it.isNotEmpty() },
                                isRecommended = o.bool("is_recommended") ?: false,
                            )
                        } ?: emptyList(),
                        multiSelect = q.bool("multi_select") ?: false,
                        allowFreeResponse = q.bool("allow_free_response") ?: false,
                    )
                }
            } else {
                // v1: synthesize a single question from the flat fields. Flat
                // options are display strings "label — description".
                listOf(
                    HeadlessAskUserQuestion(
                        header = "",
                        question = flatQuestion ?: "",
                        options = flatOptions.map { display ->
                            val idx = display.indexOf(" — ")
                            if (idx >= 0) {
                                HeadlessAskUserOption(
                                    label = display.substring(0, idx),
                                    description = display.substring(idx + 3).takeIf { it.isNotEmpty() },
                                )
                            } else {
                                HeadlessAskUserOption(label = display)
                            }
                        },
                        multiSelect = false,
                        allowFreeResponse = allowFree,
                    )
                )
            }

            val active = (data.int("active") ?: 0).coerceIn(0, (questions.size - 1).coerceAtLeast(0))
            return HeadlessAskUser(
                version = version,
                questionId = questionId,
                questions = questions,
                active = active,
                context = context,
                flatQuestion = flatQuestion,
                flatOptions = flatOptions,
                default = default,
                allowFreeResponse = allowFree,
            )
        }
    }
}

// Small JsonObject accessors shared by the parsers above.

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { el ->
        when (el) {
            is JsonPrimitive -> el.contentOrNull
            else -> el.toString()
        }
    } ?: emptyList()
