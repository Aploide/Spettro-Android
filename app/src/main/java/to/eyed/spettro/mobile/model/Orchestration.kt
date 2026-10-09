package to.eyed.spettro.mobile.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.acp.asBool
import to.eyed.spettro.mobile.core.acp.asDouble
import to.eyed.spettro.mobile.core.acp.asInt
import to.eyed.spettro.mobile.core.acp.asString
import to.eyed.spettro.mobile.core.compactTokens

/**
 * Folding the flat ACP transcript back into the shape the run actually had.
 * Port of the desktop app's `views/chat/transcript/orchestration.ts`.
 *
 * The CLI streams a workflow as a *flat* sequence of tool calls: one
 * long-lived card for the run, one call per sub-agent, and then every tool
 * each sub-agent runs, all interleaved in arrival order. Rendered literally
 * that is a wall of rows where a twenty-agent fan-out drowns out the
 * conversation and nothing says which agent did what.
 *
 * The structure is recoverable, because every row carries who it belongs to:
 * members name their `run_id`, and nested calls are titled `[code#3] bash …`.
 * This file is the single place that reconstruction happens. It is pure and
 * memo-free on purpose: a session restored from disk must fold to exactly the
 * same tree as the live one that produced it, so the answer may depend only on
 * the items passed in.
 *
 * The run's own state — phases, members, pause, stop, size — comes from one
 * of two sources, best first:
 *
 *  1. `_meta["spettro.app/workflow"]` on the card (internal/acp/workflow.go
 *     metaView), which the CLI builds from the same state as the card's text.
 *     It is the whole run as structure, so when it is there it is the answer.
 *     [ToolCallItem.workflow] holds it, live and in a snapshot alike.
 *  2. The card's text (`acpWorkflow.render()`), read back line by line. An
 *     older CLI sends nothing else, and a host that does not relay the meta in
 *     its snapshot leaves nothing else. The format is stable and documented on
 *     [parseRenderedWorkflow]; structured start args, when the card kept
 *     them, win over it.
 *
 * The other thing the wire gets wrong for us is that a workflow arrives as TWO
 * tool calls with the same name. `workflow {"save_as":…,"script":"export const
 * meta = …"}` is the model's actual invocation of the `workflow` TOOL, and it
 * carries the entire script as arguments; `workflow <name>` (card id
 * `workflow-<run id>`) is the lifecycle card the run is built from. Left alone
 * the first renders as a full page of raw JSON directly above the card it
 * belongs to, so it is folded into the run — matched by the `run_id` its
 * `<workflow_result>` block declares, or, failing that, by position, since a
 * script call can only ever precede the run it starts. The exception is
 * load-bearing: a script call that FAILED before any run existed has no
 * lifecycle call to hide behind, and it is the only trace that a workflow was
 * attempted at all, so it survives as a row of its own.
 *
 * A run can also outlive its turn: paused at a checkpoint, it waits for the
 * orchestrating model, which may continue it in a later turn. The CLI then
 * closes the earlier card ("continued in a later turn", `continuedIn`) and
 * opens a new one (`workflow-<run id>-2`) with everything recorded so far. So
 * members are pooled per run, not per card, and each card picks the members
 * its own state lists.
 *
 * Ultra swarms are gone: the CLI dropped them, and an `ultra` call is an
 * ordinary tool row now.
 */

// ---------------------------------------------------------------------------
// The shapes the views render
// ---------------------------------------------------------------------------

/**
 * A member's (or a script call's) state. [PENDING] is a member the CLI
 * reported in a status it does not name (its "·" glyph): not yet running, and
 * certainly not done. [STOPPED] is one still running when its run ended (see
 * finishWorkflow).
 */
enum class OrchStatus { RUNNING, DONE, FAILED, PENDING, STOPPED }

/**
 * A run's state.
 *
 * [PAUSED] is alive but idle — waiting at a checkpoint for the orchestrating
 * model — and must never spin like [RUNNING]. [STOPPED] was ended on purpose
 * (the orchestrator, the idle reaper, the session closing): neither a success
 * nor a failure, and it says why.
 */
enum class RunStatus { RUNNING, PAUSED, STOPPED, DONE, FAILED }

@Immutable
data class OrchCounts(
    val total: Int = 0,
    val running: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    /** Listed by the run but not started yet. */
    val pending: Int = 0,
    /** Cut off by the run ending under them (a cancelled turn, a stop). */
    val stopped: Int = 0,
    val cached: Int = 0,
) {
    /** Done and failed both count as finished — a failed agent is not still
     *  working, and the CLI's own "d/n done" counts it the same way. */
    val finished: Int get() = done + failed
}

/** One sub-agent: a workflow member or a plain delegation. */
@Immutable
data class MemberCall(
    /** The member's own `agent` call. For a member the card lists but whose
     *  call never reached this transcript, a stand-in built from the card. */
    val tool: ToolCallItem,
    /** "review#3"; falls back to the raw agent name. */
    val instance: String,
    /** "review" — the part before '#', for the tint. */
    val specId: String,
    /** 1-based dispatch index when the CLI sent one. */
    val index: Int?,
    /** The label / prompt ("" if unknown). */
    val task: String,
    /** "" when dispatched outside any phase. */
    val phase: String,
    /** Replayed from the resume journal. */
    val cached: Boolean,
    val status: OrchStatus,
    /** The member's own tool calls, arrival order. */
    val children: List<ToolCallItem>,
    val result: ToolCallItem.SubAgentResult?,
    /**
     * What to SHOW for this member, which is not the same question as "did it
     * file a report". [ToolCallItem.subAgentResult] only recognises the
     * `{agent,status,summary}` shape the CLI wraps a plain delegation in; an
     * `agent()` call given a `schema` returns its structured value instead
     * (`{"content":"beta\n","file":"b.txt"}`), and a member that just answered
     * in prose returns the prose. Both have no `summary`, so trusting `result`
     * alone makes a finished member render as an empty row — the card would be
     * hiding output it is holding. This is the summary when there is one and
     * the raw output otherwise, and it is "" only when the member really said
     * nothing.
     */
    val resultText: String,
    /** [resultText] is pretty-printed JSON: show it preformatted, not as prose. */
    val resultIsJSON: Boolean,
) {
    /**
     * Why this member failed, in one string. The agent's reported summary is
     * the best answer and is already parsed; when the output was not the
     * report shape we take the error-ish field out of whatever JSON it was,
     * and failing that the raw text — a provider's plain "429 after 3
     * attempts" is the case that matters most and never arrives as a report.
     */
    val failureReason: String
        get() {
            if (status != OrchStatus.FAILED) return ""
            val reported = result?.summary?.trim().orEmpty()
            if (reported.isNotEmpty()) return reported
            val raw = tool.output.trim()
            if (raw.isEmpty()) return ""
            if (raw.startsWith("{")) {
                val obj = parseObject(raw) ?: return raw
                for (key in listOf("error", "message", "summary", "reason")) {
                    val found = obj[key].asString?.trim()
                    if (!found.isNullOrEmpty()) return found
                }
                return ""
            }
            return raw
        }
}

@Immutable
data class WorkflowPhase(
    /** "" is the trailing "no phase" bucket. */
    val title: String,
    val detail: String,
    /** The script entered it at runtime instead of declaring it up front. */
    val dynamic: Boolean,
    val members: List<MemberCall>,
    val counts: OrchCounts,
) {
    /** Running outranks failed outranks done: what is happening now is what
     *  the header should report, even if something already went wrong beside
     *  it — the failure still has its own row and its own count. A member
     *  listed but not started yet keeps the phase in progress. */
    val state: PhaseState
        get() = when {
            members.isEmpty() -> PhaseState.PENDING
            counts.running > 0 || counts.pending > 0 -> PhaseState.RUNNING
            counts.failed > 0 -> PhaseState.FAILED
            else -> PhaseState.DONE
        }
}

enum class PhaseState { PENDING, RUNNING, FAILED, DONE }

/**
 * The `workflow` tool call the model made — the script itself, not the run.
 *
 * Kept apart from [WorkflowRun] because the two disagree about what they are:
 * the run is a live tree of agents, this is a submitted program and whatever
 * it evaluated to. Usually it belongs inside its run's card (a `script`
 * disclosure plus the returned value); when the call failed before a run
 * existed, it IS the whole story and renders on its own.
 */
