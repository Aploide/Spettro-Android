package to.eyed.spettro.mobile.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/**
 * Builders that produce transcript items shaped exactly the way the Go CLI puts
 * them on the ACP wire. Port of the desktop app's `tests/wire.ts`.
 *
 * These are not conveniences — they are the specification the tests are written
 * against, so each one records where its shape comes from. Getting a builder
 * wrong makes every test using it agree with the wrong thing, which is
 * precisely how hand-written visual fixtures encoded a misreading of the CLI
 * twice on the desktop side before a recording caught it. Anything asserted
 * here that is not obvious from the Go source is called out in a comment
 * naming the file.
 */
object Wire {

    private var seq = 0

    /** Resets the id counter so ids are stable within a test. */
    fun reset() {
        seq = 0
    }

    private fun stamp(index: Int): String = "2026-08-24T12:%02d:%02dZ".format(index / 60, index % 60)

    fun tool(
        title: String,
        kind: String? = null,
        status: AcpToolStatus = AcpToolStatus.COMPLETED,
        output: String = "",
        argsJSON: String? = null,
        id: String? = null,
        workflow: JsonObject? = null,
    ): TranscriptItem {
        seq += 1
        return TranscriptItem.Tool(
            ToolCallItem(
                id = id ?: "call-$seq",
                title = title,
                kind = kind,
                status = status,
                output = output,
                argsJSON = argsJSON,
                timestamp = stamp(seq),
                workflow = workflow,
            ),
        )
    }

    fun message(role: ChatMessage.Role, text: String): TranscriptItem {
        seq += 1
        return TranscriptItem.Message(
            ChatMessage(id = "msg-$seq", role = role, text = text, timestamp = stamp(seq)),
        )
    }

