package to.eyed.spettro.mobile.ui.screens.chat

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.model.ChatMessage
import to.eyed.spettro.mobile.model.ToolCallItem
import to.eyed.spettro.mobile.model.TranscriptItem
import to.eyed.spettro.mobile.model.TranscriptRow
import to.eyed.spettro.mobile.model.WorkflowRun
import to.eyed.spettro.mobile.model.groupTranscript

/**
 * Orchestration fixtures for the previews and the screenshot harness.
 *
 * These are deliberately built as *flat transcripts* and then run through
 * [groupTranscript], exactly as a live session is: a fixture that hand-built
 * the folded tree would let the cards look right while the derivation that
 * feeds them was broken, which is the one bug this whole layer exists to
 * prevent. Every scene below is therefore also a live exercise of the fold.
 *
 * The wire shapes are the ones the CLI sends today — a card `workflow-<run>`
 * (or `workflow-<run>-2` for a later turn's) carrying its whole state as
 * `_meta["spettro.app/workflow"]`, members carrying `{agent, workflow, run_id,
 * phase, index}`, and nested calls titled `[instance] tool {...}`.
 *
 * Declaration order matters: an `object`'s properties initialise top to bottom,
 * so every constant is declared above its first use.
 */
internal object OrchestrationPreviewData {

    // -----------------------------------------------------------------------
    // Builders for the wire shapes
    // -----------------------------------------------------------------------

    private fun member(
        id: String,
        instance: String,
        task: String,
        status: AcpToolStatus,
        extraArgs: String,
        output: String = "",
    ) = ToolCallItem(
        id = id,
        title = "agent $instance: $task",
        kind = "think",
        status = status,
        output = output,
        argsJSON = """{"agent":"$instance","task":"$task","parent_agent_id":"coding"$extraArgs}""",
    )

    private fun child(
        id: String,
        instance: String,
        title: String,
        kind: String,
        status: AcpToolStatus,
    ) = ToolCallItem(id = id, title = "[$instance] $title", kind = kind, status = status)

    private fun report(agent: String, summary: String, status: String = "ok") =
        """{"agent":"$agent","status":"$status","summary":"$summary"}"""

    /** A member as the card lists it: instance, task, phase, meta status. */
    private data class Listed(
        val instance: String,
        val task: String,
        val phase: String,
        val status: String,
        val replayed: Boolean = false,
    )

    private data class Phase(val title: String, val detail: String = "", val dynamic: Boolean = false)

    /**
     * `_meta["spettro.app/workflow"]` as metaView() in internal/acp/workflow.go
     * writes it: the run, its phases with their counts, every member, and the
     * log's tail.
     */
    private fun meta(
        runId: String,
        name: String,
        status: String,
        description: String = WF_DESCRIPTION,
        size: String = "large",
        sizeAgents: Int = 30,
        budgetTokens: Long = 500_000,
        phases: List<Phase>,
        members: List<Listed>,
        logs: List<String> = emptyList(),
        dropped: Int = 0,
        attach: Int = 1,
        pausedAt: Pair<String, String>? = null,
        stoppedReason: String? = null,
        continuedIn: String? = null,
        continuedFrom: String? = null,
        summary: String? = null,
    ): JsonObject = buildJsonObject {
        put("version", 1)
        put("runId", runId)
        put("name", name)
        put("description", description)
        put("size", size)
        put("sizeAgents", sizeAgents)
        put("budgetTokens", budgetTokens)
        put("status", status)
        put("attach", attach)
        pausedAt?.let { (checkpoint, message) ->
            putJsonObject("pausedAt") {
                put("checkpointId", checkpoint)
                put("message", message)
            }
        }
        stoppedReason?.let { put("stoppedReason", it) }
        continuedIn?.let { put("continuedIn", it) }
        continuedFrom?.let { put("continuedFrom", it) }
        summary?.let { put("summary", it) }
        putJsonArray("phases") {
            for (phase in phases) {
                val inPhase = members.filter { it.phase == phase.title }
                addJsonObject {
                    put("title", phase.title)
                    if (phase.detail.isNotEmpty()) put("detail", phase.detail)
                    put("dynamic", phase.dynamic)
                    put("done", inPhase.count { it.status == "done" || it.status == "failed" })
                    put("total", inPhase.size)
                    put("failed", inPhase.count { it.status == "failed" })
                }
            }
        }
        putJsonArray("members") {
            for (m in members) {
                addJsonObject {
                    put("instance", m.instance)
                    put("task", m.task)
                    put("phase", m.phase)
                    put("status", m.status)
                    put("replayed", m.replayed)
                }
            }
        }
        putJsonObject("counts") {
            put("agents", members.size)
            put("failed", members.count { it.status == "failed" })
            put("replayed", members.count { it.replayed })
        }
        putJsonArray("logTail") { logs.forEach { add(it) } }
        put("droppedLogLines", dropped)
    }