@Immutable
data class WorkflowScript(
    /** The call itself, so a card can show its status, timing and raw output. */
    val tool: ToolCallItem,
    /** The JS the model submitted ("" when it ran a saved workflow by name). */
    val source: String,
    /** `save_as` / `name` — what the workflow is called ("" when anonymous). */
    val savedAs: String,
    /** The run_id declared by `<workflow_result>`; "" when nothing ever ran. */
    val runId: String,
    /** Where the script came from: "inline", a path, "" when unstated. */
    val origin: String,
    /** The value the script returned, pretty-printed when it is JSON. */
    val returned: String,
    val status: OrchStatus,
    /** The failure text of a call that never started a run ("" otherwise). */
    val error: String,
)

/** The checkpoint a paused run waits at and the question it asked there. */
@Immutable
data class PausedAt(val checkpointId: String = "", val message: String = "")

/** Where a run's state was read from. */
enum class RunSource { META, TEXT }

/** A workflow run: the one thing that owns sub-agents. */
@Immutable
data class WorkflowRun(
    val tool: ToolCallItem,
    val runId: String,
    val name: String,
    val description: String,
    val origin: String,
    /** The size tier ("small", "large"…), "" when the run reported none. */
    val size: String,
    /** That tier's agent guideline; 0 for none. */
    val sizeAgents: Int,
    /** The token budget the run draws on; 0 for none. */
    val budgetTokens: Long,
    val status: RunStatus,
    /** Only while paused: the checkpoint it waits at and the question asked. */
    val pausedAt: PausedAt?,
    /** Only when stopped: why ("" when the CLI gave no reason). */
    val stoppedReason: String,
    /**
     * This card was closed because a later turn continued the run on a new
     * card. Its state stays as the earlier turn left it — usually paused —
     * but it is superseded, not waiting, and must say so rather than spin.
     */
    val continued: Boolean,
    /** The card that took over, when the CLI named it ("" otherwise). */
    val continuedIn: String,
    /** The card this one took over from ("" for a run's first card). */
    val continuedFrom: String,
    /** "12 agents · 1 failed · 0 replayed" (or a failure's error), "" while live. */
    val summary: String,
    val phases: List<WorkflowPhase>,
    val logs: List<String>,
    /** Log lines the CLI trimmed off the front of the tail it keeps. */
    val droppedLogLines: Int,
    val counts: OrchCounts,
    /** The CLI's own text tree, as it came. */
    val rendered: String,
    /** The `workflow` tool call that submitted the script, when we could match
     *  one to this run. Null for a run whose script call never reached us, and
     *  for a card a later turn opened (its first card already shows it). */
    val script: WorkflowScript?,
    val source: RunSource,
    /** Every member in the card's order, across phases. */
    val members: List<MemberCall>,
) {
    /** Display title: "review-changes". */
    val displayTitle: String get() = name.ifEmpty { "workflow" }
}

/** A row of the folded transcript. */
@Immutable
sealed class TranscriptRow {
    abstract val id: String

    data class Item(val item: TranscriptItem) : TranscriptRow() {
        override val id: String get() = item.id
    }

    data class Agent(val member: MemberCall) : TranscriptRow() {
        override val id: String get() = "agent-${member.tool.id}"
    }

    data class Run(val run: WorkflowRun) : TranscriptRow() {
        override val id: String get() = "run-${run.tool.id}"
    }

    /** A `workflow` tool call that started no run — the only surviving trace
     *  of a workflow that failed before its first agent — or a reply to a
     *  paused run's checkpoint that was refused. It carries [item] as well,
     *  so a renderer that has not learned this kind yet degrades to the
     *  ordinary tool row instead of dropping the row on the floor. */
    data class Script(
        val script: WorkflowScript,
        val item: TranscriptItem,
    ) : TranscriptRow() {
        override val id: String get() = "script-${script.tool.id}"
    }
}

// ---------------------------------------------------------------------------
// JSON accessors
// ---------------------------------------------------------------------------

/**
 * Deliberately NOT derived from [SpettroJson]: that one is lenient, and this
 * one must reject exactly what `JSON.parse` rejects, so malformed machine
 * output comes back to the reader untouched rather than quietly reformatted
 * into something the agent never produced.
 */
private val PrettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private fun parseObject(text: String): JsonObject? = try {
    SpettroJson.parseToJsonElement(text) as? JsonObject
} catch (_: Exception) {
    null
}

private fun argStr(args: JsonObject?, key: String): String = args?.get(key).asString ?: ""

private fun argBool(args: JsonObject?, key: String): Boolean = args?.get(key).asBool == true

private fun argNum(args: JsonObject?, key: String): Int? = args?.get(key).asInt

/** A count or a token total: any finite number, never below zero. */
private fun argCount(args: JsonObject?, key: String): Long {
    val value = args?.get(key).asDouble ?: return 0
    if (!value.isFinite()) return 0
    return value.toLong().coerceAtLeast(0)
}

private fun argArray(args: JsonObject?, key: String): List<JsonElement> =
    args?.get(key) as? JsonArray ?: emptyList()

private data class DeclaredPhase(val title: String, val detail: String, val dynamic: Boolean)

/**
 * The `phases` array the start payload publishes up front, so a host can
 * draw the whole plan before the first agent runs. Tolerates the degenerate
 * `["Review", …]` form as well as the `[{title, detail}]` one.
 */
private fun argPhases(args: JsonObject?): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for (entry in argArray(args, "phases")) {
        val plain = entry.asString
        if (plain != null) {
            if (plain.isNotEmpty()) out += plain to ""
            continue
        }
        val phase = entry as? JsonObject ?: continue
        val title = argStr(phase, "title")
        if (title.isNotEmpty()) out += title to argStr(phase, "detail")
    }
    return out
}

/** Pretty-prints JSON, and leaves anything else exactly as it came. Machine
 *  output that is shown to a human is worth re-indenting; prose is not. */
internal fun prettyJson(text: String): String {
    if (text.isEmpty() || (text[0] != '{' && text[0] != '[')) return text
    return try {
        PrettyJson.encodeToString(
            JsonElement.serializer(),
            SpettroJson.parseToJsonElement(text),
        )
    } catch (_: Exception) {
        text
    }
}

// ---------------------------------------------------------------------------
// Classification
// ---------------------------------------------------------------------------

/** `pending`/`in_progress`/`unknown` → running, `failed` → failed, else done. */
private fun toolStatus(tool: ToolCallItem): OrchStatus = when (tool.status) {
    AcpToolStatus.FAILED -> OrchStatus.FAILED
    AcpToolStatus.COMPLETED -> OrchStatus.DONE
    else -> OrchStatus.RUNNING
}

/**
 * The `workflow` TOOL call, as opposed to the lifecycle card of the run it
 * starts. Both are titled `workflow …`, so they are told apart by what the
 * arguments carry: an invocation carries the program (`script`, `save_as`,
 * `args`, `max_concurrency`) or a reply to a paused run (`continue_run_id`)
 * and knows nothing about a run yet, while the card carries `run_id` and
 * `workflow`. Checking for the absence of those two is what makes this safe —
 * a payload with either is never an invocation, whatever else is in it.
 */
private fun isWorkflowScriptTool(name: String, args: JsonObject?): Boolean {
    if (args == null) return false
    if (argStr(args, "run_id").isNotEmpty() || argStr(args, "workflow").isNotEmpty()) return false
    if (name != "workflow") return false
    return argStr(args, "script").isNotEmpty() ||
        argStr(args, "save_as").isNotEmpty() ||
        argStr(args, "script_path").isNotEmpty() ||
        argStr(args, "name").isNotEmpty() ||
        argStr(args, "continue_run_id").isNotEmpty()
}

/**
 * The id the CLI gives a run's card (internal/acp/workflow.go cardID):
 * `workflow-<run id>` for the first card, `workflow-<run id>-<n>` for the n-th
 * a later turn opened to continue it. A run with no id gets `wf-<n>`, which an
 * older CLI also used for every card. Run ids are `wf_<time>_<hex>`, so the
 * trailing `-<n>` can never be part of one.
 */