    private fun json(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(",", "{", "}") { (key, value) ->
            val encoded = when (value) {
                null -> "null"
                is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                is Boolean, is Int, is Long -> value.toString()
                is List<*> -> value.joinToString(",", "[", "]") { entry ->
                    when (entry) {
                        is String -> "\"$entry\""
                        else -> entry.toString()
                    }
                }
                else -> value.toString()
            }
            "\"$key\":$encoded"
        }

    /**
     * The workflow lifecycle card at `start`, as an older CLI sent it: no
     * `_meta`, an id `wf-N`.
     *
     * internal/agent/workflow_trace.go: the observer publishes the declared
     * phase list up front, from `meta`, so a host can draw the whole plan
     * before the first agent runs. For the card the current CLI sends, see
     * [workflowCard].
     */
    fun workflowStart(
        runId: String,
        name: String,
        description: String = "",
        phases: List<Pair<String, String>> = emptyList(),
        rendered: String = "",
    ): TranscriptItem {
        seq += 1
        val phaseJson = phases.joinToString(",", "[", "]") { (title, detail) ->
            """{"title":"$title","detail":"$detail"}"""
        }
        return TranscriptItem.Tool(
            ToolCallItem(
                id = "wf-$seq",
                title = "workflow $name",
                kind = "think",
                status = AcpToolStatus.IN_PROGRESS,
                output = rendered,
                argsJSON = """{"run_id":"$runId","workflow":"$name",""" +
                    """"description":"$description","origin":"inline","phases":$phaseJson}""",
                timestamp = stamp(seq),
            ),
        )
    }

    /**
     * The SAME lifecycle call after it finishes, as recorded from an older CLI.
     *
     * This is the shape that makes the text fallback necessary: the session
     * applies rawInput on every update, and that CLI's finish payload was
     * `{run_id, workflow, agents, failed, cached, tokens}` — no `phases`, no
     * `description`. A run reloaded from disk has only ever seen this version.
     */
    fun workflowFinished(
        id: String,
        runId: String,
        name: String,
        agents: Int,
        failed: Int = 0,
        cached: Int = 0,
        rendered: String,
        status: AcpToolStatus = AcpToolStatus.COMPLETED,
    ): TranscriptItem = TranscriptItem.Tool(
        ToolCallItem(
            id = id,
            title = "workflow $name",
            kind = "think",
            status = status,
            output = rendered,
            // Note the type: a count here, a bool on a member trace. Decoding
            // both into one struct is what broke the CLI's own finish path.
            argsJSON = json(
                "run_id" to runId, "workflow" to name, "agents" to agents,
                "failed" to failed, "cached" to cached, "tokens" to 1234,
            ),
            timestamp = stamp(1),
        ),
    )

    /** A workflow member's `agent` call (internal/agent/workflow_trace.go). */
    fun member(
        instance: String,
        task: String,
        runId: String,
        workflow: String,
        phase: String = "",
        index: Int = 1,
        cached: Boolean = false,
        status: AcpToolStatus = AcpToolStatus.IN_PROGRESS,
        output: String = "",
    ): TranscriptItem = tool(
        title = "agent $instance: $task",
        kind = "think",
        status = status,
        output = output,
        argsJSON = json(
            "agent" to instance, "task" to task, "parent_agent_id" to "coding",
            "workflow" to workflow, "run_id" to runId, "phase" to phase,
            "index" to index, "cached" to cached,
        ),
    )

    /**
     * The model's invocation of the `workflow` TOOL — the script, not the run.
     *
     * Confirmed from a recorded session: this arrives as an ordinary tool call
     * titled `workflow {…}` whose rawInput carries the whole program, and whose
     * output is the `<workflow_result>` block. It is a separate call from the
     * run's card and lands just before it.
     */
    fun scriptCall(
        script: String,
        savedAs: String = "",
        runId: String = "",
        returned: String = "ok",
        status: AcpToolStatus = AcpToolStatus.COMPLETED,
        error: String? = null,
    ): TranscriptItem {
        val output = error ?: listOf(
            """<workflow_result name="$savedAs" run_id="$runId">""",
            "<summary>3 agents · 0 failed · 0 replayed from journal · 100 tokens</summary>",
            "<returned>",
            returned,
            "</returned>",
            "</workflow_result>",
            "Script: inline · transcript: /tmp/x",
        ).joinToString("\n")
        return tool(
            title = """workflow {"save_as": "$savedAs", "script": "export const meta = …""",
            kind = "think",
            status = status,
            output = output,
            argsJSON = json(
                "script" to script, "save_as" to savedAs,
                "args" to "{}", "max_concurrency" to 3,
            ),
        )
    }

    /**
     * A tool call made BY a sub-agent.
     *
     * internal/acp/tools.go finishTitle only brackets the instance onto the
     * title when the instance name contains '#' — so workflow members are
     * attributable and a plain delegation like `explore` is not. Verified
     * against a recording: `explore`'s own `ls` call arrives titled plainly
     * `ls {…}`.
     */
    fun childCall(instance: String, name: String, argsJson: String): TranscriptItem {
        val prefix = if (instance.contains('#')) "[$instance] " else ""
        return tool(
            title = "$prefix$name $argsJson",
            kind = if (name == "bash") "execute" else "read",
            argsJSON = argsJson,
        )
    }

    /** A plain delegation — an `agent` call belonging to no run. */
    fun delegation(
        agent: String,
        task: String,
        status: AcpToolStatus = AcpToolStatus.COMPLETED,
        summary: String? = null,
    ): TranscriptItem = tool(
        title = "agent $agent: $task",
        kind = "think",
        status = status,
        output = if (summary == null) {
            ""
        } else {
            json("agent" to agent, "status" to "ok", "summary" to summary)
        },
        argsJSON = json("agent" to agent, "task" to task, "parent_agent_id" to "coding"),
    )

    data class RenderedMember(val glyph: String, val instance: String, val label: String)
    data class RenderedPhase(
        val title: String,
        val members: List<RenderedMember> = emptyList(),
    )

    /** The CLI's own rendered phase tree (acpWorkflow.render), for the text
     *  recovery path. The format is stable and this mirrors it exactly. */
    fun renderedTree(
        summary: String = "",
        description: String = "",
        phases: List<RenderedPhase>,
        logs: List<String> = emptyList(),
    ): String {
        val lines = mutableListOf<String>()
        if (summary.isNotEmpty()) { lines += summary; lines += "" }
        if (description.isNotEmpty()) { lines += description; lines += "" }
        for (phase in phases) {
            if (phase.members.isEmpty()) {
                lines += "○ ${phase.title} — pending"
                continue
            }
            val done = phase.members.count { it.glyph != "▶" }
            lines += "▸ ${phase.title} — $done/${phase.members.size} done"
            for (m in phase.members) {
                lines += "    ${m.glyph} ${m.instance}  ${m.label}"
            }
        }
        if (logs.isNotEmpty()) {
            lines += ""
            lines += "log:"
            for (line in logs) lines += "  $line"
        }
        return lines.joinToString("\n")
    }

    // -----------------------------------------------------------------------
    // The workflow card the current CLI sends (internal/acp/workflow.go)
    // -----------------------------------------------------------------------

    /** A member as acpWorkflow records it: the trace's own status word. */
    data class CardAgent(
        val instance: String,
        val task: String,
        val phase: String = "",
        /** "running" | "success" | "error", or any word the runtime adds. */
        val status: String,
        val cached: Boolean = false,
    )

    data class CardPhaseIn(val title: String, val detail: String = "", val dynamic: Boolean = false)

    /** The fields of acpWorkflow that render() and metaView() read. */
    data class CardRun(
        val runId: String,
        val name: String,
        val description: String = "",
        val size: String = "",
        val sizeAgents: Int = 0,
        val budget: Long = 0,
        /** "", "running", "paused", "stopped", "success", "error", or unnamed. */
        val status: String = "",
        val checkpointId: String = "",
        val waiting: String = "",
        val stopReason: String = "",
        /** Declared and phase()-noted phases, in addPhase order. */
        val phases: List<CardPhaseIn> = emptyList(),
        val agents: List<CardAgent> = emptyList(),
        val logs: List<String> = emptyList(),
        val dropped: Int = 0,
        val attach: Int = 1,
        val continuedFrom: String = "",
    )

    /** compactTokens (workflow.go): 500k, 1.5m. */
    private fun compact(n: Long): String = to.eyed.spettro.mobile.core.compactTokens(n)

    /** acpWorkflow.title(). */
    fun cardTitle(run: CardRun): String {
        var title = "workflow ${run.name}"
        if (run.size.isNotEmpty()) title += " · ${run.size}"
        if (run.budget > 0) title += " · budget ${compact(run.budget)}"
        return title
    }

    private class PhaseView(
        val title: String,
        val detail: String,
        val dynamic: Boolean,
        val members: List<CardAgent>,
    ) {
        val finished = members.count { it.status == "success" || it.status == "error" }
        val failed = members.count { it.status == "error" }
    }

    /** phaseOrder() + phaseViews(): declared phases, then undeclared ones a
     *  member named, then the "" bucket when anyone is in it. */
    private fun phaseViews(run: CardRun): List<PhaseView> {
        val order = run.phases.map { it.title }.toMutableList()
        var loose = false
        for (a in run.agents) {
            if (a.phase.isEmpty()) loose = true else if (a.phase !in order) order += a.phase
        }
        if (loose) order += ""
        return order.map { title ->
            val info = run.phases.firstOrNull { it.title == title }
            PhaseView(
                title = title,
                detail = info?.detail ?: "",
                dynamic = info?.dynamic ?: false,
                members = run.agents.filter { it.phase == title },
            )
        }
    }

    private fun glyph(status: String): String = when (status) {
        "success" -> "✓"
        "error" -> "✗"
        "running" -> "▶"
        else -> "·"
    }

    /** acpWorkflow.render(), line for line. */
    fun renderCard(run: CardRun): String {
        val b = StringBuilder()
        if (run.description.isNotEmpty()) b.append(run.description).append("\n\n")
        if (run.size.isNotEmpty()) {
            b.append(
                if (run.sizeAgents > 0) {
                    "size: ${run.size} (~${run.sizeAgents} agents, a guideline)"
                } else {
                    "size: ${run.size} (no guideline)"
                },
            )
            if (run.budget > 0) b.append(" · budget ${compact(run.budget)} tokens")
            b.append("\n\n")
        }
        when (run.status) {
            "", "running", "success", "error" -> Unit
            "stopped" -> b.append("■ stopped")
                .append(if (run.stopReason.isNotEmpty()) ": ${run.stopReason}" else "")
                .append("\n\n")
            "paused" -> {
                b.append("⏸ ")
                if (run.checkpointId.isNotEmpty()) b.append("paused at ${run.checkpointId} — ")
                b.append("waiting for orchestrator")
                if (run.waiting.isNotEmpty()) b.append(": ${run.waiting}")
                b.append("\n\n")
            }
            else -> b.append("status: ${run.status}\n\n")
        }
        for (p in phaseViews(run)) {
            var title = if (p.title.isEmpty()) "(no phase)" else p.title
            if (p.dynamic) title += " (added at runtime)"
            if (p.members.isEmpty()) {
                b.append("○ $title — pending\n")
            } else {
                b.append("▸ $title — ${p.finished}/${p.members.size} done")
                if (p.failed > 0) b.append(", ${p.failed} failed")
                b.append("\n")
            }
            if (p.detail.isNotEmpty()) b.append("    ↳ ${p.detail}\n")
            for (a in p.members) {
                b.append("    ${glyph(a.status)} ${a.instance}  ${if (a.cached) "replayed · " else ""}${a.task}\n")
            }
        }
        if (run.logs.isNotEmpty()) {
            b.append("\nlog:\n")
            if (run.dropped > 0) b.append("  … ${run.dropped} earlier lines\n")
            for (line in run.logs) b.append("  $line\n")
        }
        return b.toString().trimEnd('\n')
    }

    private fun str(value: String) = JsonPrimitive(value)
    private fun num(value: Number) = JsonPrimitive(value)
    private fun bool(value: Boolean) = JsonPrimitive(value)

    /** workflowMetaStatus + metaView(): the `_meta["spettro.app/workflow"]`
     *  payload, from the same state [renderCard] reads. */
    fun cardMeta(run: CardRun, continuedIn: String = "", summary: String = ""): JsonObject {
        val status = when (run.status) {
            "", "running" -> "running"
            "error", "failed" -> "failed"
            "cancelled", "canceled" -> "cancelled"
            else -> run.status
        }
        val meta = linkedMapOf<String, JsonElement>(
            "version" to num(1),
            "runId" to str(run.runId),
            "name" to str(run.name),
            "description" to str(run.description),
            "size" to str(run.size),
            "sizeAgents" to num(run.sizeAgents),
            "budgetTokens" to num(run.budget),
            "status" to str(status),
            "attach" to num(run.attach),
            "logTail" to JsonArray(run.logs.map(::str)),
            "droppedLogLines" to num(run.dropped),
        )
        if (status == "paused") {
            meta["pausedAt"] = JsonObject(
                mapOf("checkpointId" to str(run.checkpointId), "message" to str(run.waiting)),
            )
        }
        if (status == "stopped") meta["stoppedReason"] = str(run.stopReason)
        if (run.continuedFrom.isNotEmpty()) meta["continuedFrom"] = str(run.continuedFrom)
        if (continuedIn.isNotEmpty()) meta["continuedIn"] = str(continuedIn)
        if (summary.isNotEmpty()) meta["summary"] = str(summary)
        val phases = mutableListOf<JsonElement>()
        val members = mutableListOf<JsonElement>()
        var agents = 0
        var failed = 0
        var replayed = 0
        for (p in phaseViews(run)) {
            val phase = linkedMapOf<String, JsonElement>(
                "title" to str(p.title),
                "dynamic" to bool(p.dynamic),
                "done" to num(p.finished),
                "total" to num(p.members.size),
                "failed" to num(p.failed),
            )
            if (p.detail.isNotEmpty()) phase["detail"] = str(p.detail)
            phases += JsonObject(phase)
            for (a in p.members) {
                members += JsonObject(
                    mapOf(
                        "instance" to str(a.instance),
                        "task" to str(a.task),
                        "phase" to str(a.phase),
                        "status" to str(
                            when (a.status) {
                                "success" -> "done"
                                "error" -> "failed"
                                "running" -> "running"
                                else -> "pending"
                            },
                        ),
                        "replayed" to bool(a.cached),
                    ),
                )
                agents += 1
                if (a.status == "error") failed += 1
                if (a.cached) replayed += 1
            }
        }
        meta["phases"] = JsonArray(phases)
        meta["members"] = JsonArray(members)
        meta["counts"] = JsonObject(
            mapOf("agents" to num(agents), "failed" to num(failed), "replayed" to num(replayed)),
        )
        return JsonObject(meta)
    }

    /** cardID(): `workflow-<run id>`, plus `-<attach>` past the first card. */
    fun cardId(run: CardRun): String =
        if (run.attach <= 1) "workflow-${run.runId}" else "workflow-${run.runId}-${run.attach}"

    /** The start args a card keeps as its rawInput: the declared plan. */
    private fun cardArgs(run: CardRun): String = buildString {
        append("{")
        append(json("run_id" to run.runId, "workflow" to run.name, "description" to run.description,
            "origin" to "inline").removeSurrounding("{", "}"))
        append(",\"phases\":")
        append(
            run.phases.filter { !it.dynamic }.joinToString(",", "[", "]") {
                json("title" to it.title, "detail" to it.detail)
            },
        )
        append(",")
        append(json("size" to run.size, "size_agents" to run.sizeAgents, "budget_tokens" to run.budget)
            .removeSurrounding("{", "}"))
        append("}")
    }

    /** What [workflowCard] should do about the card's rawInput. */
    enum class RawInput { DECLARED, NONE }

    /**
     * A run's card as the transcript holds it after the CLI's latest update:
     * title, ACP status, text and (unless `meta = false`, an older CLI) the
     * `_meta` payload. [continuedIn] builds the card an earlier turn closed
     * when a later one took the run over (takeWorkflowLocked): completed, its
     * text prefixed, its state left as that turn saw it. [summary] is the
     * finish output a success or failure puts on top (finishWorkflowLocked).
     */
    fun workflowCard(
        run: CardRun,
        meta: Boolean = true,
        continuedIn: String = "",
        summary: String = "",
        rawInput: RawInput = RawInput.DECLARED,
    ): TranscriptItem {
        val status = run.status.ifEmpty { "running" }
        val acp = when {
            continuedIn.isNotEmpty() -> AcpToolStatus.COMPLETED
            status == "success" || status == "stopped" -> AcpToolStatus.COMPLETED
            status == "error" || status == "failed" || status == "cancelled" -> AcpToolStatus.FAILED
            else -> AcpToolStatus.IN_PROGRESS
        }
        var output = renderCard(run)
        if (summary.isNotEmpty() && status != "stopped") output = "$summary\n\n$output"
        if (continuedIn.isNotEmpty()) output = "continued in a later turn\n\n$output"
        return tool(
            title = cardTitle(run),
            kind = "think",
            status = acp,
            output = output,
            argsJSON = if (rawInput == RawInput.DECLARED) cardArgs(run) else null,
            id = cardId(run),
            workflow = if (meta) cardMeta(run, continuedIn, summary) else null,
        )
    }

    /** A JSON value for [tool]'s `workflow`, from text — for malformed cases. */
    fun metaOf(text: String): JsonObject =
        to.eyed.spettro.mobile.core.SpettroJson.parseToJsonElement(text) as? JsonObject
            ?: JsonObject(mapOf("broken" to JsonNull))
}
