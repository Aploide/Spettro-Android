package to.eyed.spettro.mobile.model

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
     * The workflow lifecycle call at `start`.
     *
     * internal/agent/workflow_trace.go: the observer publishes the declared
     * phase list up front, from `meta`, so a host can draw the whole plan
     * before the first agent runs. internal/acp/workflow.go gives this call an
     * id prefixed `wf-`, which is how it is told apart from the model's
     * `workflow` tool call.
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
     * The SAME lifecycle call after it finishes.
     *
     * This is the shape that makes the fold hard, and it is not a hypothetical:
     * the session applies rawInput on every update, and the CLI's finish
     * payload is `{run_id, workflow, agents, failed, cached, tokens}` — no
     * `phases`, no `description`. A run reloaded from disk has only ever seen
     * this version.
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
     * An Ultra swarm member (internal/agent/ultra.go, emitSwarmTrace).
     *
     * Deliberately carries NO run_id — swarm members have no run to name, so
     * they can only be attached to an `ultra` call by position.
     */
    fun swarmMember(
        instance: String,
        item: String,
        status: AcpToolStatus = AcpToolStatus.IN_PROGRESS,
        output: String = "",
    ): TranscriptItem = tool(
        title = "agent $instance: $item",
        kind = "think",
        status = status,
        output = output,
        argsJSON = json(
            "agent" to instance, "task" to item,
            "parent_agent_id" to "coding", "swarm" to true,
        ),
    )

    /** The `ultra` tool call itself. */
    fun ultra(
        items: List<String>,
        subagentType: String = "code",
        isolation: String = "",
        description: String = "",
        status: AcpToolStatus = AcpToolStatus.IN_PROGRESS,
    ): TranscriptItem = tool(
        title = """ultra {"description":"…"}""",
        status = status,
        argsJSON = json(
            "description" to description, "subagent_type" to subagentType,
            "prompt_template" to "Do {{item}}", "items" to items, "isolation" to isolation,
        ),
    )

    /**
     * The model's invocation of the `workflow` TOOL — the script, not the run.
     *
     * Confirmed from a recorded session: this arrives as an ordinary tool call
     * titled `workflow {…}` whose rawInput carries the whole program, and whose
     * output is the `<workflow_result>` block. It is a separate call from the
     * `wf-` lifecycle trace and lands just before it.
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
     * internal/acp/content.go only brackets the instance onto the title when
     * the instance name contains '#' — so swarm and workflow members are
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
}