private val CARD_ID_RE = Regex("""workflow-(.+?)(?:-([0-9]+))?""")
private val LEGACY_CARD_ID_RE = Regex("""wf-[0-9]+""")

private fun runIdFromCardId(id: String): String =
    CARD_ID_RE.matchEntire(id)?.groupValues?.get(1) ?: ""

/** A card a later turn opened to continue a run (attach 2 and on). */
private fun isContinuingCard(tool: ToolCallItem, meta: CardState?): Boolean {
    if (meta != null && meta.continuedFrom.isNotEmpty()) return true
    return CARD_ID_RE.matchEntire(tool.id)?.groups?.get(2) != null
}

/**
 * The lifecycle card. Recognised by its metadata when it has some, by its id
 * when that has the card shape, and — for an older CLI — by args carrying
 * `workflow` but no `agent`.
 *
 * A progress trace (`workflow X ▸ Phase`, `workflow X · log`,
 * `workflow X ⏸ cp-1`) is consumed inside the CLI and normally never reaches
 * us — but when one does escape (its run's card was not open in that turn) it
 * arrives as an ordinary `call-N` row, and it must not be mistaken for a new
 * run: a checkpoint trace carries `workflow` and no `agent`, exactly like a
 * card, and used to open a phantom run of its own.
 */
private fun isWorkflowTool(tool: ToolCallItem, name: String, args: JsonObject?): Boolean {
    if (tool.workflow != null) return true
    if (escapedTrace(tool, name, args)) return false
    if (CARD_ID_RE.matches(tool.id) || LEGACY_CARD_ID_RE.matches(tool.id)) return true
    if (args != null) {
        if (argStr(args, "workflow").isNotEmpty() && argStr(args, "agent").isEmpty()) return true
        if (argStr(args, "workflow").isNotEmpty()) return false
    }
    return name == "workflow" || name.startsWith("workflow ")
}

private fun escapedTrace(tool: ToolCallItem, name: String, args: JsonObject?): Boolean {
    if (name.startsWith("workflow-progress")) return true
    if (argStr(args, "kind").isNotEmpty() || argStr(args, "checkpoint_id").isNotEmpty()) return true
    return tool.title.contains('⏸') || tool.title.contains(" ▸ ") || tool.title.contains(" · log")
}

// ---------------------------------------------------------------------------
// Counting
// ---------------------------------------------------------------------------

private fun countMembers(members: List<MemberCall>): OrchCounts {
    var running = 0
    var done = 0
    var failed = 0
    var pending = 0
    var stopped = 0
    var cached = 0
    for (member in members) {
        when (member.status) {
            OrchStatus.RUNNING -> running++
            OrchStatus.DONE -> done++
            OrchStatus.FAILED -> failed++
            OrchStatus.PENDING -> pending++
            OrchStatus.STOPPED -> stopped++
        }
        if (member.cached) cached++
    }
    return OrchCounts(members.size, running, done, failed, pending, stopped, cached)
}

// ---------------------------------------------------------------------------
// The card's state, from either source
// ---------------------------------------------------------------------------

/** One member as the card itself lists it. */
data class DeclaredMember(
    val instance: String,
    val task: String,
    val phase: String,
    val status: OrchStatus,
    val replayed: Boolean,
)

/** One phase as the card itself lists it. */
data class CardPhase(
    val title: String,
    val detail: String = "",
    val dynamic: Boolean = false,
)

/** What a card says about its run — from the meta or from the text. */
private data class CardState(
    val runId: String,
    val name: String,
    val description: String,
    val size: String,
    val sizeAgents: Int,
    val budgetTokens: Long,
    /** The CLI's lifecycle word: running, paused, stopped, success, failed,
     *  cancelled, or one this code does not know ("" when unstated). */
    val status: String,
    val pausedAt: PausedAt?,
    val stoppedReason: String,
    val continued: Boolean,
    val continuedIn: String,
    val continuedFrom: String,
    val summary: String,
    val phases: List<CardPhase>,
    /** null when the source lists no members at all (an older text format),
     *  so the member calls themselves are the only record. */
    val members: List<DeclaredMember>?,
    val logs: List<String>,
    val droppedLogLines: Int,
)

private val META_MEMBER_STATUS = mapOf(
    "running" to OrchStatus.RUNNING,
    "done" to OrchStatus.DONE,
    "failed" to OrchStatus.FAILED,
    "pending" to OrchStatus.PENDING,
)

/**
 * Reads `_meta["spettro.app/workflow"]` (version 1). Every field is checked:
 * a newer CLI may add fields, and a malformed one must degrade to the text
 * path rather than throw — a blank chat is a worse failure than a card drawn
 * from text.
 */
