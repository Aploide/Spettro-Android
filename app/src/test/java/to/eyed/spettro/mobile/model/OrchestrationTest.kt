package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/**
 * [groupTranscript] is the most intricate pure function in the app and the one
 * with the least margin for error: it decides which rows the user sees at all.
 * Everything it does is reconstruction from evidence the CLI leaves lying
 * around — a run_id here, a bracket prefix there, a text tree when the
 * structured data has been overwritten — and each of those clues has a way of
 * being absent that these tests pin down.
 *
 * Screenshots cannot catch any of this. A member attached to the wrong run
 * still renders beautifully.
 *
 * Port of the desktop app's `tests/orchestration.test.ts`, case for case, so
 * the two front-ends cannot drift apart in what they fold.
 */
class OrchestrationTest {

    @Before
    fun setUp() = Wire.reset()

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun kinds(rows: List<TranscriptRow>): List<String> = rows.map { row ->
        when (row) {
            is TranscriptRow.Item -> "item"
            is TranscriptRow.Agent -> "agent"
            is TranscriptRow.Run -> "run"
            is TranscriptRow.Script -> "script"
        }
    }

    private fun runsOf(rows: List<TranscriptRow>): List<OrchRun> =
        rows.filterIsInstance<TranscriptRow.Run>().map { it.run }

    private fun onlyWorkflow(rows: List<TranscriptRow>): OrchRun.Workflow {
        val found = runsOf(rows).filterIsInstance<OrchRun.Workflow>()
        assertEquals("expected exactly one workflow", 1, found.size)
        return found[0]
    }

    private fun onlySwarm(rows: List<TranscriptRow>): OrchRun.Swarm {
        val found = runsOf(rows).filterIsInstance<OrchRun.Swarm>()
        assertEquals("expected exactly one swarm", 1, found.size)
        return found[0]
    }

    // -----------------------------------------------------------------------
    // Folding a workflow
    // -----------------------------------------------------------------------

    @Test
    fun absorbsItsMembersAndTheirToolCallsLeavingOneRow() {
        val rows = groupTranscript(
            listOf(
                Wire.message(ChatMessage.Role.User, "review this"),
                Wire.workflowStart("wf_1", "review", phases = listOf("Review" to "")),
                Wire.member("review#1", "bugs", "wf_1", "review", phase = "Review"),
                Wire.childCall("review#1", "bash", """{"command":"rg TODO"}"""),
                Wire.childCall("review#1", "read", """{"file_path":"a.ts"}"""),
            ),
        )
        // The user message, then the run. The member and both of its calls are
        // gone from the flat list — that is the whole point of the fold.
        assertEquals(listOf("item", "run"), kinds(rows))
        val run = onlyWorkflow(rows)
        assertEquals(1, run.phases[0].members.size)
        assertEquals(2, run.phases[0].members[0].children.size)
    }