    /** acpWorkflow.title(): `workflow <name> · <size> · budget <n>`. */
    private fun cardTitle(name: String) = "workflow $name · large · budget 500k"

    // -----------------------------------------------------------------------
    // Constants the fixtures below embed
    // -----------------------------------------------------------------------

    /** A plausible script, JSON-encoded so it survives `argsJSON` parsing. */
    private const val SCRIPT_SOURCE_JSON =
        "\"export const meta = {\\n  name: 'review-changes',\\n  description: " +
            "'Review changed files across dimensions, verify each finding',\\n  phases: " +
            "[{ title: 'Review' }, { title: 'Verify' }, { title: 'Synthesise' }],\\n}\\n\\n" +
            "const DIMENSIONS = ['correctness', 'performance', 'tests']\\n" +
            "const results = await pipeline(\\n  DIMENSIONS,\\n  d => agent(`Review the diff " +
            "for the given dimension.`, { phase: 'Review' }),\\n  review => agent(`" +
            "Adversarially verify this review.`, { phase: 'Verify' }),\\n)\\n" +
            "return results.filter(Boolean)\""

    private const val BROKEN_SCRIPT_JSON =
        "\"const PHASES = ['Read', 'Port']\\nexport const meta = {\\n  name: 'port-settings'," +
            "\\n  description: 'Port the settings screen',\\n  phases: PHASES,\\n}\\n\""

    private const val WF_RUN = "wf_20260824_a91c03"
    private const val CARD_ID = "workflow-$WF_RUN"

    private const val WF_DESCRIPTION =
        "Review the changed files across dimensions, then adversarially verify every finding"

    private val PHASES = listOf(
        Phase("Review", "one agent per dimension"),
        Phase("Verify", "three skeptics per finding"),
        Phase("Synthesise", "rank what survived"),
    )

    /** Every member the run dispatched, as the card lists them mid-flight. */
    private val LIVE_MEMBERS = listOf(
        Listed("review#1", "correctness — logic errors and unhandled states", "Review", "done"),
        Listed("review#2", "performance — allocations on the hot path", "Review", "done"),
        Listed("review#3", "tests — coverage of the changed branches", "Review", "failed"),
        Listed("verify#1", "Refute: the resume path drops queued chunks", "Verify", "running"),
        Listed("verify#2", "Refute: the pairing timeout is never cleared", "Verify", "running"),
        Listed("verify#3", "Refute: the transcript fold recomputes per frame", "Verify", "done", replayed = true),
    )

    private val LIVE_LOGS = listOf(
        "3/3 dimensions reviewed",
        "2 findings queued for verification",
    )

    // -----------------------------------------------------------------------
    // A workflow mid-flight: three phases, one member already failed
    // -----------------------------------------------------------------------

    private val workflowCard = ToolCallItem(
        id = CARD_ID,
        title = cardTitle("review-changes"),
        kind = "think",
        status = AcpToolStatus.IN_PROGRESS,
        argsJSON = """{"run_id":"$WF_RUN","workflow":"review-changes",""" +
            """"description":"$WF_DESCRIPTION","origin":"inline",""" +
            """"phases":[{"title":"Review","detail":"one agent per dimension"},""" +
            """{"title":"Verify","detail":"three skeptics per finding"},""" +
            """{"title":"Synthesise","detail":"rank what survived"}],""" +
            """"size":"large","size_agents":30,"budget_tokens":500000}""",
        workflow = meta(
            runId = WF_RUN,
            name = "review-changes",
            status = "running",
            phases = PHASES,
            members = LIVE_MEMBERS,
            logs = LIVE_LOGS,
        ),
    )