private fun parseWorkflowMeta(meta: JsonObject?): CardState? {
    if (meta == null) return null
    val version = meta["version"].asDouble
    // A version this code was not written for may mean fields it would
    // misread; the text is still there.
    if (version != null && version > 1) return null

    val phases = argArray(meta, "phases").mapNotNull { entry ->
        val phase = entry as? JsonObject ?: return@mapNotNull null
        CardPhase(argStr(phase, "title"), argStr(phase, "detail"), argBool(phase, "dynamic"))
    }
    val members = argArray(meta, "members").mapNotNull { entry ->
        val member = entry as? JsonObject ?: return@mapNotNull null
        val instance = argStr(member, "instance")
        if (instance.isEmpty()) return@mapNotNull null
        DeclaredMember(
            instance = instance,
            task = argStr(member, "task"),
            phase = argStr(member, "phase"),
            // "an unknown member status is pending" (docs/acp.md).
            status = META_MEMBER_STATUS[argStr(member, "status")] ?: OrchStatus.PENDING,
            replayed = argBool(member, "replayed"),
        )
    }
    val paused = meta["pausedAt"] as? JsonObject
    val logs = argArray(meta, "logTail").mapNotNull { it.asString }
    val continuedIn = argStr(meta, "continuedIn")
    return CardState(
        runId = argStr(meta, "runId"),
        name = argStr(meta, "name"),
        description = argStr(meta, "description"),
        size = argStr(meta, "size"),
        sizeAgents = argCount(meta, "sizeAgents").coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        budgetTokens = argCount(meta, "budgetTokens"),
        status = argStr(meta, "status"),
        pausedAt = paused?.let { PausedAt(argStr(it, "checkpointId"), argStr(it, "message")) },
        stoppedReason = argStr(meta, "stoppedReason"),
        continued = continuedIn.isNotEmpty(),
        continuedIn = continuedIn,
        continuedFrom = argStr(meta, "continuedFrom"),
        summary = argStr(meta, "summary"),
        phases = phases,
        members = members,
        logs = logs,
        droppedLogLines = argCount(meta, "droppedLogLines").coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
}

// ---------------------------------------------------------------------------
// The CLI's rendered tree, read back
// ---------------------------------------------------------------------------

// Each of these is matched against a whole line or a whole block with
// `matchEntire`, never with `^…$`: Java's `$` also matches before a final line
// terminator, JavaScript's (without the m flag) does not, and the two readers
// have to agree on every byte. Digits are spelled `[0-9]` because Android's
// ICU-backed `\d` takes every script's digits and the CLI writes only ASCII.

/** `"12 agents · 1 failed · 0 replayed"` — the finish output of
 *  workflowObserver.finish(). A run that failed sends the error text instead. */
private val SUMMARY_RE = Regex("""[0-9]+ agents? · [0-9]+ failed · [0-9]+ replayed""")

/** The prefix the CLI puts on a card a later turn took over
 *  (takeWorkflowLocked). */
private const val CONTINUED_PREFIX = "continued in a later turn"

/** `size: large (~30 agents, a guideline) · budget 500k tokens`. */
private val SIZE_RE =
    Regex("""size: (\S+) \((?:~([0-9]+) agents, a guideline|no guideline)\)(?: · budget (\S+) tokens)?""")

/** `⏸ paused at cp-1 — waiting for orchestrator: fix which?`; the checkpoint
 *  and the message are each optional. */
private val PAUSED_RE = Regex("""⏸ (?:paused at (.+?) — )?waiting for orchestrator(?:: ([\s\S]*))?""")

/** `■ stopped: at the orchestrator's request`; the reason is optional. */
private val STOPPED_RE = Regex("""■ stopped(?:: ([\s\S]*))?""")

/** A status the CLI does not name, shown as is on a card that stays open. */
private val STATUS_RE = Regex("""status: (.+)""")

/** `▸ Scan (added at runtime) — 2/3 done, 1 failed` / `○ Fix — pending`. */
private val PHASE_RE = Regex("""([▸○]) (.*) — (?:pending|[0-9]+/[0-9]+ done(?:, [0-9]+ failed)?)""")
private const val RUNTIME_SUFFIX = " (added at runtime)"

/** `    ↳ find candidates` */
private val DETAIL_RE = Regex(""" {4}↳ (.*)""")

/** `    ✓ gp#1  replayed · scan a` — two spaces between instance and label
 *  (workflow_test.go wfMemberLine is the CLI's own reading of it). */
private val MEMBER_RE = Regex(""" {4}([✓✗▶·]) (\S+) {2}(replayed · )?(.*)""")

/** `… 12 earlier lines` — the head of the log the CLI trimmed. */
private val DROPPED_RE = Regex("""… ([0-9]+) earlier lines?""")

private val GLYPH_STATUS = mapOf(
    "✓" to OrchStatus.DONE,
    "✗" to OrchStatus.FAILED,
    "▶" to OrchStatus.RUNNING,
    "·" to OrchStatus.PENDING,
)

private val COMPACT_TOKENS_RE = Regex("""([0-9]+(?:\.[0-9]+)?)([km]?)""")

/** `500k`, `1.5m`, `950` — compactTokens() in workflow.go, read back. */
fun parseCompactTokens(text: String): Long {
    val match = COMPACT_TOKENS_RE.matchEntire(text.trim().lowercase()) ?: return 0
    val n = match.groupValues[1].toDoubleOrNull() ?: return 0
    val scale = when (match.groupValues[2]) {
        "m" -> 1_000_000.0
        "k" -> 1_000.0
        else -> 1.0
    }
    return Math.round(n * scale)
}

data class RenderedWorkflow(
    val continued: Boolean = false,
    val summary: String = "",
    val description: String = "",
    val size: String = "",
    val sizeAgents: Int = 0,
    val budgetTokens: Long = 0,
    /** "paused" | "stopped" | an unnamed status | "" (running or finished). */
    val status: String = "",
    val pausedAt: PausedAt? = null,
    val stoppedReason: String = "",
    val phases: List<CardPhase> = emptyList(),
    /** Empty for a tree that lists no members (or no tree). */
    val members: List<DeclaredMember> = emptyList(),
    val logs: List<String> = emptyList(),
    val droppedLogLines: Int = 0,
)

/**
 * Recovers the run from the text the CLI rendered into the card
 * (acpWorkflow.render in internal/acp/workflow.go, plus what the finish and
 * the continue put in front of it). The format is:
 *
 *     continued in a later turn            <- only on a card a later turn took over
 *                                          <- blank line
 *     12 agents · 1 failed · 0 replayed    <- only once finished (the error, if it failed)
 *                                          <- blank line
 *     <description>                        <- only when the script set one
 *                                          <- blank line
 *     size: large (~30 agents, a guideline) · budget 500k tokens
 *                                          <- blank line
 *     ⏸ paused at cp-1 — waiting for orchestrator: fix which?
 *       (or ■ stopped: <reason>, or status: <unnamed>)
 *                                          <- blank line
 *     ▸ Review — 2/3 done, 1 failed
 *         ↳ one agent per dimension
 *         ✓ review#1  label text
 *         · review#4  replayed · label text
 *     ○ Verify (added at runtime) — pending
 *                                          <- blank line
 *     log:
 *       … 12 earlier lines
 *       a log line
 *
 * A parse failure degrades to empty — a missing description is a cosmetic
 * loss, a thrown exception is a blank chat.
 */
fun parseRenderedWorkflow(output: String, failed: Boolean): RenderedWorkflow {
    return try {
        var text = output.replace("\r\n", "\n").trim()
        if (text.isEmpty()) return RenderedWorkflow()
        var continued = false
        if (text == CONTINUED_PREFIX || text.startsWith("$CONTINUED_PREFIX\n")) {
            continued = true
            text = text.substring(CONTINUED_PREFIX.length).trim()
        }
        val lines = text.split("\n")

        // The log block runs to the end of the output.
        val logs = mutableListOf<String>()
        var dropped = 0
        var end = lines.size
        for (i in lines.indices) {
            if (lines[i] != "log:") continue
            end = i
            for (j in i + 1 until lines.size) {
                val entry = lines[j].trim()
                if (entry.isEmpty()) continue
                val head = DROPPED_RE.matchEntire(entry)
                if (head != null && logs.isEmpty()) {
                    dropped = head.groupValues[1].toIntOrNull() ?: 0
                    continue
                }
                logs += entry
            }
            break
        }

        // The head and the tree are blank-line-separated blocks, so the tree
        // is found as a block rather than by hunting for the first glyph
        // anywhere in the output. A description is free prose the script
        // author wrote, and prose that happens to contain a line starting with
        // "▸" would otherwise invent a phase out of nothing *and* truncate the
        // description at that line. Matching a whole block — every line of it
        // a phase header, a detail line or a member row — cannot be fooled by
        // one stray character.
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (i in 0 until end) {
            if (lines[i].isBlank()) {
                current = null
                continue
            }
            if (current == null) {
                current = mutableListOf()
                blocks += current
            }
            current += lines[i]
        }

        fun isTreeLine(line: String) =
            PHASE_RE.matches(line) || DETAIL_RE.matches(line) || MEMBER_RE.matches(line)
        fun isTreeBlock(block: List<String>) =
            block.any { PHASE_RE.matches(it) } && block.all(::isTreeLine)

        // The last qualifying block, not the first: the tree is always the
        // final thing before the log, and taking the last one means an earlier
        // false positive loses to the real thing.
        var treeIndex = blocks.size
        for (b in blocks.indices.reversed()) {
            if (isTreeBlock(blocks[b])) {
                treeIndex = b
                break
            }
        }
        val phases = mutableListOf<CardPhase>()
        val members = mutableListOf<DeclaredMember>()
        if (treeIndex < blocks.size) {
            var phase: CardPhase? = null
            for (line in blocks[treeIndex]) {
                val header = PHASE_RE.matchEntire(line)
                if (header != null) {
                    var title = header.groupValues[2]
                    val dynamic = title.endsWith(RUNTIME_SUFFIX)
                    if (dynamic) title = title.removeSuffix(RUNTIME_SUFFIX)
                    if (title == "(no phase)") title = ""
                    phase = CardPhase(title, "", dynamic)
                    phases += phase
                    continue
                }
                val detail = DETAIL_RE.matchEntire(line)
                if (detail != null) {
                    if (phase != null) {
                        val updated = phase.copy(detail = detail.groupValues[1])
                        phases[phases.lastIndex] = updated
                        phase = updated
                    }
                    continue
                }
                val member = MEMBER_RE.matchEntire(line)
                if (member != null) {
                    members += DeclaredMember(
                        instance = member.groupValues[2],
                        task = member.groupValues[4],
                        phase = phase?.title ?: "",
                        status = GLYPH_STATUS[member.groupValues[1]] ?: OrchStatus.PENDING,
                        replayed = member.groups[3] != null,
                    )
                }
            }
        }

        // Everything before the tree is the head: the finish summary (or, on a
        // failed run, the error), the description, the size line and the state
        // line. The CLI writes them in that order, so the fixed-format lines
        // are taken off the END of the head and the summary off the front;
        // whatever is left is the description, which is free prose and can
        // contain anything — including blank lines.
        val head = blocks.subList(0, treeIndex)
            .map { it.joinToString("\n").trim() }
            .toMutableList()
        fun last(): String = head.lastOrNull() ?: ""

        var status = ""
        var pausedAt: PausedAt? = null
        var stoppedReason = ""
        val paused = PAUSED_RE.matchEntire(last())
        val stopped = STOPPED_RE.matchEntire(last())
        val unnamed = STATUS_RE.matchEntire(last())
        when {
            paused != null -> {
                status = "paused"
                pausedAt = PausedAt(
                    checkpointId = paused.groups[1]?.value ?: "",
                    message = (paused.groups[2]?.value ?: "").trim(),
                )
                head.removeAt(head.lastIndex)
            }
            stopped != null -> {
                status = "stopped"
                stoppedReason = (stopped.groups[1]?.value ?: "").trim()
                head.removeAt(head.lastIndex)
            }
            unnamed != null -> {
                status = unnamed.groupValues[1].trim()
                head.removeAt(head.lastIndex)
            }
        }
        var size = ""
        var sizeAgents = 0
        var budgetTokens = 0L
        val sizeLine = SIZE_RE.matchEntire(last())
        if (sizeLine != null) {
            size = sizeLine.groupValues[1]
            sizeAgents = sizeLine.groups[2]?.value?.toIntOrNull() ?: 0
            budgetTokens = sizeLine.groups[3]?.value?.let(::parseCompactTokens) ?: 0
            head.removeAt(head.lastIndex)
        }
        var summary = ""
        if (head.isNotEmpty() && (SUMMARY_RE.matches(head[0]) || failed)) {
            summary = head.removeAt(0)
        }
        RenderedWorkflow(
            continued = continued,
            summary = summary,
            description = head.joinToString(" ").split("\n").joinToString(" ").trim(),
            size = size,
            sizeAgents = sizeAgents,
            budgetTokens = budgetTokens,
            status = status,
            pausedAt = pausedAt,
            stoppedReason = stoppedReason,
            phases = phases,
            members = members,
            logs = logs,
            droppedLogLines = dropped,
        )
    } catch (_: Exception) {
        RenderedWorkflow()
    }
}

/** The card's state from its text (and start args, which win where both
 *  speak): the path for an older CLI and an older snapshot. */
private fun textState(tool: ToolCallItem, args: JsonObject?, name: String): CardState {
    val text = parseRenderedWorkflow(tool.output, tool.status == AcpToolStatus.FAILED)

    // Structured args win; the text fills what they do not say. A phase the
    // script entered at runtime is only ever in the text, so the text's order
    // is kept for those, after the declared ones.
    val phases = mutableListOf<CardPhase>()
    val textPhase = text.phases.associateBy { it.title }
    for ((title, detail) in argPhases(args)) {
        if (phases.any { it.title == title }) continue
        phases += CardPhase(
            title = title,
            detail = detail.ifEmpty { textPhase[title]?.detail ?: "" },
            dynamic = false,
        )
    }
    for (phase in text.phases) {
        if (phases.none { it.title == phase.title }) phases += phase
    }

    val size = argStr(args, "size")
    val budget = argCount(args, "budget_tokens")
    return CardState(
        runId = argStr(args, "run_id").ifEmpty { runIdFromCardId(tool.id) },
        name = name,
        description = argStr(args, "description").ifEmpty { text.description },
        size = size.ifEmpty { text.size },
        sizeAgents = if (size.isNotEmpty()) {
            argCount(args, "size_agents").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            text.sizeAgents
        },
        budgetTokens = if (budget > 0) budget else text.budgetTokens,
        status = text.status,
        pausedAt = text.pausedAt,
        stoppedReason = text.stoppedReason,
        continued = text.continued,
        continuedIn = "",
        continuedFrom = "",
        summary = text.summary,
        phases = phases,
        members = text.members.ifEmpty { null },
        logs = text.logs,
        droppedLogLines = text.droppedLogLines,
    )
}

private val BUDGET_SUFFIX_RE = Regex("""budget \S+""")
private val SIZE_SUFFIX_RE = Regex("""[a-z]+""")

/**
 * The run's name. The card's title is `workflow <name>[ · <size>][ · budget
 * <n>]` (acpWorkflow.title), so the title is only a source once those
 * suffixes are off it — a card re-announced in a later turn carries no args
 * to name it otherwise.
 */
fun nameFromTitle(title: String): String {
    var text = title.trim()
    if (text.startsWith("[")) {
        val close = text.indexOf(']')
        if (close >= 0) text = text.substring(close + 1).trim()
    }
    if (text == "workflow") return ""
    if (text.startsWith("workflow ")) text = text.removePrefix("workflow ")
    val parts = text.split(" · ").toMutableList()
    while (parts.size > 1) {
        val tail = parts.last()
        if (!BUDGET_SUFFIX_RE.matches(tail) && !SIZE_SUFFIX_RE.matches(tail)) break
        parts.removeAt(parts.lastIndex)
    }
    return parts.joinToString(" · ").trim()
}

/** A run's display state from the CLI's lifecycle word and the card's own
 *  ACP status — which settles it whenever the two disagree, because it is the
 *  one every client is guaranteed to have. */
private fun runStatus(state: CardState, tool: ToolCallItem): Pair<RunStatus, String> {
    val closed = toolStatus(tool)
    when (state.status) {
        // A paused card stays in progress; one that was closed with the pause
        // still showing is a continued card, which keeps saying it paused.
        "paused" -> return (if (closed == OrchStatus.FAILED) RunStatus.FAILED else RunStatus.PAUSED) to ""
        "stopped" -> return RunStatus.STOPPED to state.stoppedReason
        // The CLI closes a cancelled run as failed, but nothing went wrong in
        // it: the user (or the session) ended it.
        "cancelled", "canceled" -> return RunStatus.STOPPED to state.stoppedReason.ifEmpty { "cancelled" }
    }
    return when (closed) {
        OrchStatus.FAILED -> RunStatus.FAILED to ""
        OrchStatus.DONE -> RunStatus.DONE to ""
        else -> RunStatus.RUNNING to ""
    }
}

// ---------------------------------------------------------------------------
// The `workflow` tool call, read back
// ---------------------------------------------------------------------------

private val WORKFLOW_RESULT_RE = Regex("""<workflow_result([^>]*)>""")
private val RUN_ID_ATTR_RE = Regex("""\brun_id="([^"]*)"""")
private val NAME_ATTR_RE = Regex("""\bname="([^"]*)"""")
private val RETURNED_RE = Regex("""<returned>([\s\S]*?)</returned>""")
private val ORIGIN_RE = Regex("""^Script:\s*([^\n·]+)""", RegexOption.MULTILINE)

/**
 * The block the tool answers with (internal/agent/workflow_trace.go
 * renderWorkflowResult):
 *
 *     <workflow_result name="check-files" run_id="wf_2026…">
 *     <summary>3 agents · 0 failed · 0 replayed from journal · 29203 tokens</summary>
 *     <phases>Read</phases>
 *     <returned>
 *     [ "…", "…" ]
 *     </returned>
 *     </workflow_result>
 *     Script: inline · transcript: /home/…/workflows/wf_2026…
 *
 * `run_id` is the reliable way to tie the call to its run, and `<returned>` is
 * the script's actual answer — the one thing in the whole exchange the model
 * wrote code to produce, and the thing a raw-JSON row buried. Anything that
 * fails to match degrades to "", never to a throw: a workflow whose output
 * shape drifts must still render.
 */
private fun newScript(tool: ToolCallItem, args: JsonObject?): WorkflowScript {
    val output = tool.output
    val header = WORKFLOW_RESULT_RE.find(output)
    val attrs = header?.groupValues?.get(1) ?: ""
    val runId = RUN_ID_ATTR_RE.find(attrs)?.groupValues?.get(1) ?: ""
    val resultName = NAME_ATTR_RE.find(attrs)?.groupValues?.get(1) ?: ""
    val returned = RETURNED_RE.find(output)?.groupValues?.get(1) ?: ""
    val origin = ORIGIN_RE.find(output)?.groupValues?.get(1)?.trim() ?: ""

    val savedAs = when {
        argStr(args, "save_as").isNotEmpty() -> argStr(args, "save_as")
        argStr(args, "name").isNotEmpty() -> argStr(args, "name")
        else -> resultName
    }

    val status = toolStatus(tool)
    return WorkflowScript(
        tool = tool,
        source = argStr(args, "script"),
        savedAs = savedAs,
        runId = runId.ifEmpty { argStr(args, "continue_run_id") },
        origin = origin.ifEmpty { argStr(args, "script_path") },
        returned = prettyJson(returned.trim()),
        status = status,
        // A call that produced a <workflow_result> ran; anything else it
        // printed while failing is the reason it never did.
        error = if (status == OrchStatus.FAILED && header == null) output.trim() else "",
    )
}

// ---------------------------------------------------------------------------
// Grouping
// ---------------------------------------------------------------------------

/** Mutable scaffolding for one card while the first pass fills it in. */
private class RunBuild(
    val tool: ToolCallItem,
    val args: JsonObject?,
    val name: String,
    val runId: String,
    val meta: CardState?,
    val script: WorkflowScript?,
) {
    /** The member calls routed to this card as they arrived. */
    val members = mutableListOf<MemberBuild>()
}

/** Mutable scaffolding for one member; [children] grows as nested calls land. */
private class MemberBuild(val call: MemberCall) {
    val children = mutableListOf<ToolCallItem>()
    fun freeze(): MemberCall = call.copy(children = children.toList())
}

private fun newMember(tool: ToolCallItem, args: JsonObject?): MemberCall {
    val call = tool.subAgentCall
    val instance = argStr(args, "agent").ifEmpty { call?.agent ?: "" }
    val task = argStr(args, "task").ifEmpty { call?.task ?: "" }
    val result = tool.subAgentResult
    val hash = instance.indexOf('#')
    val shown = memberOutput(tool, result)
    return MemberCall(
        tool = tool,
        instance = instance,
        specId = if (hash > 0) instance.substring(0, hash) else instance,
        index = argNum(args, "index"),
        task = task,
        phase = argStr(args, "phase"),
        cached = argBool(args, "cached"),
        status = if (result?.status == "error") OrchStatus.FAILED else toolStatus(tool),
        children = emptyList(),
        result = result,
        resultText = shown.first,
        resultIsJSON = shown.second,
    )
}

/**
 * A member the card lists whose own call is not in this transcript (a chat
 * reopened mid-run, or a card that carried members over from a turn this one
 * never saw). It still gets a row — the card says it exists — with nothing to
 * expand.
 */
private fun standInMember(card: ToolCallItem, declared: DeclaredMember): MemberCall {
    val hash = declared.instance.indexOf('#')
    return MemberCall(
        tool = ToolCallItem(
            id = "${card.id}:${declared.instance}",
            title = "agent ${declared.instance}: ${declared.task}",
            kind = "think",
            status = when (declared.status) {
                OrchStatus.DONE -> AcpToolStatus.COMPLETED
                OrchStatus.FAILED -> AcpToolStatus.FAILED
                else -> AcpToolStatus.IN_PROGRESS
            },
            timestamp = card.timestamp,
        ),
        instance = declared.instance,
        specId = if (hash > 0) declared.instance.substring(0, hash) else declared.instance,
        index = null,
        task = declared.task,
        phase = declared.phase,
        cached = declared.replayed,
        status = declared.status,
        children = emptyList(),
        result = null,
        resultText = "",
        resultIsJSON = false,
    )
}

/**
 * The member's output as something showable. [ToolCallItem.subAgentResult]
 * answers a narrower question — "did this agent file a `{agent,status,summary}`
 * report" — and its contract is relied on elsewhere, so the widening happens
 * here: a schema'd `agent()` call returns its structured value and a prose
 * answer returns prose, and neither has a `summary` to find. Structured output
 * is re-indented because the CLI sends it minified onto one enormous line.
 */
private fun memberOutput(
    tool: ToolCallItem,
    result: ToolCallItem.SubAgentResult?,
): Pair<String, Boolean> {
    if (result != null && result.summary.isNotEmpty()) return result.summary to false
    val raw = tool.output.trim()
    if (raw.isEmpty()) return "" to false
    val structured = raw.startsWith("{") || raw.startsWith("[")
    return (if (structured) prettyJson(raw) else raw) to structured
}

/**
 * The card's member list: the ones its own state names, in its order, each
 * matched to its call by instance across the whole run (a continued card lists
 * members an earlier card's turn dispatched), and then any call routed here
 * that the state does not mention yet. The card's word on a member's status
 * wins — it is the run's view of it, and a continued card must show the run as
 * its turn left it.
 */
private fun cardMembers(
    build: RunBuild,
    state: CardState,
    pool: Map<String, MemberBuild>,
): List<MemberCall> {
    val routed = build.members.map { it.freeze() }
    val declared = state.members ?: return routed
    val out = mutableListOf<MemberCall>()
    val listed = mutableSetOf<String>()
    for (entry in declared) {
        if (!listed.add(entry.instance)) continue
        val call = pool[entry.instance]?.freeze()
        out += if (call != null) {
            call.copy(
                phase = entry.phase,
                cached = call.cached || entry.replayed,
                status = if (call.status == OrchStatus.FAILED) OrchStatus.FAILED else entry.status,
                task = call.task.ifEmpty { entry.task },
            )
        } else {
            standInMember(build.tool, entry)
        }
    }
    for (member in routed) {
        if (member.instance !in listed) out += member
    }
    return out
}

/**
 * Which member pool a run's card reads. Every card of one run shares its run
 * id's pool; a run with no id (`wf-<n>`, and every card of an older CLI) gets a
 * pool of its own, because the empty id is not one run — pooling them all
 * under it would hand one run's `code#1` to every other run's card.
 */
private fun poolKey(runId: String, build: RunBuild): String =
    runId.ifEmpty { "card:${build.tool.id}" }

private fun finishWorkflow(
    build: RunBuild,
    pools: Map<String, Map<String, MemberBuild>>,
): WorkflowRun {
    val tool = build.tool
    val state = build.meta ?: textState(tool, build.args, build.name)
    val pool = pools[poolKey(state.runId.ifEmpty { build.runId }, build)] ?: emptyMap()
    val (status, stoppedReason) = runStatus(state, tool)
    // A run that has ended has nobody still working in it. A member the card
    // last listed as running was cut off with it — a cancelled turn closes the
    // card without a word about its members — so it is settled as stopped
    // rather than left spinning under a finished run forever.
    val ended = status == RunStatus.DONE || status == RunStatus.FAILED || status == RunStatus.STOPPED
    val members = cardMembers(build, state, pool).map { member ->
        if (ended && member.status == OrchStatus.RUNNING) member.copy(status = OrchStatus.STOPPED) else member
    }

    // Phases in the card's order; then phases only a member names; then the
    // unnamed bucket, which always trails and only exists when it holds
    // someone. The meta already lists them so — this keeps the text path and
    // the bare-calls path drawing the same tree.
    val order = mutableListOf<CardPhase>()
    val seen = mutableSetOf<String>()
    for (phase in state.phases) {
        if (phase.title.isEmpty() || !seen.add(phase.title)) continue
        order += phase
    }
    for (member in members) {
        if (member.phase.isEmpty() || !seen.add(member.phase)) continue
        order += CardPhase(member.phase)
    }
    val phases = order.map { phase ->
        val inPhase = members.filter { it.phase == phase.title }
        WorkflowPhase(phase.title, phase.detail, phase.dynamic, inPhase, countMembers(inPhase))
    }.toMutableList()
    val loose = members.filter { it.phase.isEmpty() }
    if (loose.isNotEmpty()) {
        phases += WorkflowPhase("", "", false, loose, countMembers(loose))
    }

    return WorkflowRun(
        tool = tool,
        runId = state.runId.ifEmpty { build.runId },
        name = state.name.ifEmpty { build.name },
        description = state.description.ifEmpty { argStr(build.args, "description") },
        origin = argStr(build.args, "origin"),
        size = state.size,
        sizeAgents = state.sizeAgents,
        budgetTokens = state.budgetTokens,
        status = status,
        pausedAt = if (status == RunStatus.PAUSED) state.pausedAt ?: PausedAt() else null,
        stoppedReason = stoppedReason,
        continued = state.continued,
        continuedIn = state.continuedIn,
        continuedFrom = state.continuedFrom,
        summary = if (status == RunStatus.DONE || status == RunStatus.FAILED) state.summary else "",
        phases = phases,
        logs = state.logs,
        droppedLogLines = state.droppedLogLines,
        counts = countMembers(members),
        rendered = tool.output.trim(),
        script = build.script,
        source = if (build.meta != null) RunSource.META else RunSource.TEXT,
        members = members,
    )
}

/** A script call and whether a run has taken it. */
private class ScriptEntry(val script: WorkflowScript) {
    var claimed = false
}

/**
 * Finds the script call a lifecycle card belongs to and marks it taken.
 *
 * `run_id` is the honest link and is used whenever the `<workflow_result>`
 * block carried one. The fallback is positional and safe because a script call
 * is the thing that *starts* a run, so it can only ever precede its own
 * lifecycle card, and the nearest unclaimed one above is the only candidate.
 * Earlier unclaimed calls are left alone — they are failed attempts, and
 * stealing one into this run would hide it.
 */
private fun claimScript(
    scripts: List<ScriptEntry>,
    byRunId: Map<String, ScriptEntry>,
    runId: String,
): WorkflowScript? {
    val exact = if (runId.isNotEmpty()) byRunId[runId] else null
    if (exact != null && !exact.claimed) {
        exact.claimed = true
        return exact.script
    }
    for (i in scripts.indices.reversed()) {
        val entry = scripts[i]
        if (entry.claimed) continue
        // A script call that already names a *different* run is not this one's.
        if (entry.script.runId.isNotEmpty() && runId.isNotEmpty() &&
            entry.script.runId != runId
        ) {
            continue
        }
        entry.claimed = true
        return entry.script
    }
    return null
}

/**
 * Folds a flat transcript into rows: workflow runs absorb their members,
 * members absorb their own tool calls, everything else passes through
 * untouched and in order.
 *
 * Two linear passes — one to index runs, members and children, one to emit —
 * because this runs on every recomposition of a transcript that can hold
 * thousands of items, and a per-item scan of the list would make a long
 * session crawl.
 */
fun groupTranscript(items: List<TranscriptItem>): List<TranscriptRow> {
    val builds = linkedMapOf<String, RunBuild>()
    val byRunId = mutableMapOf<String, RunBuild>()
    val members = mutableMapOf<String, MemberBuild>()
    val standalone = mutableMapOf<String, MemberBuild>()
    val absorbed = mutableSetOf<String>()
    // Every workflow member call, by run id and then instance: a card a later
    // turn opened lists members an earlier card's turn dispatched.
    val pools = mutableMapOf<String, MutableMap<String, MemberBuild>>()
    // Script calls seen so far, oldest first, with the ones already claimed by
    // a run marked. Whatever is still unclaimed at the end started no run and
    // becomes a row of its own.
    val scripts = mutableListOf<ScriptEntry>()
    val scriptByRunId = mutableMapOf<String, ScriptEntry>()
    // `workflow {"continue_run_id": …}` calls: the model answering a paused
    // run's checkpoint (or stopping it). The run's card already shows what that
    // did, so one that went through folds away; one that was refused is the
    // only place the refusal is said, and keeps a row.
    val replies = mutableListOf<WorkflowScript>()
    var lastWorkflow: RunBuild? = null
    var openWorkflow: RunBuild? = null

    // Pass 1 — index.
    for (item in items) {
        val tool = (item as? TranscriptItem.Tool)?.tool ?: continue
        val (prefix, name, args) = tool.parsedTitle()

        if (isWorkflowScriptTool(name, args)) {
            val script = newScript(tool, args)
            if (argStr(args, "continue_run_id").isNotEmpty()) {
                replies += script
                continue
            }
            val entry = ScriptEntry(script)
            scripts += entry
            if (script.runId.isNotEmpty() && script.runId !in scriptByRunId) {
                scriptByRunId[script.runId] = entry
            }
            continue
        }

        if (isWorkflowTool(tool, name, args)) {
            val meta = parseWorkflowMeta(tool.workflow)
            val runId = meta?.runId?.ifEmpty { null }
                ?: argStr(args, "run_id").ifEmpty { null }
                ?: runIdFromCardId(tool.id)
            val build = RunBuild(
                tool = tool,
                args = args,
                name = meta?.name?.ifEmpty { null }
                    ?: argStr(args, "workflow").ifEmpty { null }
                    ?: nameFromTitle(tool.title),
                runId = runId,
                meta = meta,
                // Only a run's first card claims its script: a card a later
                // turn opened continues a program an earlier card already shows.
                script = if (isContinuingCard(tool, meta)) {
                    null
                } else {
                    claimScript(scripts, scriptByRunId, runId)
                },
            )
            builds[tool.id] = build
            if (runId.isNotEmpty()) byRunId[runId] = build
            lastWorkflow = build
            if (toolStatus(tool) == OrchStatus.RUNNING) openWorkflow = build
            continue
        }

        // A member's own call is titled with its own instance in brackets, so
        // a bracket that names *someone else* is what marks a nested child.
        val owner = if (prefix != null && prefix != argStr(args, "agent")) {
            members[prefix]
        } else {
            null
        }
        if (owner != null) {
            owner.children += tool
            absorbed += item.id
            continue
        }

        if (tool.subAgentCall == null) continue

        val member = MemberBuild(newMember(tool, args))
        if (member.call.instance.isNotEmpty()) members[member.call.instance] = member

        var run: RunBuild? = null
        if (argStr(args, "workflow").isNotEmpty() && member.call.instance.isNotEmpty()) {
            val runId = argStr(args, "run_id")
            run = (if (runId.isNotEmpty()) byRunId[runId] else null) ?: openWorkflow ?: lastWorkflow
            if (run != null) {
                val pool = pools.getOrPut(poolKey(runId.ifEmpty { run.runId }, run)) { mutableMapOf() }
                pool[member.call.instance] = member
            }
        }
        if (run != null) {
            run.members += member
            absorbed += item.id
        } else {
            standalone[tool.id] = member
        }
    }

    // Finalise each run once, now that every member has landed.
    val runs = builds.mapValues { (_, build) -> finishWorkflow(build, pools) }

    // A script call that found a run is now part of that run's card; one that
    // did not is a workflow that never began, and dropping it would erase the
    // only evidence it was ever attempted.
    val orphans = mutableMapOf<String, WorkflowScript>()
    val claimed = mutableSetOf<String>()
    for (entry in scripts) {
        if (entry.claimed) claimed += entry.script.tool.id else orphans[entry.script.tool.id] = entry.script
    }
    for (reply in replies) {
        if (reply.status != OrchStatus.FAILED && reply.runId in byRunId) {
            claimed += reply.tool.id
        } else {
            orphans[reply.tool.id] = reply
        }
    }

    // Pass 2 — emit, in the original order.
    val rows = mutableListOf<TranscriptRow>()
    for (item in items) {
        if (item is TranscriptItem.Tool) {
            val id = item.tool.id
            val run = runs[id]
            if (run != null) {
                rows += TranscriptRow.Run(run)
                continue
            }
            val member = standalone[id]
            if (member != null) {
                rows += TranscriptRow.Agent(member.freeze())
                continue
            }
            val orphan = orphans[id]
            if (orphan != null) {
                rows += TranscriptRow.Script(orphan, item)
                continue
            }
            if (id in claimed) continue
        }
        if (item.id in absorbed) continue
        rows += TranscriptRow.Item(item)
    }
    return rows
}

// ---------------------------------------------------------------------------
// Derived views
// ---------------------------------------------------------------------------

/** Runs still working, in transcript order — what the live strip shows. A
 *  paused run is waiting on the model, not working, and a continued card has
 *  handed the run on to its successor. */
fun activeRuns(rows: List<TranscriptRow>): List<WorkflowRun> =
    rows.mapNotNull { row ->
        (row as? TranscriptRow.Run)?.run?.takeIf { it.status == RunStatus.RUNNING && !it.continued }
    }

/** "Large · ~30 agents" — the size tier in plain words, "" when the run
 *  reported none. */
fun sizeLabel(run: WorkflowRun): String {
    if (run.size.isEmpty()) return ""
    val tier = run.size.replaceFirstChar { it.uppercaseChar() }
    return if (run.sizeAgents > 0) "$tier · ~${run.sizeAgents} agents" else "$tier · no agent limit"
}

/** "Large · ~30 agents · 500k token budget" — what the run was sized for, in
 *  plain words. "" when it reported neither. */
fun planLabel(run: WorkflowRun): String {
    val parts = mutableListOf<String>()
    val size = sizeLabel(run)
    if (size.isNotEmpty()) parts += size
    if (run.budgetTokens > 0) parts += "${compactTokens(run.budgetTokens)} token budget"
    return parts.joinToString(" · ")
}

private val SYNTAX_ERROR_RE = Regex("""does not parse|SyntaxError""")
private val LINE_RE = Regex("""\bline ([0-9]+)""", RegexOption.IGNORE_CASE)
private val MISSING_WORKFLOW_RE = Regex("""no saved workflow named "([^"]+)"""")
private val ERROR_PREFIX_RE = Regex("""^error:\s*""", RegexOption.IGNORE_CASE)
private val WORKFLOW_PREFIX_RE = Regex("""^workflow(?: "[^"]*")?:\s*""", RegexOption.IGNORE_CASE)

/**
 * A workflow's failure in one plain sentence, for a card whose run died before
 * any member could carry the reason — a script that would not parse, a saved
 * workflow that is not there. The CLI's text is `error: workflow "x": script
 * does not parse: SyntaxError: (anonymous): Line 35:177 Unexpected token )`,
 * which is accurate and unreadable; the cases worth a sentence get one, and
 * anything else is shown with its plumbing ("error: workflow …:") taken off.
 * "" for an empty input.
 */
fun plainWorkflowError(raw: String): String {
    val first = raw.trim().split("\n").firstOrNull()?.trim() ?: ""
    if (first.isEmpty()) return ""
    if (SYNTAX_ERROR_RE.containsMatchIn(first)) {
        val line = LINE_RE.find(first)
        return if (line == null) {
            "The workflow script has a syntax error."
        } else {
            "The workflow script has a syntax error (line ${line.groupValues[1]})."
        }
    }
    MISSING_WORKFLOW_RE.find(first)?.let {
        return "There’s no saved workflow named “${it.groupValues[1]}”."
    }
    val text = first.replace(ERROR_PREFIX_RE, "").replace(WORKFLOW_PREFIX_RE, "").trim()
    if (text.isEmpty()) return ""
    return text.replaceFirstChar { it.uppercaseChar() }
}

private val COUNTS_SUMMARY_RE = Regex("""^[0-9]+ agents? · """)

/**
 * Why a failed run failed, when no member can say it: the run died on the way
 * in (a script that would not parse) or around its agents rather than in one.
 * The CLI puts that error where a finished run's counts go, so it is the
 * summary — unless the summary IS the counts, in which case a member failed
 * and its own row carries the reason. Null when there is nothing to add.
 */
fun runFailure(run: WorkflowRun): String? {
    if (run.status != RunStatus.FAILED || run.counts.failed > 0) return null
    val raw = run.summary.trim()
    if (COUNTS_SUMMARY_RE.containsMatchIn(raw)) return null
    return plainWorkflowError(raw).ifEmpty { "The workflow ended with an error before it finished." }
}

/**
 * The sentence an open card leads with: why a failed run failed, what a paused
 * run waits for, why a stopped one stopped, where a continued one went. ""
 * for a run that is simply working or finished. "Waiting" is the paused run's
 * whole story — the checkpoint's question is what it waits on, and Spettro
 * (the orchestrating model) is who answers.
 */
fun runStateLine(run: WorkflowRun): String {
    if (run.continued) return "Continued below — this run went on in a later turn"
    runFailure(run)?.let { return it }
    if (run.status == RunStatus.PAUSED) {
        val message = run.pausedAt?.message ?: ""
        return if (message.isEmpty()) "Waiting for Spettro to continue it" else "Waiting — $message"
    }
    if (run.status == RunStatus.STOPPED) {
        return if (run.stoppedReason.isEmpty()) "Stopped" else "Stopped — ${run.stoppedReason}"
    }
    return ""
}

/** The folded card's one line beside its badge once the run is not working:
 *  the badge already names the state, so this is what it is about — the
 *  question a paused run asks, why a stopped one stopped, what a finished one
 *  concluded. */
fun runStateDetail(run: WorkflowRun): String {
    if (run.continued) return run.description
    if (run.status == RunStatus.PAUSED) {
        val message = run.pausedAt?.message ?: ""
        return message.ifEmpty { "Waiting for Spettro to continue it" }
    }
    if (run.status == RunStatus.STOPPED) return run.stoppedReason
    runFailure(run)?.let { return it }
    return runSummaryText(run)
}

/** The line a finished run settles to: what the CLI concluded, or — when the
 *  finish output never arrived — the same shape rebuilt from the counts. */
fun runSummaryText(run: WorkflowRun): String {
    if (run.summary.isNotEmpty()) return run.summary
    val counts = run.counts
    if (counts.total == 0) return if (run.status == RunStatus.FAILED) "failed" else "no agents"
    val parts = mutableListOf("${counts.total} ${if (counts.total == 1) "agent" else "agents"}")
    if (counts.failed > 0) parts += "${counts.failed} failed"
    if (counts.stopped > 0) parts += "${counts.stopped} stopped"
    if (counts.cached > 0) parts += "${counts.cached} replayed"
    return parts.joinToString(" · ")
}

/**
 * The line the live strip settles to when a run leaves it. A run that left
 * because it is waiting at a checkpoint, or was stopped, says that — "done"
 * would be a claim about a run that has not finished.
 */
fun runSettledNote(run: WorkflowRun): String {
    if (run.status == RunStatus.PAUSED) {
        val message = run.pausedAt?.message ?: ""
        return if (message.isEmpty()) "Waiting for Spettro" else "Waiting — $message"
    }
    if (run.status == RunStatus.STOPPED) {
        return if (run.stoppedReason.isEmpty()) "Stopped" else "Stopped — ${run.stoppedReason}"
    }
    if (run.summary.isNotEmpty()) return run.summary
    val counts = run.counts
    if (counts.total == 0) return "finished"
    return if (counts.failed > 0) "${counts.done} done · ${counts.failed} failed" else "${counts.done} done"
}

/**
 * Trims a phase to [cap] rows. Port of the `prio` ordering in
 * view_workflow.go's workflowRow: running (and not yet started) rows are never
 * dropped, then failures are kept ahead of successes, because a failure is the
 * row a reader most wants to see among work that is already over — and a
 * member cut off by the run ending is as unfinished as a failure, and just as
 * worth a row. Survivors stay in dispatch order — reordering them would make
 * the list churn as it fills.
 */
fun capMembers(members: List<MemberCall>, cap: Int): Pair<List<MemberCall>, Int> {
    if (members.size <= cap) return members to 0
    val keep = mutableSetOf<Int>()
    members.forEachIndexed { i, member ->
        if (member.status == OrchStatus.RUNNING || member.status == OrchStatus.PENDING) keep += i
    }
    var budget = (cap - keep.size).coerceAtLeast(0)
    for (status in listOf(OrchStatus.FAILED, OrchStatus.STOPPED, OrchStatus.DONE)) {
        members.forEachIndexed { i, member ->
            if (budget == 0 || member.status != status || i in keep) return@forEachIndexed
            keep += i
            budget -= 1
        }
    }
    val shown = members.filterIndexed { i, _ -> i in keep }
    return shown to (members.size - shown.size)
}

// ---------------------------------------------------------------------------
// Name truncation
// ---------------------------------------------------------------------------

/**
 * Shortens an instance name while KEEPING its "#N" suffix — port of
 * truncateAgentName() in internal/tui/view_workflow.go. Members of a run share
 * a long spec prefix ("general-purpose#7"), so a plain clip throws away the
 * only part of the name that tells one member from another.
 */
fun truncateInstance(name: String, max: Int): String {
    if (max < 4 || name.length <= max) return truncateLabel(name, max)
    val hash = name.lastIndexOf('#')
    if (hash <= 0) return truncateLabel(name, max)
    val suffix = name.substring(hash)
    val keep = max - suffix.length - 1
    if (keep < 1) return truncateLabel(name, max)
    return name.substring(0, keep) + "…" + suffix
}

private fun truncateLabel(text: String, max: Int): String = when {
    max <= 0 -> ""
    text.length <= max -> text
    max <= 1 -> text.substring(0, max)
    else -> text.substring(0, max - 1) + "…"
}

/** Drops the `[instance] ` prefix [ToolCallItem.displayDetail] re-applies —
 *  the row already says whose work this is, so repeating it eats the width. */
internal fun stripInstance(detail: String, instance: String): String {
    val prefix = "[$instance] "
    return if (detail.startsWith(prefix)) detail.removePrefix(prefix) else detail
}
