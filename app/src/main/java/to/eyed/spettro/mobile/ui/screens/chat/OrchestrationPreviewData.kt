package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.model.ChatMessage
import to.eyed.spettro.mobile.model.OrchRun
import to.eyed.spettro.mobile.model.ToolCallItem
import to.eyed.spettro.mobile.model.TranscriptItem
import to.eyed.spettro.mobile.model.TranscriptRow
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
 * The wire shapes are copied from real CLI output — `workflow <name>` carrying
 * `{run_id, workflow, phases}`, members carrying `{agent, workflow, run_id,
 * phase, index}`, Ultra members carrying `{agent, swarm: true}`, and nested
 * calls titled `[instance] tool {...}`.
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
        title = """agent {"agent":"$instance"}""",
        kind = "think",
        status = status,
        output = output,
        argsJSON = """{"agent":"$instance","task":"$task"$extraArgs}""",
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

    private const val WF_RUN = "wf_2026_08_24_a91"

    private const val WF_DESCRIPTION =
        "Review the changed files across dimensions, then adversarially verify every finding"

    /**
     * The tree `acpWorkflow.render()` writes into a finished run's output. The
     * settled fixture carries it because it is the *only* place a finished
     * run's phase plan, description and log survive — parsing it back is what
     * `parseRenderedWorkflow` exists for.
     */
    private val RENDERED_TREE = """
        6 agents · 1 failed · 1 replayed

        $WF_DESCRIPTION

        ▸ Review — 2/3 done, 1 failed
            ✓ review#1  correctness — logic errors and unhandled states
            ✓ review#2  performance — allocations on the hot path
            ✗ review#3  tests — coverage of the changed branches
        ▸ Verify — 3/3 done
            ✓ verify#1  Refute: the resume path drops queued chunks
            ✓ verify#2  Refute: the pairing timeout is never cleared
            ✓ verify#3  Refute: groupTranscript recomputes per frame
        ○ Synthesise — pending

        log:
          3/3 dimensions reviewed
          2 findings queued for verification
          1 finding refuted
    """.trimIndent()

    // -----------------------------------------------------------------------
    // A workflow mid-flight: three phases, one member already failed
    // -----------------------------------------------------------------------

    private val workflowLifecycle = ToolCallItem(
        id = "wf-lifecycle",
        title = "workflow review-changes",
        kind = "think",
        status = AcpToolStatus.IN_PROGRESS,
        argsJSON = """{"run_id":"$WF_RUN","workflow":"review-changes",""" +
            """"description":"$WF_DESCRIPTION","origin":"inline",""" +
            """"phases":[{"title":"Review","detail":"one agent per dimension"},""" +
            """{"title":"Verify","detail":"three skeptics per finding"},""" +
            """{"title":"Synthesise","detail":"rank what survived"}]}""",
    )

    /** The model's own `workflow` tool call, carrying the script it wrote. */
    private val workflowScriptCall = ToolCallItem(
        id = "wf-script",
        title = """workflow {"save_as":"review-changes","script":"export const meta = ..."}""",
        kind = "think",
        status = AcpToolStatus.IN_PROGRESS,
        argsJSON = """{"save_as":"review-changes","script":$SCRIPT_SOURCE_JSON}""",
    )

    private val workflowMembers = listOf(
        member(
            "wf-m1", "review#1", "correctness — logic errors and unhandled states",
            AcpToolStatus.COMPLETED,
            ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Review","index":1""",
            report(
                "review#1",
                "Two real defects: the resume path drops queued chunks, and the pairing " +
                    "timeout is never cleared.",
            ),
        ),
        member(
            "wf-m2", "review#2", "performance — allocations on the hot path",
            AcpToolStatus.COMPLETED,
            ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Review","index":2""",
            report(
                "review#2",
                "One finding: the transcript fold was recomputed per frame before the memo landed.",
            ),
        ),
        member(
            "wf-m3", "review#3", "tests — coverage of the changed branches",
            AcpToolStatus.FAILED,
            ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Review","index":3""",
            """{"error":"provider returned 429 after 3 attempts"}""",
        ),
        member(
            "wf-m4", "verify#1", "Refute: the resume path drops queued chunks",
            AcpToolStatus.IN_PROGRESS,
            ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Verify","index":4""",
        ),
        member(
            "wf-m5", "verify#2", "Refute: the pairing timeout is never cleared",
            AcpToolStatus.IN_PROGRESS,
            ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Verify","index":5""",
        ),
        member(
            "wf-m6", "verify#3", "Refute: the transcript fold recomputes per frame",
            AcpToolStatus.COMPLETED,
            ""","workflow":"review-changes","run_id":"$WF_RUN","phase":"Verify","index":6,""" +
                """"cached":true""",
            report("verify#3", "Refuted — the memo was added in the same commit."),
        ),
    )

    private val workflowChildren = listOf(
        child(
            "wf-c1", "review#1",
            """read {"path":"app/src/main/java/to/eyed/spettro/mobile/core/remote/RemoteClient.kt"}""",
            "read", AcpToolStatus.COMPLETED,
        ),
        child(
            "wf-c2", "review#1",
            """grep {"pattern":"queuedChunks","path":"app/src"}""",
            "search", AcpToolStatus.COMPLETED,
        ),
        child(
            "wf-c3", "verify#1",
            """bash {"command":"./gradlew :app:testDebugUnitTest --tests '*Resume*'"}""",
            "execute", AcpToolStatus.IN_PROGRESS,
        ),
        child(
            "wf-c4", "verify#2",
            """read {"path":"app/src/main/java/to/eyed/spettro/mobile/core/remote/RemotePairing.kt"}""",
            "read", AcpToolStatus.COMPLETED,
        ),
    )

    private val workflowPrompt = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.User,
            text = "ultracode — review the changes on this branch",
        ),
    )

    /**
     * A live workflow, interleaved the way the wire delivers it: the script
     * call, the lifecycle call, then members and their nested tools in arrival
     * order rather than grouped by owner.
     */
    val liveWorkflowTranscript: List<TranscriptItem> = listOf(
        workflowPrompt,
        TranscriptItem.Tool(workflowScriptCall),
        TranscriptItem.Tool(workflowLifecycle),
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

    /**
     * The same run, finished. The finish update replaces `argsJSON` with a
     * completely different payload and the phase plan is gone — everything the
     * card still shows has to be mined back out of the rendered text, which is
     * exactly what this fixture exercises.
     */
    val settledWorkflowTranscript: List<TranscriptItem> = buildList {
        add(workflowPrompt)
        add(
            TranscriptItem.Tool(
                workflowLifecycle.copy(
                    status = AcpToolStatus.COMPLETED,
                    argsJSON = """{"run_id":"$WF_RUN","workflow":"review-changes","agents":6,""" +
                        """"failed":1,"cached":1,"tokens":412000}""",
                    output = RENDERED_TREE,
                ),
            ),
        )
        workflowMembers.forEachIndexed { index, call ->
            val settled = if (call.status == AcpToolStatus.IN_PROGRESS) {
                call.copy(
                    status = AcpToolStatus.COMPLETED,
                    output = report(
                        call.subAgentCall?.agent ?: "agent",
                        "Confirmed — reproduced on a clean checkout.",
                    ),
                )
            } else {
                call
            }
            add(TranscriptItem.Tool(settled))
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
    // An Ultra swarm mid-ramp
    // -----------------------------------------------------------------------

    private val swarmItems = listOf(
        "app/src/main/java/to/eyed/spettro/mobile/ui/screens/chat/ChatScreen.kt",
        "app/src/main/java/to/eyed/spettro/mobile/ui/screens/chat/ChatComposer.kt",
        "app/src/main/java/to/eyed/spettro/mobile/ui/screens/chat/ToolCallRow.kt",
        "app/src/main/java/to/eyed/spettro/mobile/ui/screens/home/ChatListScreen.kt",
        "app/src/main/java/to/eyed/spettro/mobile/ui/screens/home/PairingScreen.kt",
        "app/src/main/java/to/eyed/spettro/mobile/ui/screens/settings/SettingsScreen.kt",
        "app/src/main/java/to/eyed/spettro/mobile/core/remote/RemoteClient.kt",
        "app/src/main/java/to/eyed/spettro/mobile/core/remote/RemotePairing.kt",
        "app/src/main/java/to/eyed/spettro/mobile/core/acp/AcpParser.kt",
        "app/src/main/java/to/eyed/spettro/mobile/model/ChatSession.kt",
        "app/src/main/java/to/eyed/spettro/mobile/model/TranscriptModels.kt",
        "app/src/main/java/to/eyed/spettro/mobile/coordinator/AppContainer.kt",
    )

    private val ultraArgs: String = buildString {
        append("""{"description":"Add KDoc to every public composable and check the imports",""")
        append(""""subagent_type":"code","isolation":"worktree","items":[""")
        append(swarmItems.joinToString(",") { "\"$it\"" })
        append("]}")
    }

    private val ultraCall = ToolCallItem(
        id = "ultra-1",
        title = "ultra",
        kind = "think",
        status = AcpToolStatus.IN_PROGRESS,
        argsJSON = ultraArgs,
    )

    private fun swarmMember(
        index: Int,
        status: AcpToolStatus,
        output: String = "",
    ): ToolCallItem {
        val instance = "code#${index + 1}"
        return ToolCallItem(
            id = "ultra-m$index",
            title = """agent {"agent":"$instance"}""",
            kind = "think",
            status = status,
            output = output,
            argsJSON = """{"agent":"$instance","task":"${swarmItems[index]}",""" +
                """"swarm":true,"index":${index + 1}}""",
        )
    }

    private val swarmMembers = listOf(
        swarmMember(
            0, AcpToolStatus.COMPLETED,
            report("code#1", "Documented 6 composables; no import changes needed."),
        ),
        swarmMember(
            1, AcpToolStatus.COMPLETED,
            report("code#2", "Documented 4 composables and dropped two unused imports."),
        ),
        swarmMember(
            2, AcpToolStatus.FAILED,
            "the file was modified by another member; rebase before retrying",
        ),
        swarmMember(3, AcpToolStatus.COMPLETED, report("code#4", "Documented 9 composables.")),
        swarmMember(4, AcpToolStatus.IN_PROGRESS),
        swarmMember(5, AcpToolStatus.IN_PROGRESS),
        swarmMember(6, AcpToolStatus.IN_PROGRESS),
    )

    private val swarmChildren = listOf(
        child(
            "us-c1", "code#5",
            """read {"path":"app/src/main/java/to/eyed/spettro/mobile/ui/screens/home/PairingScreen.kt"}""",
            "read", AcpToolStatus.COMPLETED,
        ),
        child(
            "us-c2", "code#5",
            """edit {"path":"app/src/main/java/to/eyed/spettro/mobile/ui/screens/home/PairingScreen.kt"}""",
            "edit", AcpToolStatus.IN_PROGRESS,
        ),
        child(
            "us-c3", "code#6",
            """bash {"command":"./gradlew :app:compileDebugKotlin"}""",
            "execute", AcpToolStatus.IN_PROGRESS,
        ),
    )

    private val swarmPrompt = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.User,
            text = "fan this out across sub-agents: document every public composable",
        ),
    )

    val liveSwarmTranscript: List<TranscriptItem> = buildList {
        add(swarmPrompt)
        add(TranscriptItem.Tool(ultraCall))
        swarmMembers.forEach { add(TranscriptItem.Tool(it)) }
        swarmChildren.forEach { add(TranscriptItem.Tool(it)) }
    }

    /** A settled swarm: every item dispatched, two of them broken. */
    val settledSwarmTranscript: List<TranscriptItem> = buildList {
        add(swarmPrompt)
        add(TranscriptItem.Tool(ultraCall.copy(status = AcpToolStatus.COMPLETED)))
        swarmItems.indices.forEach { index ->
            val status = if (index == 2 || index == 9) {
                AcpToolStatus.FAILED
            } else {
                AcpToolStatus.COMPLETED
            }
            val output = if (status == AcpToolStatus.FAILED) {
                "the file was modified by another member; rebase before retrying"
            } else {
                report("code#${index + 1}", "Documented the public surface of this file.")
            }
            add(TranscriptItem.Tool(swarmMember(index, status, output)))
        }
        add(
            TranscriptItem.Message(
                ChatMessage(
                    role = ChatMessage.Role.Assistant,
                    text = "Ten files documented. Two members hit the same rebase conflict — " +
                        "I'll redo those two serially.",
                ),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // A workflow that never started
    // -----------------------------------------------------------------------

    val failedScriptTranscript: List<TranscriptItem> = listOf(
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.User,
                text = "use a workflow to port the settings screen",
            ),
        ),
        TranscriptItem.Tool(
            ToolCallItem(
                id = "wf-broken",
                title = """workflow {"save_as":"port-settings","script":"export const meta = ..."}""",
                kind = "think",
                status = AcpToolStatus.FAILED,
                output = "script error: meta must be a pure literal — `phases` referenced the " +
                    "variable `PHASES` (line 4)",
                argsJSON = """{"save_as":"port-settings","script":$BROKEN_SCRIPT_JSON}""",
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
        addAll(liveSwarmTranscript.drop(1))
        add(TranscriptItem.Tool(ChatPreviewData.executeTool))
        addAll(settledWorkflowTranscript.drop(1))
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
    val liveSwarmRows: List<TranscriptRow> get() = groupTranscript(liveSwarmTranscript)
    val settledSwarmRows: List<TranscriptRow> get() = groupTranscript(settledSwarmTranscript)
    val failedScriptRows: List<TranscriptRow> get() = groupTranscript(failedScriptTranscript)
    val mixedRows: List<TranscriptRow> get() = groupTranscript(mixedTranscript)

    /** The first workflow run in a folded list, for card-level previews. */
    fun workflowRun(rows: List<TranscriptRow>): OrchRun.Workflow =
        rows.filterIsInstance<TranscriptRow.Run>()
            .map { it.run }
            .filterIsInstance<OrchRun.Workflow>()
            .first()

    /** The first swarm run in a folded list, for card-level previews. */
    fun swarmRun(rows: List<TranscriptRow>): OrchRun.Swarm =
        rows.filterIsInstance<TranscriptRow.Run>()
            .map { it.run }
            .filterIsInstance<OrchRun.Swarm>()
            .first()

    /** The live swarm, folded — the shape most previews want. */
    val liveSwarm: OrchRun.Swarm get() = swarmRun(liveSwarmRows)

    /** The live workflow, folded. */
    val liveWorkflow: OrchRun.Workflow get() = workflowRun(liveWorkflowRows)
}
