package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private fun runsOf(rows: List<TranscriptRow>): List<WorkflowRun> =
        rows.filterIsInstance<TranscriptRow.Run>().map { it.run }

    private fun onlyWorkflow(rows: List<TranscriptRow>): WorkflowRun {
        val found = runsOf(rows)
        assertEquals("expected exactly one workflow", 1, found.size)
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
        val found = runsOf(rows)
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
        assertEquals(RunStatus.DONE, run.status)
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
                Wire.workflowStart("wf_1", "port", phases = listOf("Port" to "")),
                Wire.member("code#1", "a", "wf_1", "port", phase = "Port"),
                Wire.member("code#12", "b", "wf_1", "port", phase = "Port"),
                Wire.childCall("code#12", "bash", """{"command":"belongs to twelve"}"""),
            ),
        )
        val members = onlyWorkflow(rows).phases[0].members
        assertEquals(0, members.first { it.instance == "code#1" }.children.size)
        assertEquals(1, members.first { it.instance == "code#12" }.children.size)
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
                Wire.workflowStart("wf_1", "r", phases = listOf("P" to "")),
                Wire.member(
                    "code#1", "a", "wf_1", "r", phase = "P", status = AcpToolStatus.COMPLETED,
                    output = """{"content":"beta\n","file":"b.txt"}""",
                ),
            ),
        )
        val member = onlyWorkflow(rows).phases[0].members[0]
        assertTrue(member.resultText.contains("b.txt"))
        assertTrue(member.resultIsJSON)
    }

    @Test
    fun isEmptyOnlyWhenTheMemberReallySaidNothing() {
        val rows = groupTranscript(
            listOf(
                Wire.workflowStart("wf_1", "r", phases = listOf("P" to "")),
                Wire.member(
                    "code#1", "a", "wf_1", "r", phase = "P",
                    status = AcpToolStatus.COMPLETED, output = "",
                ),
            ),
        )
        assertEquals("", onlyWorkflow(rows).phases[0].members[0].resultText)
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
                    id = "wf-9", runId = "wf_1", name = "done-one", agents = 1,
                    rendered = Wire.renderedTree(
                        summary = "1 agents · 0 failed · 0 replayed",
                        phases = emptyList(),
                    ),
                ),
                Wire.workflowStart("wf_2", "live-one", phases = emptyList()),
            ),
        )
        val live = activeRuns(rows)
        assertEquals(1, live.size)
        assertEquals("live-one", live[0].name)
    }

    @Test
    fun activeRunsLeavesOutAPausedRunAndACardALaterTurnTookOver() {
        // Neither is working: one waits for Spettro, the other has handed the
        // run to the card below. A live strip holding either would never empty.
        val paused = Wire.CardRun(
            runId = "wf_1", name = "audit", status = "paused",
            checkpointId = "cp-1", waiting = "fix which?",
        )
        val rows = groupTranscript(
            listOf(
                Wire.workflowCard(paused, continuedIn = "workflow-wf_1-2"),
                Wire.workflowCard(Wire.CardRun(runId = "wf_2", name = "other", status = "paused")),
            ),
        )
        assertEquals(emptyList<WorkflowRun>(), activeRuns(rows))
    }

    @Test
    fun titlesARunForThePanel() {
        val rows = groupTranscript(
            listOf(Wire.workflowStart("wf_1", "review-changes", phases = emptyList())),
        )
        assertEquals(listOf("review-changes"), runsOf(rows).map { it.displayTitle })
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
                Wire.tool(
                    title = "workflow audit", argsJSON = "[]",
                    workflow = Wire.metaOf("""{"version":1,"phases":"nope","members":[null,3]}"""),
                ),
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
                    Wire.workflowStart("wf_1", "r", phases = listOf("P" to "")),
                    Wire.member(
                        "code#1", "a", "wf_1", "r", phase = "P",
                        status = AcpToolStatus.FAILED, output = output,
                    ),
                ),
            )
            return onlyWorkflow(rows).phases[0].members[0].failureReason
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
                Wire.workflowStart("wf_1", "r", phases = listOf("P" to "")),
                Wire.member(
                    "code#1", "a", "wf_1", "r", phase = "P", status = AcpToolStatus.COMPLETED,
                    output = """{"agent":"code#1","status":"error","summary":"nope"}""",
                ),
            ),
        )
        val run = onlyWorkflow(rows)
        assertEquals(OrchStatus.FAILED, run.members[0].status)
        assertEquals(1, run.counts.failed)
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

    /** A run with no members at all still renders as a run. */
    @Test
    fun aRunThatDispatchedNothingIsStillARun() {
        val rows = groupTranscript(listOf(Wire.workflowStart("wf_1", "r")))
        assertEquals(listOf("run"), kinds(rows))
        assertEquals(0, onlyWorkflow(rows).counts.total)
    }

    /** The CLI dropped swarms: an `ultra` call is an ordinary row now, and
     *  an `agent` call it once fanned out is a plain delegation. */
    @Test
    fun anUltraCallIsAnOrdinaryToolRow() {
        val rows = groupTranscript(
            listOf(
                Wire.tool(title = "ultra", argsJSON = """{"items":["a"],"subagent_type":"code"}"""),
                Wire.tool(
                    title = "agent code#1: a", kind = "think", status = AcpToolStatus.IN_PROGRESS,
                    argsJSON = """{"agent":"code#1","task":"a","swarm":true}""",
                ),
            ),
        )
        assertEquals(listOf("item", "agent"), kinds(rows))
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

    // -----------------------------------------------------------------------
    // The card the CLI sends today
    // -----------------------------------------------------------------------

    /**
     * A run in the middle of everything the card can say: a size and a budget,
     * a detail line, a phase added at runtime, a replayed member, one that
     * failed, one the runtime reports in a word it does not name, a member
     * outside any phase, and a log whose head was trimmed.
     */
    private val audit = Wire.CardRun(
        runId = "wf_1",
        name = "audit",
        description = "Audit the repo",
        size = "large",
        sizeAgents = 30,
        budget = 500_000,
        phases = listOf(
            Wire.CardPhaseIn("Scan", "find candidates"),
            Wire.CardPhaseIn("Fix"),
            Wire.CardPhaseIn("Verify auth", "3 suspects", dynamic = true),
        ),
        agents = listOf(
            Wire.CardAgent("gp#1", "scan a", "Scan", "success"),
            Wire.CardAgent("gp#2", "scan b", "Scan", "error"),
            Wire.CardAgent("gp#3", "scan c", "Scan", "success", cached = true),
            Wire.CardAgent("gp#4", "fix a", "Fix", "running"),
            Wire.CardAgent("gp#5", "fix b", "Fix", "queued"),
            Wire.CardAgent("gp#6", "loose", status = "running"),
        ),
        logs = listOf("3 findings", "⏸ cp-1 pick targets"),
        dropped = 12,
    )

    // -----------------------------------------------------------------------
    // Reading the card's text (an older CLI, or a snapshot without the meta)
    // -----------------------------------------------------------------------

    // The exact strings internal/acp/workflow_test.go expects render() to
    // write, with the test line each comes from.

    @Test
    fun readsAPauseAndWhatItWaitsFor() {
        // workflow_test.go:467
        val text = parseRenderedWorkflow(
            "⏸ paused at cp-1 — waiting for orchestrator: which findings to fix?\n\n▸ Scan — 1/1 done\n    ✓ gp#1  scan a",
            false,
        )
        assertEquals("paused", text.status)
        assertEquals(PausedAt("cp-1", "which findings to fix?"), text.pausedAt)
        // The pause line is not the description.
        assertEquals("", text.description)
    }

    @Test
    fun readsAPauseWithNoCheckpointIdOrMessage() {
        val text = parseRenderedWorkflow("⏸ waiting for orchestrator\n\n○ Scan — pending", false)
        assertEquals("paused", text.status)
        assertEquals(PausedAt("", ""), text.pausedAt)
    }

    @Test
    fun readsAStopAndItsReason() {
        // workflow_test.go:778, :808
        for (reason in listOf("at the orchestrator's request", "paused for over 30m with no continue")) {
            val text = parseRenderedWorkflow("Audit\n\n■ stopped: $reason\n\n○ Scan — pending", false)
            assertEquals("stopped", text.status)
            assertEquals(reason, text.stoppedReason)
            assertEquals("Audit", text.description)
        }
    }

    @Test
    fun readsTheSizeLineWithAndWithoutABudget() {
        // workflow_test.go:692-694
        val large = parseRenderedWorkflow("size: large (~30 agents, a guideline)\n\n○ Scan — pending", false)
        assertEquals(Triple("large", 30, 0L), Triple(large.size, large.sizeAgents, large.budgetTokens))
        val unbounded = parseRenderedWorkflow(
            "size: unbounded (no guideline) · budget 500k tokens\n\n○ Scan — pending",
            false,
        )
        assertEquals(
            Triple("unbounded", 0, 500_000L),
            Triple(unbounded.size, unbounded.sizeAgents, unbounded.budgetTokens),
        )
        assertEquals("", unbounded.description)
    }

    @Test
    fun takesTheSizeAndBudgetSuffixesOffTheTitleForTheName() {
        // workflow_test.go:692-694
        assertEquals("audit", nameFromTitle("workflow audit"))
        assertEquals("audit", nameFromTitle("workflow audit · large"))
        assertEquals("audit", nameFromTitle("workflow audit · unbounded · budget 500k"))
        assertEquals("audit", nameFromTitle("[code#3] workflow audit · small"))
        assertEquals("", nameFromTitle("workflow"))
        assertEquals(1_500_000L, parseCompactTokens("1.5m"))
        assertEquals(500_000L, parseCompactTokens("500k"))
        assertEquals(950L, parseCompactTokens("950"))
        assertEquals(0L, parseCompactTokens("lots"))
    }

    @Test
    fun readsDetailLinesAndPhasesAddedAtRuntime() {
        // workflow_test.go:674
        val text = parseRenderedWorkflow(
            "○ Scan — pending\n    ↳ find candidates\n○ Verify auth (added at runtime) — pending\n    ↳ 3 suspects",
            false,
        )
        assertEquals(
            listOf(
                CardPhase("Scan", "find candidates", false),
                CardPhase("Verify auth", "3 suspects", true),
            ),
            text.phases,
        )
    }

    @Test
    fun readsEveryMemberGlyphTheReplayedPrefixAndTheNoPhaseBucket() {
        // workflow_test.go:826
        val text = parseRenderedWorkflow(
            listOf(
                "▸ Scan — 2/3 done, 1 failed",
                "    ✓ gp#1  scan a",
                "    ✗ gp#2  scan b",
                "    ▶ gp#3  replayed · scan c",
                "▸ (no phase) — 0/1 done",
                "    · gp#4  loose",
            ).joinToString("\n"),
            false,
        )
        assertEquals(
            listOf(
                listOf("gp#1", OrchStatus.DONE, false, "Scan", "scan a"),
                listOf("gp#2", OrchStatus.FAILED, false, "Scan", "scan b"),
                listOf("gp#3", OrchStatus.RUNNING, true, "Scan", "scan c"),
                listOf("gp#4", OrchStatus.PENDING, false, "", "loose"),
            ),
            text.members.map { listOf(it.instance, it.status, it.replayed, it.phase, it.task) },
        )
        assertEquals(listOf("Scan", ""), text.phases.map { it.title })
    }

    @Test
    fun readsTheLogTailAndHowManyLinesWereDroppedBeforeIt() {
        val text = parseRenderedWorkflow("○ Scan — pending\n\nlog:\n  … 12 earlier lines\n  3 findings", false)
        assertEquals(listOf("3 findings"), text.logs)
        assertEquals(12, text.droppedLogLines)
    }

    @Test
    fun readsTheFinishSummaryAndTheContinuedPrefixOffTheFront() {
        val finished = parseRenderedWorkflow(
            "5 agents · 1 failed · 1 replayed\n\nAudit\n\n▸ Scan — 1/1 done\n    ✓ gp#1  a",
            false,
        )
        assertEquals("5 agents · 1 failed · 1 replayed", finished.summary)
        assertEquals("Audit", finished.description)

        val continued = parseRenderedWorkflow(
            "continued in a later turn\n\nAudit\n\n⏸ paused at cp-1 — waiting for orchestrator: fix which?\n\n○ Scan — pending",
            false,
        )
        assertTrue(continued.continued)
        assertEquals("Audit", continued.description)
        assertEquals("paused", continued.status)
    }

    @Test
    fun treatsAStatusTheCliDoesNotNameAsStillRunning() {
        // workflow.go render default
        val run = onlyWorkflow(
            groupTranscript(
                listOf(Wire.workflowCard(Wire.CardRun(runId = "wf_1", name = "audit", status = "draining"), meta = false)),
            ),
        )
        assertEquals(RunStatus.RUNNING, run.status)
    }

    @Test
    fun keepsADescriptionThatLooksLikeTheSizeLine() {
        val text = parseRenderedWorkflow(
            "size: whatever I like, really\n\nsize: small (~5 agents, a guideline)\n\n○ A — pending",
            false,
        )
        assertEquals("size: whatever I like, really", text.description)
        assertEquals("small", text.size)
    }

    @Test
    fun aTrailingCarriageReturnDoesNotHideTheStateLine() {
        // Java's `$` matches before a final line terminator where JavaScript's
        // does not; the reader must not depend on either.
        val text = parseRenderedWorkflow("■ stopped: done for now\r\n\r\n○ Scan — pending\r\n", false)
        assertEquals("stopped", text.status)
        assertEquals("done for now", text.stoppedReason)
    }

    @Test
    fun drawsTheSameRunFromTheTextAsFromTheMetadata() {
        // The fallback is only worth having if it agrees with the real thing.
        // Every state the card can be in, both ways.
        val states = listOf(
            audit,
            audit.copy(status = "paused", checkpointId = "cp-1", waiting = "fix which?"),
            audit.copy(status = "stopped", stopReason = "at the orchestrator's request"),
            audit.copy(status = "success", agents = audit.agents.map { it.copy(status = "success") }),
        )
        fun shape(run: WorkflowRun): List<Any?> = listOf(
            run.name,
            run.runId,
            run.status,
            run.pausedAt,
            run.stoppedReason,
            Triple(run.size, run.sizeAgents, run.budgetTokens),
            run.description,
            run.phases.map { p ->
                listOf(p.title, p.detail, p.dynamic, p.members.map { listOf(it.instance, it.status, it.cached, it.task) })
            },
            run.logs to run.droppedLogLines,
        )
        for (state in states) {
            Wire.reset()
            val fromMeta = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(state))))
            Wire.reset()
            val fromText = onlyWorkflow(
                groupTranscript(listOf(Wire.workflowCard(state, meta = false, rawInput = Wire.RawInput.NONE))),
            )
            assertEquals(RunSource.META, fromMeta.source)
            assertEquals(RunSource.TEXT, fromText.source)
            assertEquals("state ${state.status}", shape(fromMeta), shape(fromText))
        }
    }

    // -----------------------------------------------------------------------
    // The card's metadata (`_meta["spettro.app/workflow"]`)
    // -----------------------------------------------------------------------

    @Test
    fun theMetadataIsTheSourceOfTheRunWhenPresent() {
        val run = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit))))
        assertEquals(RunSource.META, run.source)
        assertEquals("audit", run.name)
        assertEquals("large", run.size)
        assertEquals(30, run.sizeAgents)
        assertEquals(500_000L, run.budgetTokens)
        assertEquals(
            listOf("Scan" to false, "Fix" to false, "Verify auth" to true, "" to false),
            run.phases.map { it.title to it.dynamic },
        )
        assertEquals("find candidates", run.phases[0].detail)
        assertEquals(12, run.droppedLogLines)
    }

    @Test
    fun countsAPhasesFailedMembersAsFinishedAsTheClisDoneCountDoes() {
        val scan = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit)))).phases[0]
        assertEquals(3, scan.counts.finished)
        assertEquals(1, scan.counts.failed)
        assertEquals(1, scan.counts.cached)
    }

    @Test
    fun keepsAMemberItReportsInAnUnnamedStateAsNotStarted() {
        val fix = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit)))).phases[1]
        assertEquals(listOf(OrchStatus.RUNNING, OrchStatus.PENDING), fix.members.map { it.status })
        // Not started is still in progress: the phase is not "done".
        assertEquals(PhaseState.RUNNING, fix.state)
    }

    @Test
    fun theLiveSheetCallsAPhaseOfOnlyQueuedMembersPendingNotRunning() {
        // The next wave of a fan-out, held back by max_concurrency: the card
        // counts it as in progress, but nobody in it is working yet.
        val queued = audit.copy(
            agents = listOf(
                Wire.CardAgent("gp#1", "scan a", "Scan", "running"),
                Wire.CardAgent("gp#2", "fix a", "Fix", "queued"),
                Wire.CardAgent("gp#3", "fix b", "Fix", "queued"),
            ),
        )
        val (scan, fix) = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(queued)))).phases
        assertEquals(PhaseState.RUNNING, fix.state)
        assertEquals(PhaseState.PENDING, fix.liveState)
        assertEquals(PhaseState.RUNNING, scan.liveState)
    }

    @Test
    fun theLiveSheetRanksRunningThenFailedThenDoneAsTheDesktopPanelDoes() {
        val (scan, fix, verify) = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit)))).phases
        // Scan: two done, one failed, nobody running.
        assertEquals(PhaseState.FAILED, scan.liveState)
        // Fix: one running beside one queued.
        assertEquals(PhaseState.RUNNING, fix.liveState)
        // Verify auth: declared, nothing dispatched.
        assertEquals(PhaseState.PENDING, verify.liveState)
        val done = audit.copy(agents = listOf(Wire.CardAgent("gp#1", "scan a", "Scan", "success")))
        assertEquals(
            PhaseState.DONE,
            onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(done)))).phases[0].liveState,
        )
    }

    @Test
    fun theMetadataWinsOverTextItDisagreesWith() {
        val card = Wire.workflowCard(audit) as TranscriptItem.Tool
        val item = TranscriptItem.Tool(card.tool.copy(output = "▸ Bogus — 0/0 done"))
        val run = onlyWorkflow(groupTranscript(listOf(item)))
        assertFalse(run.phases.map { it.title }.contains("Bogus"))
    }

    @Test
    fun fallsBackToTheTextForAVersionItWasNotWrittenFor() {
        val card = Wire.workflowCard(audit) as TranscriptItem.Tool
        val item = TranscriptItem.Tool(card.tool.copy(workflow = Wire.metaOf("""{"version":2,"phases":[]}""")))
        val run = onlyWorkflow(groupTranscript(listOf(item)))
        assertEquals(RunSource.TEXT, run.source)
        assertTrue(run.phases.map { it.title }.contains("Verify auth"))
    }

    @Test
    fun matchesListedMembersToTheirOwnCallsAndStandsInForMissingOnes() {
        val rows = groupTranscript(
            listOf(
                Wire.workflowCard(audit),
                Wire.member("gp#1", "scan a", "wf_1", "audit", phase = "Scan", status = AcpToolStatus.COMPLETED),
                Wire.childCall("gp#1", "read", """{"file_path":"a.go"}"""),
            ),
        )
        val run = onlyWorkflow(rows)
        // One row: the member and its call are inside the card.
        assertEquals(1, rows.size)
        val (gp1, gp2) = run.phases[0].members
        assertEquals(1, gp1.children.size)
        // gp#2's call never reached this transcript; the card still lists it.
        assertEquals("gp#2", gp2.instance)
        assertEquals(OrchStatus.FAILED, gp2.status)
        assertEquals(emptyList<ToolCallItem>(), gp2.children)
    }

    @Test
    fun readsAPauseAsWaitingNeverAsRunning() {
        val run = onlyWorkflow(
            groupTranscript(
                listOf(Wire.workflowCard(audit.copy(status = "paused", checkpointId = "cp-1", waiting = "fix which?"))),
            ),
        )
        assertEquals(RunStatus.PAUSED, run.status)
        assertEquals(PausedAt("cp-1", "fix which?"), run.pausedAt)
    }

    @Test
    fun readsAStopAsStoppedNotDoneThoughTheCardClosesCompleted() {
        val run = onlyWorkflow(
            groupTranscript(listOf(Wire.workflowCard(audit.copy(status = "stopped", stopReason = "the session closed")))),
        )
        assertEquals(AcpToolStatus.COMPLETED, run.tool.status)
        assertEquals(RunStatus.STOPPED, run.status)
        assertEquals("the session closed", run.stoppedReason)
    }

    @Test
    fun readsACancelledRunAsStoppedNotAsAFailure() {
        val run = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit.copy(status = "cancelled")))))
        assertEquals(AcpToolStatus.FAILED, run.tool.status)
        assertEquals(RunStatus.STOPPED, run.status)
        assertEquals("cancelled", run.stoppedReason)
    }

    @Test
    fun settlesMembersStillRunningWhenTheirRunEndsInsteadOfSpinningOn() {
        // A cancelled turn closes the card with its members as they last were.
        val run = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit.copy(status = "cancelled")))))
        val byInstance = run.phases.flatMap { it.members }.associate { it.instance to it.status }
        assertEquals(OrchStatus.STOPPED, byInstance["gp#4"])
        assertEquals(OrchStatus.STOPPED, byInstance["gp#6"])
        assertEquals(OrchStatus.DONE, byInstance["gp#1"])
        assertEquals(0, run.counts.running)
        assertEquals(2, run.counts.stopped)
        // A run that is only waiting keeps its members as they are.
        Wire.reset()
        val paused = onlyWorkflow(
            groupTranscript(
                listOf(Wire.workflowCard(audit.copy(status = "paused", checkpointId = "cp-1", waiting = "which?"))),
            ),
        )
        assertEquals(2, paused.counts.running)
    }

    @Test
    fun carriesTheSummaryOfAFinishedRun() {
        val done = audit.copy(status = "success", agents = audit.agents.take(3))
        val run = onlyWorkflow(
            groupTranscript(listOf(Wire.workflowCard(done, summary = "3 agents · 1 failed · 1 replayed"))),
        )
        assertEquals(RunStatus.DONE, run.status)
        assertEquals("3 agents · 1 failed · 1 replayed", run.summary)
    }

    @Test
    fun theMetadataCarriedThroughASnapshotFoldsTheSameAsLive() {
        // A reopened chat restores each tool's `workflow` from the snapshot
        // (StoredSessionData) onto the same ToolCallItem field the live
        // update fills, so the fold cannot tell them apart.
        val live = Wire.workflowCard(audit) as TranscriptItem.Tool
        val restored = TranscriptItem.Tool(
            ToolCallItem(
                id = live.tool.id,
                title = live.tool.title,
                status = live.tool.status,
                timestamp = live.tool.timestamp,
                workflow = live.tool.workflow,
            ),
        )
        val fromLive = onlyWorkflow(groupTranscript(listOf(live)))
        val fromSnapshot = onlyWorkflow(groupTranscript(listOf(restored)))
        assertEquals(fromLive.phases.map { it.title }, fromSnapshot.phases.map { it.title })
        assertEquals(fromLive.counts, fromSnapshot.counts)
        assertEquals(fromLive.budgetTokens, fromSnapshot.budgetTokens)
    }

    // -----------------------------------------------------------------------
    // A run continued in a later turn
    // -----------------------------------------------------------------------

    // workflow_test.go:490-540: a run pauses in turn one, turn two continues
    // it. Turn one's card closes "continued in a later turn" with
    // `continuedIn` and its state as it paused; turn two opens
    // `workflow-wf_1-2`.
    private val pausedRun = Wire.CardRun(
        runId = "wf_1",
        name = "audit",
        phases = listOf(Wire.CardPhaseIn("Scan"), Wire.CardPhaseIn("Fix")),
        agents = listOf(Wire.CardAgent("gp#1", "scan a", "Scan", "success")),
        status = "paused",
        checkpointId = "cp-1",
        waiting = "fix which?",
    )
    private val resumedRun = pausedRun.copy(
        status = "running",
        attach = 2,
        continuedFrom = "workflow-wf_1",
        agents = pausedRun.agents + Wire.CardAgent("gp#2", "fix a", "Fix", "running"),
    )

    private fun continuedTranscript(meta: Boolean): List<TranscriptRow> = groupTranscript(
        listOf(
            Wire.message(ChatMessage.Role.User, "ultracode audit"),
            Wire.workflowCard(pausedRun, meta = meta, continuedIn = "workflow-wf_1-2"),
            Wire.member("gp#1", "scan a", "wf_1", "audit", phase = "Scan", status = AcpToolStatus.COMPLETED),
            Wire.childCall("gp#1", "read", """{"file_path":"a.go"}"""),
            Wire.message(ChatMessage.Role.User, "fix the first one"),
            Wire.workflowCard(
                resumedRun,
                meta = meta,
                rawInput = if (meta) Wire.RawInput.DECLARED else Wire.RawInput.NONE,
            ),
            Wire.member("gp#2", "fix a", "wf_1", "audit", phase = "Fix"),
        ),
    )

    @Test
    fun marksTheEarlierCardContinuedStillShowingThePause() {
        for (meta in listOf(true, false)) {
            Wire.reset()
            val first = runsOf(continuedTranscript(meta))[0]
            assertTrue("meta=$meta", first.continued)
            assertEquals("meta=$meta", RunStatus.PAUSED, first.status)
            if (meta) assertEquals("workflow-wf_1-2", first.continuedIn)
        }
    }

    @Test
    fun givesTheLaterCardEveryMemberWithTheCallsAnEarlierTurnMade() {
        for (meta in listOf(true, false)) {
            Wire.reset()
            val second = runsOf(continuedTranscript(meta))[1]
            assertFalse("meta=$meta", second.continued)
            assertEquals("meta=$meta", RunStatus.RUNNING, second.status)
            assertEquals("meta=$meta", "wf_1", second.runId)
            assertEquals("meta=$meta", "audit", second.name)
            val scan = second.phases.first { it.title == "Scan" }
            assertEquals("meta=$meta", "gp#1", scan.members[0].instance)
            // Turn one's member call, children and all, found from turn two's card.
            assertEquals("meta=$meta", 1, scan.members[0].children.size)
            assertEquals("meta=$meta", 2, second.counts.total)
        }
    }

    @Test
    fun showsOnlyTheLaterCardAsLive() {
        for (meta in listOf(true, false)) {
            Wire.reset()
            val live = activeRuns(continuedTranscript(meta))
            assertEquals("meta=$meta", 1, live.size)
            assertEquals("meta=$meta", "workflow-wf_1-2", live[0].tool.id)
        }
    }

    @Test
    fun foldsTheModelsReplyToTheCheckpointIntoTheRun() {
        val rows = groupTranscript(
            listOf(
                Wire.workflowCard(pausedRun, continuedIn = "workflow-wf_1-2"),
                Wire.tool(
                    title = """workflow {"continue_run_id": "wf_1", "reply": {"fix": ["a"]}}""",
                    kind = "think",
                    argsJSON = """{"continue_run_id":"wf_1","reply":{"fix":["a"]}}""",
                    output = "<workflow_result name=\"audit\" run_id=\"wf_1\">\n</workflow_result>",
                ),
                Wire.workflowCard(resumedRun),
            ),
        )
        assertEquals(listOf("run", "run"), kinds(rows))
        // The later card continues a program the first card already shows.
        assertNull(runsOf(rows)[1].script)
    }

    @Test
    fun keepsARefusedReplyVisibleSinceItIsTheOnlyPlaceTheRefusalIsSaid() {
        // internal/agent/workflow.go:456 — continuing under Ask first is refused.
        val rows = groupTranscript(
            listOf(
                Wire.workflowCard(pausedRun),
                Wire.tool(
                    title = """workflow {"continue_run_id": "wf_1"}""",
                    kind = "think",
                    status = AcpToolStatus.FAILED,
                    argsJSON = """{"continue_run_id":"wf_1","reply":"go"}""",
                    output = "workflow: continuing run wf_1 starts sub-agents again, which needs " +
                        "restricted or yolo permission (current: ask-first)",
                ),
            ),
        )
        assertEquals(listOf("run", "script"), kinds(rows))
    }

    // -----------------------------------------------------------------------
    // Telling a run's card from things that look like one
    // -----------------------------------------------------------------------

    @Test
    fun neverOpensARunForAnEscapedCheckpointTrace() {
        // internal/acp/tools.go toolCallTitle: a progress trace that finds no
        // open card goes out as an ordinary call — `workflow audit ⏸ cp-2`,
        // args with `workflow` and no `agent`, exactly what a card's args look
        // like.
        val rows = groupTranscript(
            listOf(
                Wire.tool(
                    title = "workflow audit ⏸ cp-2",
                    kind = "think",
                    argsJSON = """{"run_id":"wf_1","workflow":"audit","kind":"checkpoint","checkpoint_id":"cp-2","message":"m"}""",
                ),
                Wire.tool(
                    title = "workflow audit",
                    kind = "think",
                    argsJSON = """{"run_id":"wf_1","workflow":"audit","checkpoint_id":"cp-2","message":"m"}""",
                ),
            ),
        )
        assertEquals(emptyList<WorkflowRun>(), runsOf(rows))
    }

    @Test
    fun recognisesAReAnnouncedCardWithNoArgsByItsIdAndTitle() {
        // A card a later turn opened through a member trace carries no
        // rawInput (announceWorkflowLocked(w, nil)), and an older host kept no
        // meta.
        val rows = groupTranscript(
            listOf(
                Wire.workflowCard(
                    Wire.CardRun(
                        runId = "wf_20261006_ab12cd34", name = "audit", size = "unbounded",
                        budget = 1_500_000, attach = 2,
                    ),
                    meta = false,
                    rawInput = Wire.RawInput.NONE,
                ),
            ),
        )
        val run = onlyWorkflow(rows)
        assertEquals("audit", run.name)
        assertEquals("wf_20261006_ab12cd34", run.runId)
        assertEquals(1_500_000L, run.budgetTokens)
    }

    @Test
    fun doesNotTakeAMembersOwnCallForACard() {
        val rows = groupTranscript(
            listOf(
                Wire.workflowCard(audit),
                Wire.member("gp#1", "scan a", "wf_1", "audit", phase = "Scan"),
            ),
        )
        assertEquals(1, runsOf(rows).size)
    }

    // -----------------------------------------------------------------------
    // Runs without an id
    // -----------------------------------------------------------------------

    @Test
    fun runsWithoutAnIdKeepTheirOwnMembersEvenWhenInstanceNamesRepeat() {
        // A run with no id gets a `wf-<n>` card, and an older CLI used that
        // form for every card. The empty id is not one run: pooling members
        // under it handed each run's `code#1` to every other run's card.
        fun idless(name: String, task: String, id: String): TranscriptItem {
            val card = Wire.workflowCard(
                Wire.CardRun(
                    runId = "", name = name, status = "success",
                    phases = listOf(Wire.CardPhaseIn("P")),
                    agents = listOf(Wire.CardAgent("code#1", task, "P", "success")),
                ),
                rawInput = Wire.RawInput.NONE,
            ) as TranscriptItem.Tool
            return TranscriptItem.Tool(card.tool.copy(id = id))
        }
        val rows = groupTranscript(
            listOf(
                idless("one", "first", "wf-1"),
                Wire.member("code#1", "first", "", "one", phase = "P", status = AcpToolStatus.COMPLETED, output = "FIRST"),
                idless("two", "second", "wf-2"),
                Wire.member("code#1", "second", "", "two", phase = "P", status = AcpToolStatus.COMPLETED, output = "SECOND"),
            ),
        )
        assertEquals(
            listOf(listOf("FIRST"), listOf("SECOND")),
            runsOf(rows).map { run -> run.phases[0].members.map { it.resultText } },
        )
    }

    // -----------------------------------------------------------------------
    // A long phase trimmed to its row cap
    // -----------------------------------------------------------------------

    @Test
    fun keepsMembersCutOffByTheRunEndingAheadOfOnesThatFinished() {
        fun call(instance: String, status: OrchStatus) = MemberCall(
            tool = ToolCallItem(id = instance, title = "agent $instance", status = AcpToolStatus.COMPLETED),
            instance = instance, specId = instance, index = null, task = "", phase = "",
            cached = false, status = status, children = emptyList(), result = null,
            resultText = "", resultIsJSON = false,
        )
        val members = listOf("a", "b", "c", "d").map { call(it, OrchStatus.DONE) } +
            call("cut", OrchStatus.STOPPED) + call("bad", OrchStatus.FAILED)
        val (shown, hidden) = capMembers(members, 3)
        assertEquals(listOf("a", "cut", "bad"), shown.map { it.instance })
        assertEquals(3, hidden)
        // A member not yet started is never trimmed, any more than a running one.
        val waiting = members + call("next", OrchStatus.PENDING)
        assertTrue(capMembers(waiting, 1).first.any { it.instance == "next" })
    }

    // -----------------------------------------------------------------------
    // What the card says (port of workflowCard.test.tsx)
    // -----------------------------------------------------------------------

    private val parseError =
        "error: workflow \"typo-review\": script does not parse: SyntaxError: (anonymous): Line 35:177 Unexpected token )"

    private val typoReview = Wire.CardRun(
        runId = "wf_1",
        name = "typo-review",
        description = "Review each file for typos",
        phases = listOf(Wire.CardPhaseIn("Review")),
    )

    @Test
    fun plainWorkflowErrorNamesASyntaxErrorAndItsLine() {
        assertEquals("The workflow script has a syntax error (line 35).", plainWorkflowError(parseError))
        assertEquals("The workflow script has a syntax error.", plainWorkflowError("SyntaxError: oops"))
    }

    @Test
    fun plainWorkflowErrorNamesAMissingSavedWorkflow() {
        assertEquals(
            "There’s no saved workflow named “check-files”.",
            plainWorkflowError("error: workflow: no saved workflow named \"check-files\" (looked in /tmp/x)"),
        )
    }

    @Test
    fun plainWorkflowErrorTakesThePlumbingOffAnythingElse() {
        assertEquals("Budget exhausted", plainWorkflowError("error: workflow \"x\": budget exhausted"))
        assertEquals("", plainWorkflowError(""))
    }

    @Test
    fun saysWhyARunThatNeverStartedFailed() {
        val run = onlyWorkflow(
            groupTranscript(listOf(Wire.workflowCard(typoReview.copy(status = "error"), summary = parseError))),
        )
        assertEquals(RunStatus.FAILED, run.status)
        assertEquals("The workflow script has a syntax error (line 35).", runStateLine(run))
        assertEquals("The workflow script has a syntax error (line 35).", runStateDetail(run))
        // Nothing ran, so there is nothing to meter.
        assertEquals(0, run.counts.total)
        assertEquals(PhaseState.PENDING, run.phases[0].state)
    }

    @Test
    fun leavesTheReasonToTheMemberWhenAMemberFailed() {
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowCard(
                        typoReview.copy(
                            status = "error",
                            agents = listOf(Wire.CardAgent("review#1", "review a", "Review", "error")),
                        ),
                        summary = "1 agent · 1 failed · 0 replayed",
                    ),
                ),
            ),
        )
        assertNull(runFailure(run))
        assertEquals("", runStateLine(run))
        assertEquals("1 agent · 1 failed · 0 replayed", runStateDetail(run))
    }

    @Test
    fun aFinishedRunSettlesToItsSummaryWithNoStateLine() {
        val run = onlyWorkflow(
            groupTranscript(
                listOf(
                    Wire.workflowCard(
                        typoReview.copy(
                            status = "success",
                            agents = listOf(Wire.CardAgent("review#1", "review a", "Review", "success")),
                        ),
                        summary = "1 agent · 0 failed · 0 replayed",
                    ),
                ),
            ),
        )
        assertEquals(RunStatus.DONE, run.status)
        assertEquals("", runStateLine(run))
        assertEquals("1 agent · 0 failed · 0 replayed", runStateDetail(run))
    }

    @Test
    fun aPausedStoppedOrContinuedRunSaysSoInWords() {
        val paused = onlyWorkflow(
            groupTranscript(listOf(Wire.workflowCard(audit.copy(status = "paused", checkpointId = "cp-1", waiting = "fix which?")))),
        )
        assertEquals("Waiting — fix which?", runStateLine(paused))
        assertEquals("fix which?", runStateDetail(paused))
        assertEquals("Waiting — fix which?", runSettledNote(paused))

        Wire.reset()
        val silent = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit.copy(status = "paused")))))
        assertEquals("Waiting for Spettro to continue it", runStateLine(silent))
        assertEquals("Waiting for Spettro", runSettledNote(silent))

        Wire.reset()
        val stopped = onlyWorkflow(
            groupTranscript(listOf(Wire.workflowCard(audit.copy(status = "stopped", stopReason = "the session closed")))),
        )
        assertEquals("Stopped — the session closed", runStateLine(stopped))
        assertEquals("Stopped — the session closed", runSettledNote(stopped))

        Wire.reset()
        val continued = runsOf(continuedTranscript(meta = true))[0]
        assertEquals("Continued below — this run went on in a later turn", runStateLine(continued))
    }

    @Test
    fun saysWhatTheRunWasSizedForInPlainWords() {
        val run = onlyWorkflow(groupTranscript(listOf(Wire.workflowCard(audit))))
        assertEquals("Large · ~30 agents", sizeLabel(run))
        assertEquals("Large · ~30 agents · 500k token budget", planLabel(run))
        Wire.reset()
        val unbounded = onlyWorkflow(
            groupTranscript(listOf(Wire.workflowCard(audit.copy(size = "unbounded", sizeAgents = 0, budget = 0)))),
        )
        assertEquals("Unbounded · no agent limit", planLabel(unbounded))
        Wire.reset()
        val plain = onlyWorkflow(groupTranscript(listOf(Wire.workflowStart("wf_1", "r"))))
        assertEquals("", planLabel(plain))
    }
}