    @Test
    fun keepsDeclaredPhasesNobodyHasReached() {
        // A workflow's structure is decided before it runs, so an unreached
        // phase is information, not absence. Dropping it would make the card
        // describe only the past.
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowStart(
                        "wf_1", "review",
                        phases = listOf("Review" to "", "Verify" to "", "Synthesize" to ""),
                    ),
                    Wire.member("review#1", "bugs", "wf_1", "review", phase = "Review"),
                ),
            ),
        )
        assertEquals(listOf("Review", "Verify", "Synthesize"), run.phases.map { it.title })
        assertEquals(emptyList<MemberCall>(), run.phases[1].members)
    }

    @Test
    fun appendsAnUndeclaredPhaseAfterTheDeclaredOnes() {
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowStart("wf_1", "review", phases = listOf("Review" to "")),
                    Wire.member("x#1", "t", "wf_1", "review", phase = "Extra"),
                ),
            ),
        )
        assertEquals(listOf("Review", "Extra"), run.phases.map { it.title })
    }

    @Test
    fun phaselessMembersLandInATrailingBucketOnlyWhenThereAreAny() {
        val withNone = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowStart("wf_1", "r", phases = listOf("A" to "")),
                    Wire.member("a#1", "t", "wf_1", "r", phase = "A"),
                ),
            ),
        )
        assertEquals(listOf("A"), withNone.phases.map { it.title })

        Wire.reset()
        val withSome = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowStart("wf_1", "r", phases = listOf("A" to "")),
                    Wire.member("a#1", "t", "wf_1", "r", phase = "A"),
                    Wire.member("b#2", "t", "wf_1", "r"),
                ),
            ),
        )
        // The unnamed bucket sorts last: named plan first, strays after.
        assertEquals(listOf("A", ""), withSome.phases.map { it.title })
        assertEquals(1, withSome.phases[1].members.size)
    }

    @Test
    fun routesMembersToTheRunWhoseRunIdTheyNameNotTheNearestOne() {
        // Two runs in one turn, with their members interleaved. Position would
        // get this wrong; the run_id is the only thing that gets it right.
        val rows = groupTranscript(
            listOf(
                Wire.workflowStart("wf_1", "first", phases = listOf("A" to "")),
                Wire.workflowStart("wf_2", "second", phases = listOf("B" to "")),
                Wire.member("a#1", "for first", "wf_1", "first", phase = "A"),
                Wire.member("b#1", "for second", "wf_2", "second", phase = "B"),
            ),
        )
        val found = runsOf(rows).filterIsInstance<OrchRun.Workflow>()
        assertEquals("first", found[0].name)
        assertEquals(listOf("for first"), found[0].phases[0].members.map { it.task })
        assertEquals(listOf("for second"), found[1].phases[0].members.map { it.task })
    }

    // -----------------------------------------------------------------------
    // A finished run, whose plan the CLI has already overwritten
    // -----------------------------------------------------------------------

    private val renderedFinish = Wire.renderedTree(
        summary = "4 agents · 1 failed · 1 replayed",
        description = "Review then verify",
        phases = listOf(
            Wire.RenderedPhase(
                "Review",
                listOf(
                    Wire.RenderedMember("✓", "review#1", "bugs"),
                    Wire.RenderedMember("✗", "review#2", "perf"),
                ),
            ),
            Wire.RenderedPhase("Synthesize"),
        ),
        logs = listOf("3/10 findings collected", "perf still running"),
    )

    private fun finishedItems(): List<TranscriptItem> = listOf(
        Wire.workflowFinished(
            id = "wf-1", runId = "wf_1", name = "review",
            agents = 4, failed = 1, cached = 1, rendered = renderedFinish,
        ),
        Wire.member(
            "review#1", "bugs", "wf_1", "review",
            phase = "Review", status = AcpToolStatus.COMPLETED,
        ),
        Wire.member(
            "review#2", "perf", "wf_1", "review",
            phase = "Review", status = AcpToolStatus.FAILED,
        ),
    )

    @Test
    fun recoversThePhasePlanFromTheRenderedTree() {
        val run = onlyWorkflow(groupTranscript(finishedItems()))
        // "Synthesize" has no members and appears in no member's args — the
        // text tree is the only place it still exists.
        assertTrue(run.phases.map { it.title }.contains("Synthesize"))
        assertTrue(run.phases.map { it.title }.contains("Review"))
    }

    @Test
    fun recoversTheDescriptionAndTheLogLines() {
        val run = onlyWorkflow(groupTranscript(finishedItems()))
        assertEquals("Review then verify", run.description)
        assertEquals(listOf("3/10 findings collected", "perf still running"), run.logs)
    }

    @Test
    fun reportsTheRunAsFinishedAndCountsItsFailure() {
        val run = onlyWorkflow(groupTranscript(finishedItems()))
        assertEquals(OrchStatus.DONE, run.status)
        assertEquals(1, run.counts.failed)
        assertTrue(run.summary.contains("4 agents"))
    }

    @Test
    fun prefersStructuredArgsOverTheTextWhenBothArePresent() {
        // A running workflow still has its real phase list; the text must not
        // be allowed to override it with whatever the tree happened to show.
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowStart(
                        "wf_1", "review",
                        description = "from args",
                        phases = listOf("Declared" to "the real one"),
                        rendered = Wire.renderedTree(
                            description = "from text",
                            phases = listOf(Wire.RenderedPhase("FromText")),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("from args", run.description)
        assertEquals("Declared", run.phases[0].title)
        assertEquals("the real one", run.phases[0].detail)
    }

    @Test
    fun survivesADescriptionThatItselfLooksLikeTreeSyntax() {
        // The recovery is line-oriented, so a description containing a phase
        // glyph must not be mistaken for the tree.
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowFinished(
                        id = "wf-1", runId = "wf_1", name = "r", agents = 0,
                        rendered = Wire.renderedTree(
                            summary = "0 agents · 0 failed · 0 replayed",
                            description = "▸ not a phase, just prose",
                            phases = listOf(Wire.RenderedPhase("Real")),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(
            "prose must not become a phase",
            !run.phases.map { it.title }.contains("not a phase, just prose"),
        )
    }

    // -----------------------------------------------------------------------
    // Attributing a sub-agent's own tool calls
    // -----------------------------------------------------------------------

    @Test
    fun aLongerInstanceNameDoesNotStealAShorterOnesCalls() {
        // "code#1" is a prefix of "code#12" as a string. Matching loosely would
        // hand #12's work to #1, and the two members would swap identities in
        // the card without anything looking broken.
        val rows = groupTranscript(
            listOf(
                Wire.ultra(listOf("a", "b")),
                Wire.swarmMember("code#1", "a"),
                Wire.swarmMember("code#12", "b"),
                Wire.childCall("code#12", "bash", """{"command":"belongs to twelve"}"""),
            ),
        )
        val swarm = onlySwarm(rows)
        assertEquals(0, swarm.members.first { it.instance == "code#1" }.children.size)
        assertEquals(1, swarm.members.first { it.instance == "code#12" }.children.size)
    }

    @Test
    fun leavesAnUnattributableCallAsAFlatRow() {
        // The CLI only brackets instances containing '#', so a plain
        // delegation's own calls carry no attribution at all. Guessing would be
        // worse than leaving them where they are.
        val rows = groupTranscript(
            listOf(
                Wire.delegation("explore", "map it", summary = "done"),
                Wire.childCall("explore", "ls", """{"path":"/x"}"""),
            ),
        )
        assertEquals(listOf("agent", "item"), kinds(rows))
    }

    @Test
    fun doesNotMakeAMemberAChildOfItself() {
        // A member's own `agent` call is titled `[review#1] agent {…}` when the
        // instance has a '#', so a naive prefix rule swallows the member
        // entirely.
        val rows = groupTranscript(
            listOf(
                Wire.workflowStart("wf_1", "r", phases = listOf("A" to "")),
                Wire.member("review#1", "t", "wf_1", "r", phase = "A"),
            ),
        )
        val run = onlyWorkflow(rows)
        assertEquals(1, run.phases[0].members.size)
        assertEquals(0, run.phases[0].members[0].children.size)
    }

    // -----------------------------------------------------------------------
    // Folding an Ultra swarm
    // -----------------------------------------------------------------------

    @Test
    fun attachesSwarmMembersByPositionSinceTheyCarryNoRunId() {
        val rows = groupTranscript(
            listOf(
                Wire.ultra(listOf("a", "b"), subagentType = "code"),
                Wire.swarmMember("code#1", "a"),
                Wire.swarmMember("code#2", "b"),
            ),
        )
        assertEquals(listOf("run"), kinds(rows))
        assertEquals(2, onlySwarm(rows).members.size)
    }

    @Test
    fun keepsTwoSwarmsInOneTurnApart() {
        val rows = groupTranscript(
            listOf(
                Wire.ultra(listOf("a")),
                Wire.swarmMember("code#1", "a"),
                Wire.ultra(listOf("b", "c")),
                Wire.swarmMember("code#2", "b"),
                Wire.swarmMember("code#3", "c"),
            ),
        )
        val swarms = runsOf(rows).filterIsInstance<OrchRun.Swarm>()
        assertEquals(listOf(1, 2), swarms.map { it.members.size })
    }

    @Test
    fun countsUnlaunchedItemsAsPendingWorkNotAbsentWork() {
        // Ultra ramps: five at once, then one every 700ms. A ten-item swarm
        // spends its first seconds mostly un-launched, and a denominator that
        // grows as members appear makes the meter run backwards.
        val swarm = onlySwarm(
            groupTranscript(
                listOf(
                    Wire.ultra(listOf("a", "b", "c", "d", "e")),
                    Wire.swarmMember("code#1", "a", status = AcpToolStatus.COMPLETED),
                    Wire.swarmMember("code#2", "b"),
                ),
            ),
        )
        assertEquals(listOf("c", "d", "e"), swarm.pending)
        assertEquals(5, swarm.counts.total)
        assertEquals(1, swarm.counts.done)
        assertEquals(1, swarm.counts.running)
    }

    @Test
    fun reportsWorktreeIsolationAndIgnoresAnyOtherValue() {
        assertEquals(
            "worktree",
            onlySwarm(
                groupTranscript(listOf(Wire.ultra(listOf("a"), isolation = "worktree"))),
            ).isolation,
        )
        Wire.reset()
        assertEquals(
            "",
            onlySwarm(
                groupTranscript(listOf(Wire.ultra(listOf("a"), isolation = "nonsense"))),
            ).isolation,
        )
    }

    // -----------------------------------------------------------------------
    // The workflow tool call that carries the script
    // -----------------------------------------------------------------------

    @Test
    fun theScriptCallFoldsIntoTheRunItStartedMatchedOnRunId() {
        val rows = groupTranscript(
            listOf(
                Wire.scriptCall(
                    script = "export const meta = {}", savedAs = "check",
                    runId = "wf_1", returned = """["a"]""",
                ),
                Wire.workflowStart("wf_1", "check", phases = listOf("Read" to "")),
            ),
        )
        // One row, not two: the script belongs inside its card, not as a page
        // of raw JSON above it.
        assertEquals(listOf("run"), kinds(rows))
        val run = onlyWorkflow(rows)
        assertEquals("check", run.script?.savedAs)
        assertTrue(run.script?.source?.contains("export const meta") == true)
        assertTrue(run.script?.returned?.contains("a") == true)
    }

    @Test
    fun aScriptThatFailedBeforeAnyRunStartedSurvivesOnItsOwn() {
        // It is the only evidence a workflow was attempted at all. Folding it
        // into nothing, or leaving it as raw JSON, both lose that.
        val rows = groupTranscript(
            listOf(
                Wire.scriptCall(
                    script = "export const meta = {}", savedAs = "missing",
                    status = AcpToolStatus.FAILED,
                    error = """error: workflow: no saved workflow named "missing"""",
                ),
            ),
        )
        assertEquals(listOf("script"), kinds(rows))
        val row = rows[0] as TranscriptRow.Script
        assertEquals(OrchStatus.FAILED, row.script.status)
        assertTrue(row.script.error.contains("no saved workflow"))
        // It carries its item too, so a renderer that never learned this row
        // kind degrades to the ordinary tool row instead of dropping it.
        assertTrue(row.item is TranscriptItem.Tool)
    }

    @Test
    fun givesEveryRowAUniqueStableId() {
        // LazyColumn keys depend on this; a collision silently reuses composable
        // state between two different runs.
        val rows = groupTranscript(
            listOf(
                Wire.message(ChatMessage.Role.User, "go"),
                Wire.workflowStart("wf_1", "a", phases = listOf("P" to "")),
                Wire.workflowStart("wf_2", "b", phases = listOf("P" to "")),
                Wire.ultra(listOf("x")),
                Wire.delegation("explore", "t"),
            ),
        )
        val ids = rows.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        // Stable: the same item folds to the same id every time, so a
        // recomposition reuses state instead of remounting the row.
        val item = Wire.message(ChatMessage.Role.User, "go")
        assertEquals(groupTranscript(listOf(item))[0].id, groupTranscript(listOf(item))[0].id)
    }

    // -----------------------------------------------------------------------
    // What a member has to show
    // -----------------------------------------------------------------------

    @Test
    fun usesTheReportSummaryWhenTheOutputIsTheReportShape() {
        val rows = groupTranscript(
            listOf(Wire.delegation("explore", "t", summary = "I found it")),
        )
        val row = rows[0] as TranscriptRow.Agent
        assertEquals("I found it", row.member.resultText)
        assertEquals(false, row.member.resultIsJSON)
    }

    @Test
    fun fallsBackToRawOutputForAStructuredResult() {
        // An `agent()` call given a `schema` returns its value, which has no
        // `summary` — trusting the report parse alone renders a finished member
        // as an empty row while the card is holding its output.
        val rows = groupTranscript(
            listOf(
                Wire.ultra(listOf("a")),
                Wire.swarmMember(
                    "code#1", "a", status = AcpToolStatus.COMPLETED,
                    output = """{"content":"beta\n","file":"b.txt"}""",
                ),
            ),
        )
        val member = onlySwarm(rows).members[0]
        assertTrue(member.resultText.contains("b.txt"))
        assertTrue(member.resultIsJSON)
    }

    @Test
    fun isEmptyOnlyWhenTheMemberReallySaidNothing() {
        val rows = groupTranscript(
            listOf(
                Wire.ultra(listOf("a")),
                Wire.swarmMember("code#1", "a", status = AcpToolStatus.COMPLETED, output = ""),
            ),
        )
        assertEquals("", onlySwarm(rows).members[0].resultText)
    }

    // -----------------------------------------------------------------------
    // Ordering and identity
    // -----------------------------------------------------------------------

    @Test
    fun leavesEverythingThatIsNotPartOfARunExactlyWhereItWas() {
        val rows = groupTranscript(
            listOf(
                Wire.message(ChatMessage.Role.User, "first"),
                Wire.tool(
                    title = """read {"file_path":"a.ts"}""",
                    kind = "read", argsJSON = """{"file_path":"a.ts"}""",
                ),
                Wire.workflowStart("wf_1", "r", phases = listOf("P" to "")),
                Wire.member("a#1", "t", "wf_1", "r", phase = "P"),
                Wire.message(ChatMessage.Role.Assistant, "last"),
            ),
        )
        assertEquals(listOf("item", "item", "run", "item"), kinds(rows))
    }

    @Test
    fun derivesAMembersSpecIdAndIndexForTintingAndOrdering() {
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowStart("wf_1", "r", phases = listOf("P" to "")),
                    Wire.member(
                        "general-purpose#7", "t", "wf_1", "r",
                        phase = "P", index = 7, cached = true,
                    ),
                ),
            ),
        )
        val member = run.phases[0].members[0]
        assertEquals("general-purpose", member.specId)
        assertEquals(7, member.index)
        assertTrue(member.cached)
    }

    // -----------------------------------------------------------------------
    // activeRuns
    // -----------------------------------------------------------------------

    @Test
    fun activeRunsReportsOnlyWhatIsStillMoving() {
        val rows = groupTranscript(
            listOf(
                Wire.workflowFinished(
                    id = "wf-1", runId = "wf_1", name = "done-one", agents = 1,
                    rendered = Wire.renderedTree(
                        summary = "1 agents · 0 failed · 0 replayed",
                        phases = emptyList(),
                    ),
                ),
                Wire.ultra(listOf("a")),
                Wire.swarmMember("code#1", "a"),
            ),
        )
        val live = activeRuns(rows)
        assertEquals(1, live.size)
        assertTrue(live[0] is OrchRun.Swarm)
    }

    @Test
    fun titlesBothKindsOfRunForThePanel() {
        val rows = groupTranscript(
            listOf(
                Wire.workflowStart("wf_1", "review-changes", phases = emptyList()),
                Wire.ultra(listOf("a"), subagentType = "code"),
            ),
        )
        val titles = runsOf(rows).map { it.displayTitle }
        assertTrue(titles[0].contains("review-changes"))
        assertTrue(titles[1].lowercase().contains("swarm"))
    }

    // -----------------------------------------------------------------------
    // Robustness
    // -----------------------------------------------------------------------

    @Test
    fun foldsAnEmptyTranscriptToNothing() {
        assertEquals(emptyList<TranscriptRow>(), groupTranscript(emptyList()))
    }

    @Test
    fun doesNotThrowOnMalformedArgs() {
        val rows = groupTranscript(
            listOf(
                Wire.tool(title = "workflow broken", kind = "think", argsJSON = "{not json"),
                Wire.tool(title = "agent x#1: t", kind = "think", argsJSON = "null"),
                Wire.tool(title = "ultra", argsJSON = "[]"),
            ),
        )
        assertNotNull(rows)
    }

    @Test
    fun keepsAMemberWhoseRunNeverArrivedVisibleAsADelegation() {
        // Losing the row entirely would hide work that really happened.
        val rows = groupTranscript(
            listOf(Wire.member("orphan#1", "t", "wf_missing", "gone")),
        )
        assertEquals(1, rows.size)
        assertTrue("an orphaned member must not degrade to a bare tool row", rows[0] !is TranscriptRow.Item)
    }

    // -----------------------------------------------------------------------
    // Beyond the desktop suite: things only this port can get wrong
    // -----------------------------------------------------------------------

    /**
     * `prettyJson` must be a no-op on anything that is not valid JSON. The
     * app-wide parser is lenient and would happily accept — and silently
     * rewrite — output the agent never produced.
     */
    @Test
    fun prettyPrintingLeavesMalformedJsonExactlyAsItArrived() {
        val malformed = "{unquoted: 'single', trailing: ,}"
        assertEquals(malformed, prettyJson(malformed))
        assertEquals("plain prose", prettyJson("plain prose"))
        assertEquals("", prettyJson(""))
        assertTrue(prettyJson("""{"a":1}""").contains("\n"))
    }

    /** The "#N" suffix is the only part of an instance name that tells two
     *  members of one run apart, so it survives truncation. */
    @Test
    fun truncationKeepsTheInstanceSuffix() {
        assertEquals("general-purpose#7", truncateInstance("general-purpose#7", 20))
        assertEquals("gener…#7", truncateInstance("general-purpose#7", 8))
        assertEquals("shorty", truncateInstance("shorty", 10))
        // No suffix to keep, and no room for one: fall back to a plain clip.
        assertEquals("gene…", truncateInstance("general-purpose", 5))
    }

    /** A failure's reason has to survive every shape the CLI reports it in. */
    @Test
    fun failureReasonReadsEveryShapeTheCliReportsIn() {
        fun reasonFor(output: String): String {
            val rows = groupTranscript(
                listOf(
                    Wire.ultra(listOf("a")),
                    Wire.swarmMember("code#1", "a", status = AcpToolStatus.FAILED, output = output),
                ),
            )
            return onlySwarm(rows).members[0].failureReason
        }

        // The report shape.
        assertEquals(
            "it broke",
            reasonFor("""{"agent":"code#1","status":"error","summary":"it broke"}"""),
        )
        Wire.reset()
        // Structured, but not a report: the error-ish field is dug out.
        assertEquals(
            "provider returned 429",
            reasonFor("""{"error":"provider returned 429"}"""),
        )
        Wire.reset()
        // Not JSON at all — the case that matters most, and never a report.
        assertEquals("429 after 3 attempts", reasonFor("429 after 3 attempts"))
        Wire.reset()
        assertEquals("", reasonFor(""))
    }

    /**
     * A member whose *reported* status is an error is failed even when the
     * tool call itself completed: the CLI closes the call normally and puts
     * the failure in the payload.
     */
    @Test
    fun aReportedErrorFailsTheMemberEvenOnACompletedCall() {
        val rows = groupTranscript(
            listOf(
                Wire.ultra(listOf("a")),
                Wire.swarmMember(
                    "code#1", "a", status = AcpToolStatus.COMPLETED,
                    output = """{"agent":"code#1","status":"error","summary":"nope"}""",
                ),
            ),
        )
        val swarm = onlySwarm(rows)
        assertEquals(OrchStatus.FAILED, swarm.members[0].status)
        assertEquals(1, swarm.counts.failed)
    }

    /**
     * The progress traces the CLI folds into its lifecycle call must never be
     * mistaken for runs of their own when one escapes.
     */
    @Test
    fun progressTracesAreNotRuns() {
        val rows = groupTranscript(
            listOf(
                Wire.tool(
                    title = "workflow review ▸ Review",
                    kind = "think",
                    argsJSON = """{"workflow":"review","kind":"phase"}""",
                ),
                Wire.tool(
                    title = "workflow review · log",
                    kind = "think",
                    argsJSON = """{"workflow":"review","kind":"log"}""",
                ),
            ),
        )
        assertEquals(listOf("item", "item"), kinds(rows))
    }

    /**
     * A run with no members at all still renders as a run. The desktop reached
     * this state with an Ultra call whose fan-out never started.
     */
    @Test
    fun aRunThatDispatchedNothingIsStillARun() {
        val rows = groupTranscript(listOf(Wire.ultra(emptyList())))
        assertEquals(listOf("run"), kinds(rows))
        val swarm = onlySwarm(rows)
        assertEquals(0, swarm.counts.total)
        assertEquals(emptyList<String>(), swarm.pending)
    }

    /**
     * The fold has to be a pure function of its input: a session restored from
     * disk must produce exactly the same tree as the live one that made it.
     */
    @Test
    fun foldingIsDeterministic() {
        Wire.reset()
        val items = listOf(
            Wire.workflowStart("wf_1", "r", phases = listOf("A" to "", "B" to "")),
            Wire.member("a#1", "t", "wf_1", "r", phase = "A"),
            Wire.childCall("a#1", "bash", """{"command":"x"}"""),
            Wire.member("b#1", "u", "wf_1", "r", phase = "B"),
        )
        val first = groupTranscript(items)
        val second = groupTranscript(items)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(
            onlyWorkflow(first).phases.map { p -> p.title to p.members.map { it.instance } },
            onlyWorkflow(second).phases.map { p -> p.title to p.members.map { it.instance } },
        )
    }
}
