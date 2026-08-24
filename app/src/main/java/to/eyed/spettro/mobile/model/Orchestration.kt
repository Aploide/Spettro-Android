package to.eyed.spettro.mobile.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.acp.asBool
import to.eyed.spettro.mobile.core.acp.asInt
import to.eyed.spettro.mobile.core.acp.asString

/**
 * Folding the flat ACP transcript back into the shape the run actually had.
 * Port of the desktop app's `views/chat/transcript/orchestration.ts`.
 *
 * The CLI streams a workflow or an Ultra swarm as a *flat* sequence of tool
 * calls: one long-lived lifecycle call, one call per sub-agent, and then every
 * tool each sub-agent runs, all interleaved in arrival order. Rendered
 * literally that is a wall of rows where a twenty-agent fan-out drowns out the
 * conversation and nothing says which agent did what — the exact readability
 * problem internal/tui/view_swarm.go documents on the terminal side.
 *
 * The structure is recoverable, because every row carries who it belongs to:
 * members name their `run_id`, and nested calls are titled `[code#3] bash …`.
 * This file is the single place that reconstruction happens. It is pure and
 * memo-free on purpose: a session restored from disk must fold to exactly the
 * same tree as the live one that produced it, so the answer may depend only on
 * the items passed in.
 *
 * The one thing that is *not* recoverable from structured data is a finished
 * run's plan. `ChatSession.applyToolCallUpdate` overwrites `argsJSON` on every
 * update carrying rawInput, and the CLI's finish update re-sends a completely
 * different payload (`{run_id, workflow, agents, failed, cached, tokens}`) —
 * so `phases`, `description` and `origin` are gone the moment the run ends,
 * and gone forever once the session is reloaded. Since the phase tree is the
 * entire point of the card, we mine them back out of the CLI's own rendered
 * text (`acpWorkflow.render()` in internal/acp/workflow.go), whose format is
 * stable. Structured args always win; the text only fills what is missing.
 *
 * The other thing the wire gets wrong for us is that a workflow arrives as TWO
 * tool calls with the same name. `workflow {"save_as":…,"script":"export const
 * meta = …"}` is the model's actual invocation of the `workflow` TOOL, and it
 * carries the entire script as arguments; `workflow <name>` (call id `wf-…`)
 * is the lifecycle trace the run is built from. Left alone the first renders
 * as a full page of raw JSON directly above the card it belongs to, so it is
 * folded into the run — matched by the `run_id` its `<workflow_result>` block
 * declares, or, failing that, by position, since a script call can only ever
 * precede the run it starts. The exception is load-bearing: a script call that
 * FAILED before any run existed has no lifecycle call to hide behind, and it
 * is the only trace that a workflow was attempted at all, so it survives as a
 * row of its own.
 */

// ---------------------------------------------------------------------------
// The shapes the views render
// ---------------------------------------------------------------------------

enum class OrchStatus { RUNNING, DONE, FAILED }

@Immutable
data class OrchCounts(
    val total: Int = 0,
    val running: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val cached: Int = 0,
) {
    /** Done and failed both count as finished — a failed agent is not still working. */
    val finished: Int get() = done + failed
}