    /** The model's own `workflow` tool call, carrying the script it wrote. */
    private val workflowScriptCall = ToolCallItem(
        id = "call-1",
        title = """workflow {"save_as":"review-changes","script":"export const meta = ..."}""",
        kind = "think",
        status = AcpToolStatus.IN_PROGRESS,
        argsJSON = """{"save_as":"review-changes","script":$SCRIPT_SOURCE_JSON}""",
    )

    private fun runArgs(phase: String, index: Int, extra: String = "") =
        ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"$phase","index":$index$extra"""

    private val workflowMembers = listOf(
        member(
            "call-2", "review#1", "correctness — logic errors and unhandled states",
            AcpToolStatus.COMPLETED,
            runArgs("Review", 1),
            report(
                "review#1",
                "Two real defects: the resume path drops queued chunks, and the pairing " +
                    "timeout is never cleared.",
            ),
        ),
        member(
            "call-3", "review#2", "performance — allocations on the hot path",
            AcpToolStatus.COMPLETED,
            runArgs("Review", 2),
            report(
                "review#2",
                "One finding: the transcript fold was recomputed per frame before the memo landed.",
            ),
        ),
        member(
            "call-4", "review#3", "tests — coverage of the changed branches",
            AcpToolStatus.FAILED,
            runArgs("Review", 3),
            """{"error":"provider returned 429 after 3 attempts"}""",
        ),
        member(
            "call-5", "verify#1", "Refute: the resume path drops queued chunks",
            AcpToolStatus.IN_PROGRESS,
            runArgs("Verify", 4),
        ),
        member(
            "call-6", "verify#2", "Refute: the pairing timeout is never cleared",
            AcpToolStatus.IN_PROGRESS,
            runArgs("Verify", 5),
        ),
        member(
            "call-7", "verify#3", "Refute: the transcript fold recomputes per frame",
            AcpToolStatus.COMPLETED,
            runArgs("Verify", 6, ""","cached":true"""),
            report("verify#3", "Refuted — the memo was added in the same commit."),
        ),
    )

    private val workflowChildren = listOf(
        child(
            "call-8", "review#1",
            """read {"path":"app/src/main/java/to/eyed/spettro/mobile/core/remote/RemoteClient.kt"}""",
            "read", AcpToolStatus.COMPLETED,
        ),
        child(
            "call-9", "review#1",
            """grep {"pattern":"queuedChunks","path":"app/src"}""",
            "search", AcpToolStatus.COMPLETED,
        ),
        child(
            "call-10", "verify#1",
            """bash {"command":"./gradlew :app:testDebugUnitTest --tests '*Resume*'"}""",
            "execute", AcpToolStatus.IN_PROGRESS,
        ),
        child(
            "call-11", "verify#2",
            """read {"path":"app/src/main/java/to/eyed/spettro/mobile/core/remote/RemotePairing.kt"}""",
            "read", AcpToolStatus.COMPLETED,
        ),
    )

    private val workflowPrompt = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.User,
            text = "ultracode +500k — review the changes on this branch",
        ),
    )

    /**
     * A live workflow, interleaved the way the wire delivers it: the script
     * call, the card, then members and their nested tools in arrival order
     * rather than grouped by owner.
     */
    val liveWorkflowTranscript: List<TranscriptItem> = listOf(
        workflowPrompt,
        TranscriptItem.Tool(workflowScriptCall),
        TranscriptItem.Tool(workflowCard),
        TranscriptItem.Tool(workflowMembers[0]),
        TranscriptItem.Tool(workflowChildren[0]),
        TranscriptItem.Tool(workflowMembers[1]),
        TranscriptItem.Tool(workflowChildren[1]),
        TranscriptItem.Tool(workflowMembers[2]),
        TranscriptItem.Tool(workflowMembers[3]),
        TranscriptItem.Tool(workflowChildren[2]),
        TranscriptItem.Tool(workflowMembers[4]),
        TranscriptItem.Tool(workflowChildren[3]),
        TranscriptItem.Tool(workflowMembers[5]),
    )

    /** The members once every one of them has settled. */
    private val settledMembers: List<ToolCallItem> = workflowMembers.map { call ->
        if (call.status != AcpToolStatus.IN_PROGRESS) return@map call
        call.copy(
            status = AcpToolStatus.COMPLETED,
            output = report(
                call.subAgentCall?.agent ?: "agent",
                "Confirmed — reproduced on a clean checkout.",
            ),
        )
    }

    /** The same run, finished: the card closes completed, its summary on top. */
    val settledWorkflowTranscript: List<TranscriptItem> = buildList {
        add(workflowPrompt)
        add(TranscriptItem.Tool(workflowScriptCall.copy(status = AcpToolStatus.COMPLETED)))
        add(
            TranscriptItem.Tool(
                workflowCard.copy(
                    status = AcpToolStatus.COMPLETED,
                    workflow = meta(
                        runId = WF_RUN,
                        name = "review-changes",
                        status = "success",
                        phases = PHASES,
                        members = LIVE_MEMBERS.map {
                            if (it.status == "running") it.copy(status = "done") else it
                        },
                        logs = LIVE_LOGS + "1 finding refuted",
                        summary = "6 agents · 1 failed · 1 replayed",
                    ),
                ),
            ),
        )
        settledMembers.forEachIndexed { index, call ->
            add(TranscriptItem.Tool(call))
            if (index == 0) add(TranscriptItem.Tool(workflowChildren[0]))
        }
        add(
            TranscriptItem.Message(
                ChatMessage(
                    role = ChatMessage.Role.Assistant,
                    text = "Three findings survived verification. The resume path and the " +
                        "pairing timeout are real; the allocation one was refuted.",
                ),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // A run waiting at a checkpoint, and the same run continued
    // -----------------------------------------------------------------------

    private val PAUSE = "cp-1" to "Two findings survived. Fix both, or only the resume path?"

    private val pausedMembers = LIVE_MEMBERS.map {
        if (it.status == "running") it.copy(status = "done") else it
    }

    /** The card as the turn that paused it leaves it: open, in progress. */
    private val pausedCard = workflowCard.copy(
        workflow = meta(
            runId = WF_RUN,
            name = "review-changes",
            status = "paused",
            phases = PHASES,
            members = pausedMembers,
            logs = LIVE_LOGS + "1 finding refuted",
            pausedAt = PAUSE,
        ),
    )

    val pausedWorkflowTranscript: List<TranscriptItem> = buildList {
        add(workflowPrompt)
        add(TranscriptItem.Tool(workflowScriptCall.copy(status = AcpToolStatus.COMPLETED)))
        add(TranscriptItem.Tool(pausedCard))
        settledMembers.forEach { add(TranscriptItem.Tool(it)) }
        add(
            TranscriptItem.Message(
                ChatMessage(
                    role = ChatMessage.Role.Assistant,
                    text = "The review paused before fixing anything: two findings survived " +
                        "verification. Should I fix both, or only the resume path?",
                ),
            ),
        )
    }

    /**
     * Turn two answers the checkpoint. The CLI closes turn one's card
     * "continued in a later turn" with its state as it paused, and opens
     * `workflow-<run>-2` with everything recorded so far plus the new phase.
     */
    val continuedWorkflowTranscript: List<TranscriptItem> = buildList {
        add(workflowPrompt)
        add(TranscriptItem.Tool(workflowScriptCall.copy(status = AcpToolStatus.COMPLETED)))
        add(
            TranscriptItem.Tool(
                pausedCard.copy(
                    status = AcpToolStatus.COMPLETED,
                    workflow = meta(
                        runId = WF_RUN,
                        name = "review-changes",
                        status = "paused",
                        phases = PHASES,
                        members = pausedMembers,
                        logs = LIVE_LOGS + "1 finding refuted",
                        pausedAt = PAUSE,
                        continuedIn = "$CARD_ID-2",
                    ),
                ),
            ),
        )
        settledMembers.forEach { add(TranscriptItem.Tool(it)) }
        add(
            TranscriptItem.Message(
                ChatMessage(role = ChatMessage.Role.User, text = "Fix both."),
            ),
        )
        add(
            TranscriptItem.Tool(
                ToolCallItem(
                    id = "call-1~2",
                    title = """workflow {"continue_run_id":"$WF_RUN","reply":"fix both"}""",
                    kind = "think",
                    status = AcpToolStatus.COMPLETED,
                    argsJSON = """{"continue_run_id":"$WF_RUN","reply":"fix both"}""",
                    output = """<workflow_result name="review-changes" run_id="$WF_RUN">""" +
                        "\n</workflow_result>",
                ),
            ),
        )
        val fix = Phase("Fix", "one agent per surviving finding", dynamic = true)
        add(
            TranscriptItem.Tool(
                ToolCallItem(
                    id = "$CARD_ID-2",
                    title = cardTitle("review-changes"),
                    kind = "think",
                    status = AcpToolStatus.IN_PROGRESS,
                    workflow = meta(
                        runId = WF_RUN,
                        name = "review-changes",
                        status = "running",
                        attach = 2,
                        continuedFrom = CARD_ID,
                        phases = PHASES + fix,
                        members = pausedMembers + listOf(
                            Listed("code#1", "Fix: the resume path drops queued chunks", "Fix", "running"),
                            Listed("code#2", "Fix: the pairing timeout is never cleared", "Fix", "pending"),
                        ),
                        logs = LIVE_LOGS + listOf("1 finding refuted", "fixing 2 findings"),
                    ),
                ),
            ),
        )
        add(
            TranscriptItem.Tool(
                member(
                    "call-2~2", "code#1", "Fix: the resume path drops queued chunks",
                    AcpToolStatus.IN_PROGRESS,
                    ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Fix","index":7""",
                ),
            ),
        )
        add(
            TranscriptItem.Tool(
                child(
                    "call-3~2", "code#1",
                    """edit {"path":"app/src/main/java/to/eyed/spettro/mobile/core/remote/RemoteClient.kt"}""",
                    "edit", AcpToolStatus.IN_PROGRESS,
                ),
            ),
        )
    }

    /** A run the orchestrator stopped: the card closes completed, saying why. */
    val stoppedWorkflowTranscript: List<TranscriptItem> = listOf(
        workflowPrompt,
        TranscriptItem.Tool(workflowScriptCall.copy(status = AcpToolStatus.COMPLETED)),
        TranscriptItem.Tool(
            workflowCard.copy(
                status = AcpToolStatus.COMPLETED,
                workflow = meta(
                    runId = WF_RUN,
                    name = "review-changes",
                    status = "stopped",
                    phases = PHASES,
                    members = LIVE_MEMBERS.map {
                        if (it.status == "running") it.copy(status = "done") else it
                    },
                    logs = LIVE_LOGS,
                    dropped = 12,
                    stoppedReason = "paused for over 30m with no continue",
                ),
            ),
        ),
    ) + settledMembers.map { TranscriptItem.Tool(it) }

    // -----------------------------------------------------------------------
    // Workflows that never started
    // -----------------------------------------------------------------------

    val failedScriptTranscript: List<TranscriptItem> = listOf(
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.User,
                text = "use a workflow to port the settings screen",
            ),
        ),
        // A run that died on the way in: the card closes failed, the parse
        // error where a finished run's counts would go.
        TranscriptItem.Tool(
            ToolCallItem(
                id = "workflow-wf_20260824_b7e211",
                title = "workflow port-settings",
                kind = "think",
                status = AcpToolStatus.FAILED,
                workflow = meta(
                    runId = "wf_20260824_b7e211",
                    name = "port-settings",
                    status = "failed",
                    description = "Port the settings screen",
                    size = "",
                    sizeAgents = 0,
                    budgetTokens = 0,
                    phases = listOf(Phase("Read"), Phase("Port")),
                    members = emptyList(),
                    summary = "error: workflow \"port-settings\": script does not parse: " +
                        "SyntaxError: (anonymous): Line 4:12 Unexpected identifier",
                ),
            ),
        ),
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.Assistant,
                text = "The script didn't parse. I'll run the saved version instead.",
            ),
        ),
        // And a script call that started no run at all.
        TranscriptItem.Tool(
            ToolCallItem(
                id = "call-12",
                title = """workflow {"save_as":"port-settings","script":"export const meta = ..."}""",
                kind = "think",
                status = AcpToolStatus.FAILED,
                output = "error: workflow: no saved workflow named \"port-settings\" " +
                    "(looked in .spettro/workflows)",
                argsJSON = """{"name":"port-settings","script":$BROKEN_SCRIPT_JSON}""",
            ),
        ),
    )

    // -----------------------------------------------------------------------
    // Everything at once
    // -----------------------------------------------------------------------

    /** The scene that proves the cards coexist with ordinary conversation. */
    val mixedTranscript: List<TranscriptItem> = buildList {
        add(ChatPreviewData.userMessage)
        add(ChatPreviewData.reasoning)
        add(TranscriptItem.Tool(ChatPreviewData.readTool))
        addAll(settledWorkflowTranscript.drop(1))
        add(TranscriptItem.Tool(ChatPreviewData.executeTool))
        addAll(liveWorkflowTranscript.drop(1).map(::secondRun))
    }

    /** The live workflow under a different run id, so two runs can share a
     *  transcript without their ids colliding. */
    private fun secondRun(item: TranscriptItem): TranscriptItem {
        val tool = (item as? TranscriptItem.Tool)?.tool ?: return item
        val renamed = tool.copy(
            id = tool.id.replace(WF_RUN, "wf_20260824_c4d590").let { if (it == tool.id) "${it}~2" else it },
            argsJSON = tool.argsJSON?.replace(WF_RUN, "wf_20260824_c4d590"),
            workflow = tool.workflow?.let { meta ->
                JsonObject(meta + ("runId" to JsonPrimitive("wf_20260824_c4d590")))
            },
        )
        return TranscriptItem.Tool(renamed)
    }

    /**
     * Messages that arm workflows, so the highlight can be eyeballed against
     * prose that must stay quiet. The last two are the near-misses the matcher
     * is most likely to get wrong.
     */
    val activationTranscript: List<TranscriptItem> = listOf(
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.User,
                text = "ultracode — review the changes on this branch",
            ),
        ),
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.User,
                text = "fan this out across sub-agents and orchestrate this with subagents",
            ),
        ),
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.User,
                text = "our deploy workflow is broken — check .github/workflows",
            ),
        ),
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.Assistant,
                text = "The first two messages arm orchestration; the third does not.",
            ),
        ),
    )

    // -----------------------------------------------------------------------
    // Folded views the previews and the screenshot scenes render
    // -----------------------------------------------------------------------

    val liveWorkflowRows: List<TranscriptRow> get() = groupTranscript(liveWorkflowTranscript)
    val settledWorkflowRows: List<TranscriptRow> get() = groupTranscript(settledWorkflowTranscript)
    val failedScriptRows: List<TranscriptRow> get() = groupTranscript(failedScriptTranscript)
    val mixedRows: List<TranscriptRow> get() = groupTranscript(mixedTranscript)

    /** The first workflow run in a folded list, for card-level previews. */
    fun workflowRun(rows: List<TranscriptRow>): WorkflowRun =
        rows.filterIsInstance<TranscriptRow.Run>().map { it.run }.first()

    /** The live workflow, folded — the shape most previews want. */
    val liveWorkflow: WorkflowRun get() = workflowRun(liveWorkflowRows)

    /** The workflow waiting at its checkpoint, folded. */
    val pausedWorkflow: WorkflowRun get() = workflowRun(groupTranscript(pausedWorkflowTranscript))
}