/** One sub-agent: a workflow member, a swarm member, or a plain delegation. */
@Immutable
data class MemberCall(
    val tool: ToolCallItem,
    /** "review#3"; falls back to the raw agent name. */
    val instance: String,
    /** "review" — the part before '#', for the tint. */
    val specId: String,
    /** 1-based dispatch index when the CLI sent one. */
    val index: Int?,
    /** The label / prompt / swarm item ("" if unknown). */
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
    val members: List<MemberCall>,
    val counts: OrchCounts,
) {
    /** Running outranks failed outranks done: what is happening now is what
     *  the header should report, even if something already went wrong beside
     *  it — the failure still has its own row and its own count. */
    val state: PhaseState
        get() = when {
            members.isEmpty() -> PhaseState.PENDING
            counts.running > 0 -> PhaseState.RUNNING
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

/** A workflow run or an Ultra swarm — the two things that own sub-agents. */
@Immutable
sealed class OrchRun {
    abstract val tool: ToolCallItem
    abstract val status: OrchStatus
    abstract val counts: OrchCounts
    abstract val members: List<MemberCall>

    /** Display title: "review-changes" / "ultra swarm · code". */
    abstract val displayTitle: String

    @Immutable
    data class Workflow(
        override val tool: ToolCallItem,
        val runId: String,
        val name: String,
        val description: String,
        val origin: String,
        override val status: OrchStatus,
        /** "12 agents · 1 failed · 0 replayed", "" while running. */
        val summary: String,
        val phases: List<WorkflowPhase>,
        val logs: List<String>,
        override val counts: OrchCounts,
        /** The CLI's own text tree, kept as a raw fallback. */
        val rendered: String,
        /** The `workflow` tool call that submitted the script, when we could
         *  match one to this run. Null for a run whose script call never
         *  reached us. */
        val script: WorkflowScript?,
        override val members: List<MemberCall>,
    ) : OrchRun() {
        override val displayTitle: String get() = name.ifEmpty { "workflow" }

        /** The line a finished run settles to: what the CLI concluded, or —
         *  when the finish output never arrived — the same shape rebuilt from
         *  the counts. */
        val summaryText: String
            get() {
                if (summary.isNotEmpty()) return summary
                if (counts.total == 0) {
                    return if (status == OrchStatus.FAILED) "failed" else "no agents"
                }
                val parts = mutableListOf(
                    "${counts.total} ${if (counts.total == 1) "agent" else "agents"}",
                )
                if (counts.failed > 0) parts += "${counts.failed} failed"
                if (counts.cached > 0) parts += "${counts.cached} replayed"
                return parts.joinToString(" · ")
            }
    }

    @Immutable
    data class Swarm(
        override val tool: ToolCallItem,
        val description: String,
        val subagentType: String,
        /** "" | "worktree" */
        val isolation: String,
        val items: List<String>,
        override val status: OrchStatus,
        override val members: List<MemberCall>,
        /**
         * The items no member has taken yet — the tail of [items] past the
         * members that exist, because Ultra dispatches in item order. Ultra
         * ramps its launches (five at once, then one every 700ms), so a
         * twenty-item swarm spends its first seconds with most of its work
         * un-launched; that work is pending, not absent, and the cards draw it
         * as ghost cells.
         */
        val pending: List<String>,
        /** Counted over [items], not over the members that happen to exist
         *  yet: a meter whose denominator grows makes a swarm appear to go
         *  backwards. */
        override val counts: OrchCounts,
    ) : OrchRun() {
        override val displayTitle: String
            get() = if (subagentType.isEmpty()) "ultra swarm" else "ultra swarm · $subagentType"
    }
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

    data class Run(val run: OrchRun) : TranscriptRow() {
        override val id: String get() = "run-${run.tool.id}"
    }

    /** A `workflow` tool call that started no run — the only surviving trace
     *  of a workflow that failed before its first agent. It carries [item] as
     *  well, so a renderer that has not learned this kind yet degrades to the
     *  ordinary tool row instead of dropping the row on the floor. */
    data class Script(
        val script: WorkflowScript,
        val item: TranscriptItem,
    ) : TranscriptRow() {
        override val id: String get() = "script-${script.tool.id}"
    }
}

// ---------------------------------------------------------------------------
// JSON argument accessors
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

private fun argStrings(args: JsonObject?, key: String): List<String> {
    val array = args?.get(key) as? JsonArray ?: return emptyList()
    return array.mapNotNull { it.asString }
}

/**
 * The `phases` array the workflow observer publishes up front, so a host can
 * draw the whole plan before the first agent runs. Tolerates the degenerate
 * `["Review", …]` form as well as the `[{title, detail}]` one.
 */
private fun argPhases(args: JsonObject?): List<Pair<String, String>> {
    val array = args?.get("phases") as? JsonArray ?: return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    for (entry in array) {
        val plain = entry.asString
        if (plain != null) {
            if (plain.isNotEmpty()) out += plain to ""
            continue
        }
        val obj = entry as? JsonObject ?: continue
        val title = obj["title"].asString ?: ""
        if (title.isNotEmpty()) out += title to (obj["detail"].asString ?: "")
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
 * The `workflow` TOOL call, as opposed to the lifecycle trace of the run it
 * starts. Both are titled `workflow …`, so they are told apart by what the
 * arguments carry: an invocation carries the program (`script`, `save_as`,
 * `args`, `max_concurrency`) and knows nothing about a run yet, while the
 * trace carries `run_id` and `workflow`. Checking for the absence of those
 * two is what makes this safe — a payload with either is never an invocation,
 * whatever else is in it.
 */
private fun isWorkflowScriptTool(name: String, args: JsonObject?): Boolean {
    if (args == null) return false
    if (argStr(args, "run_id").isNotEmpty() || argStr(args, "workflow").isNotEmpty()) return false
    if (name != "workflow") return false
    return argStr(args, "script").isNotEmpty() ||
        argStr(args, "save_as").isNotEmpty() ||
        argStr(args, "script_path").isNotEmpty() ||
        argStr(args, "name").isNotEmpty()
}

/**
 * The workflow lifecycle call: `workflow <name>`, args carrying `workflow`
 * but no `agent`. Progress traces (`workflow X ▸ Phase`, `workflow X · log`)
 * are consumed inside the CLI and normally never reach us — but when they do
 * escape (no run open) they must not be mistaken for a run.
 */
private fun isWorkflowTool(tool: ToolCallItem, name: String, args: JsonObject?): Boolean {
    if (args != null) {
        when (argStr(args, "kind")) {
            "phase", "log" -> return false
        }
        if (argStr(args, "workflow").isNotEmpty()) {
            return argStr(args, "agent").isEmpty()
        }
    }
    if (!name.startsWith("workflow")) return false
    return !tool.title.contains(" ▸ ") && !tool.title.contains(" · log")
}

private fun isUltraTool(name: String): Boolean = name == "ultra" || name.startsWith("ultra ")

// ---------------------------------------------------------------------------
// Counting
// ---------------------------------------------------------------------------

private fun countMembers(members: List<MemberCall>): OrchCounts {
    var running = 0
    var done = 0
    var failed = 0
    var cached = 0
    for (member in members) {
        when (member.status) {
            OrchStatus.DONE -> done++
            OrchStatus.FAILED -> failed++
            OrchStatus.RUNNING -> running++
        }
        if (member.cached) cached++
    }
    return OrchCounts(members.size, running, done, failed, cached)
}

// ---------------------------------------------------------------------------
// The CLI's rendered tree, read back
// ---------------------------------------------------------------------------

/** `"12 agents · 1 failed · 0 replayed"` — the finish output of
 *  workflowObserver.finish(). A run that failed sends the error text instead. */
private val SUMMARY_RE = Regex("""^\d+ agents · \d+ failed · \d+ replayed$""")

/** Member rows are indented under their phase; the glyph set is the one
 *  acpWorkflow.render writes. */
private val MEMBER_ROW_RE = Regex("""^\s+[✓▶✗] """)

data class RenderedWorkflow(
    val summary: String = "",
    val description: String = "",
    /** Phase titles in render order; "" for the "(no phase)" bucket. */
    val phases: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
)

/**
 * Recovers what the finish update destroyed from the text the CLI rendered
 * into the tool's output. The format (internal/acp/workflow.go) is:
 *
 *     12 agents · 1 failed · 0 replayed   <- only once finished
 *                                          <- blank line
 *     <description>                        <- only when the script set one
 *                                          <- blank line
 *     ▸ Review — 2/3 done, 1 failed
 *         ✓ review#1  label text
 *     ○ Verify — pending
 *                                          <- blank line
 *     log:
 *       a log line
 *
 * The `log:` block is the only place the script's `log()` lines survive at
 * all: the CLI folds workflow-progress traces into this call and never emits
 * them as tool calls of their own. A parse failure degrades to empty — a
 * missing description is a cosmetic loss, a thrown exception is a blank chat.
 */
fun parseRenderedWorkflow(output: String, failed: Boolean): RenderedWorkflow {
    return try {
        val text = output.trim()
        if (text.isEmpty()) return RenderedWorkflow()
        val lines = text.split("\n")

        // The log block runs to the end of the output.
        val logs = mutableListOf<String>()
        var end = lines.size
        for (i in lines.indices) {
            if (lines[i] != "log:") continue
            end = i
            for (j in i + 1 until lines.size) {
                val entry = lines[j].trim()
                if (entry.isNotEmpty()) logs += entry
            }
            break
        }

        // The head and the tree are blank-line-separated blocks, so the tree
        // is found as a block rather than by hunting for the first glyph
        // anywhere in the output. A description is free prose the script
        // author wrote, and prose that happens to contain a line starting with
        // "▸" would otherwise invent a phase out of nothing *and* truncate the
        // description at that line. Matching a whole block — every line of it
        // either a phase header or an indented member row — costs nothing and
        // cannot be fooled by one stray character.
        class Block(val start: Int) {
            val lines = mutableListOf<String>()
        }

        val blocks = mutableListOf<Block>()
        var current: Block? = null
        for (i in 0 until end) {
            if (lines[i].isBlank()) {
                current = null
                continue
            }
            if (current == null) {
                current = Block(i)
                blocks += current
            }
            current.lines += lines[i]
        }

        fun isPhaseHeader(line: String) = line.startsWith("▸ ") || line.startsWith("○ ")
        fun isMemberRow(line: String) = MEMBER_ROW_RE.containsMatchIn(line)
        fun isTreeBlock(block: Block) = block.lines.isNotEmpty() &&
            block.lines.any(::isPhaseHeader) &&
            block.lines.all { isPhaseHeader(it) || isMemberRow(it) }

        // The last qualifying block, not the first: the tree is always the
        // final thing before the log, and taking the last one means an earlier
        // false positive loses to the real thing.
        var treeStart = end
        val phases = mutableListOf<String>()
        for (b in blocks.indices.reversed()) {
            if (!isTreeBlock(blocks[b])) continue
            treeStart = blocks[b].start
            for (line in blocks[b].lines) {
                if (!isPhaseHeader(line)) continue
                val rest = line.substring(2)
                val dash = rest.lastIndexOf(" — ")
                val title = if (dash >= 0) rest.substring(0, dash) else rest
                phases += if (title == "(no phase)") "" else title
            }
            break
        }

        // The head is one or two blank-line-separated blocks: the finish
        // summary (or, on a failed run, the error) and the script's
        // description.
        val head = mutableListOf<String>()
        var block = ""
        for (i in 0 until treeStart) {
            val line = lines[i]
            if (line.isBlank()) {
                if (block.isNotEmpty()) head += block
                block = ""
                continue
            }
            block = if (block.isEmpty()) line.trim() else "$block ${line.trim()}"
        }
        if (block.isNotEmpty()) head += block

        var summary = ""
        var description = ""
        if (head.isNotEmpty() && (SUMMARY_RE.matches(head[0]) || failed)) {
            summary = head[0]
            description = head.getOrElse(1) { "" }
        } else {
            description = head.getOrElse(0) { "" }
        }
        RenderedWorkflow(summary, description, phases, logs)
    } catch (_: Exception) {
        RenderedWorkflow()
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
 * The block the tool answers with (internal/acp/workflow.go):
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
        runId = runId,
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

/** Mutable scaffolding for one run while the first pass fills it in. */
private class RunBuild(
    val isWorkflow: Boolean,
    val tool: ToolCallItem,
    val args: JsonObject?,
    val runId: String,
    var script: WorkflowScript?,
) {
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

private fun finishWorkflow(build: RunBuild): OrchRun.Workflow {
    val args = build.args
    val status = toolStatus(build.tool)
    val rendered = build.tool.output.trim()
    val text = parseRenderedWorkflow(rendered, status == OrchStatus.FAILED)
    val members = build.members.map { it.freeze() }

    // Structured args win; the text fills only what the finish update
    // destroyed.
    var declared = argPhases(args)
    if (declared.isEmpty()) {
        declared = text.phases.filter { it.isNotEmpty() }.map { it to "" }
    }

    val buckets = linkedMapOf<String, MutableList<MemberCall>>()
    for ((title, _) in declared) buckets.getOrPut(title) { mutableListOf() }
    var loose = false
    for (member in members) {
        if (member.phase.isEmpty()) {
            loose = true
            continue
        }
        buckets.getOrPut(member.phase) { mutableListOf() } += member
    }
    // The unnamed bucket always trails, and only exists when something is in it.
    if (loose) {
        buckets[""] = members.filter { it.phase.isEmpty() }.toMutableList()
    }

    val details = declared.toMap()
    val phases = buckets.map { (title, bucket) ->
        WorkflowPhase(
            title = title,
            detail = details[title] ?: "",
            members = bucket.toList(),
            counts = countMembers(bucket),
        )
    }

    val name = when {
        argStr(args, "workflow").isNotEmpty() -> argStr(args, "workflow")
        build.tool.title.startsWith("workflow ") ->
            build.tool.title.removePrefix("workflow ").trim()
        else -> build.tool.title
    }

    val declaredDescription = argStr(args, "description")
    return OrchRun.Workflow(
        tool = build.tool,
        runId = build.runId,
        name = name,
        description = declaredDescription.ifEmpty { text.description },
        origin = argStr(args, "origin"),
        status = status,
        summary = if (status == OrchStatus.RUNNING) "" else text.summary,
        phases = phases,
        logs = text.logs,
        counts = countMembers(members),
        rendered = rendered,
        script = build.script,
        members = members,
    )
}

private fun finishSwarm(build: RunBuild): OrchRun.Swarm {
    val args = build.args
    val isolation = argStr(args, "isolation")
    val items = argStrings(args, "items")
    val members = build.members.map { it.freeze() }
    // Members are dispatched in item order, so everything past the members
    // that exist is exactly what the ramp still owes.
    val pending = if (items.size > members.size) items.drop(members.size) else emptyList()
    return OrchRun.Swarm(
        tool = build.tool,
        description = argStr(args, "description"),
        subagentType = argStr(args, "subagent_type"),
        isolation = if (isolation == "worktree") "worktree" else "",
        items = items,
        status = toolStatus(build.tool),
        members = members,
        pending = pending,
        // The denominator is the work that was ASKED for. Counting launched
        // members instead made the header say "4/7" directly above "10 items"
        // — two numbers for one swarm, neither of them the one the user
        // requested.
        counts = countMembers(members).copy(total = members.size + pending.size),
    )
}

/** A script call and whether a run has taken it. */
private class ScriptEntry(val script: WorkflowScript) {
    var claimed = false
}

/**
 * Finds the script call a lifecycle trace belongs to and marks it taken.
 *
 * `run_id` is the honest link and is used whenever the `<workflow_result>`
 * block carried one. The fallback is positional and safe for the same reason
 * the swarm's is: a script call is the thing that *starts* a run, so it can
 * only ever precede its own lifecycle call, and the nearest unclaimed one
 * above is the only candidate. Earlier unclaimed calls are left alone — they
 * are failed attempts, and stealing one into this run would hide it.
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
 * Folds a flat transcript into rows: workflow/ultra runs absorb their members,
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
    // Script calls seen so far, oldest first, with the ones already claimed by
    // a run marked. Whatever is still unclaimed at the end started no run and
    // becomes a row of its own.
    val scripts = mutableListOf<ScriptEntry>()
    val scriptByRunId = mutableMapOf<String, ScriptEntry>()
    var lastWorkflow: RunBuild? = null
    var openWorkflow: RunBuild? = null
    var lastSwarm: RunBuild? = null
    var openSwarm: RunBuild? = null

    // Pass 1 — index.
    for (item in items) {
        val tool = (item as? TranscriptItem.Tool)?.tool ?: continue
        val (prefix, name, args) = tool.parsedTitle()

        if (isWorkflowScriptTool(name, args)) {
            val entry = ScriptEntry(newScript(tool, args))
            scripts += entry
            if (entry.script.runId.isNotEmpty()) scriptByRunId[entry.script.runId] = entry
            continue
        }

        if (isWorkflowTool(tool, name, args)) {
            val runId = argStr(args, "run_id")
            val build = RunBuild(
                isWorkflow = true,
                tool = tool,
                args = args,
                runId = runId,
                script = claimScript(scripts, scriptByRunId, runId),
            )
            builds[tool.id] = build
            if (runId.isNotEmpty()) byRunId[runId] = build
            lastWorkflow = build
            if (toolStatus(tool) == OrchStatus.RUNNING) openWorkflow = build
            continue
        }

        if (isUltraTool(name)) {
            val build = RunBuild(false, tool, args, runId = "", script = null)
            builds[tool.id] = build
            lastSwarm = build
            if (toolStatus(tool) == OrchStatus.RUNNING) openSwarm = build
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

        val isWorkflowMember =
            argStr(args, "workflow").isNotEmpty() && member.call.instance.isNotEmpty()
        val isSwarmMember = argBool(args, "swarm") && member.call.instance.isNotEmpty()
        val run: RunBuild? = when {
            isWorkflowMember -> {
                val runId = argStr(args, "run_id")
                (if (runId.isNotEmpty()) byRunId[runId] else null) ?: openWorkflow ?: lastWorkflow
            }
            // Swarm members carry no run_id at all — they belong to the ultra
            // call they were fanned out from, which is the nearest one still
            // in flight.
            isSwarmMember -> openSwarm ?: lastSwarm
            else -> null
        }
        if (run != null) {
            run.members += member
            absorbed += item.id
        } else {
            standalone[tool.id] = member
        }
    }

    // Finalise each run once, now that every member has landed.
    val runs = builds.mapValues { (_, build) ->
        if (build.isWorkflow) finishWorkflow(build) else finishSwarm(build)
    }

    // A script call that found a run is now part of that run's card; one that
    // did not is a workflow that never began, and dropping it would erase the
    // only evidence it was ever attempted.
    val orphans = mutableMapOf<String, WorkflowScript>()
    val claimed = mutableSetOf<String>()
    for (entry in scripts) {
        if (entry.claimed) claimed += entry.script.tool.id else orphans[entry.script.tool.id] = entry.script
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

/** Runs still in flight, in transcript order — what the live panel shows. */
fun activeRuns(rows: List<TranscriptRow>): List<OrchRun> =
    rows.mapNotNull { row ->
        (row as? TranscriptRow.Run)?.run?.takeIf { it.status == OrchStatus.RUNNING }
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
