package to.eyed.spettro.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The activation matcher has to agree with the CLI's, character for character:
 * the composer lights up the phrase that arms multi-agent orchestration, and a
 * highlight that disagreed with `internal/agent/workflow.go` would promise a
 * mode the run never enters.
 *
 * [GO_SPANS] is the desktop app's `tests/go-activation-spans.json` verbatim —
 * generated from the Go implementation itself, so this suite pins all three
 * front-ends to one answer.
 */
class WorkflowActivationTest {

    private val GO_SPANS: List<Pair<String, List<ActivationSpan>>> = listOf(
        "" to emptyList(),
        "ULTRACODE and ultracode twice" to listOf(ActivationSpan(0, 9), ActivationSpan(14, 23)),
        "add a workflow_dispatch trigger" to emptyList(),
        "agents are useful" to emptyList(),
        "an ultracoded word must not light up" to emptyList(),
        "can you create an orchestration workflow" to listOf(ActivationSpan(8, 40)),
        "check .github/workflows for the CI config" to emptyList(),
        "do this with workflows" to listOf(ActivationSpan(8, 22)),
        "fan it out over agents" to listOf(ActivationSpan(0, 22)),
        "fan this out across sub-agents" to listOf(ActivationSpan(0, 30)),
        "hello" to emptyList(),
        "multi agent pipeline" to listOf(ActivationSpan(0, 20)),
        "multi-agent orchestration please" to listOf(ActivationSpan(0, 25)),
        "now ultracode" to listOf(ActivationSpan(4, 13)),
        "orchestrate this with subagents" to listOf(ActivationSpan(0, 31)),
        "our deploy workflow is broken" to emptyList(),
        "please ultracode this refactor" to listOf(ActivationSpan(7, 16)),
        "reach for the workflow tool here" to listOf(ActivationSpan(14, 27)),
        "run it via workflows" to listOf(ActivationSpan(7, 20)),
        "run the workflow again" to emptyList(),
        "set up a workflow" to listOf(ActivationSpan(0, 17)),
        "the workflow failed last night" to emptyList(),
        "this is a normal message" to emptyList(),
        "ultracode" to listOf(ActivationSpan(0, 9)),
        "ultracode now" to listOf(ActivationSpan(0, 9)),
        "ultracode, then use a workflow" to listOf(ActivationSpan(0, 9), ActivationSpan(16, 30)),
        "ultracode: go" to listOf(ActivationSpan(0, 9)),
        "ultracode: review the changes and use a workflow for the port" to
            listOf(ActivationSpan(0, 9), ActivationSpan(34, 48)),
        "use a workflow and then ultracode" to
            listOf(ActivationSpan(0, 14), ActivationSpan(24, 33)),
        "use a workflow to modernise these handlers" to listOf(ActivationSpan(0, 14)),
        "use a workflow tool for this" to listOf(ActivationSpan(0, 19)),
        "use the workflow" to emptyList(),
        "what does this workflow do" to emptyList(),
        "write a multi-agent workflow for this" to listOf(ActivationSpan(0, 28)),
    )

    @Test
    fun matchesTheGoImplementationExactly() {
        for ((text, expected) in GO_SPANS) {
            assertEquals("spans for \"$text\"", expected, workflowActivationSpans(text))
        }
    }

    @Test
    fun requestedTracksTheSpans() {
        for ((text, expected) in GO_SPANS) {
            assertEquals("requested for \"$text\"", expected.isNotEmpty(), workflowRequested(text))
        }
    }

    /** Typing the keyword is a standing yes; asking in prose is a request. */
    @Test
    fun onlyTheKeywordPreapproves() {
        assertTrue(workflowPreapproved("ultracode"))
        assertTrue(workflowPreapproved("please ULTRACODE this"))
        assertFalse(workflowPreapproved("use a workflow to port this"))
        assertFalse(workflowPreapproved("an ultracoded word"))
    }

    /** Overlapping patterns must merge, never nest: "use a workflow" and
     *  "workflow tool" both match inside "use a workflow tool". */
    @Test
    fun overlappingMatchesMergeIntoOneSpan() {
        assertEquals(
            listOf(ActivationSpan(0, 19)),
            workflowActivationSpans("use a workflow tool for this"),
        )
    }

    /** The split must cover the input exactly once, in order. */
    @Test
    fun splitCoversTheWholeString() {
        for ((text, _) in GO_SPANS) {
            val pieces = splitOnActivation(text)
            assertEquals("rejoined \"$text\"", text, pieces.joinToString("") { it.text })
            assertTrue("no empty pieces in \"$text\"", pieces.none { it.text.isEmpty() })
        }
    }

    @Test
    fun splitMarksOnlyTheMatchedPhrases() {
        assertEquals(
            listOf(
                ActivationPiece("please ", false),
                ActivationPiece("ultracode", true),
                ActivationPiece(" this refactor", false),
            ),
            splitOnActivation("please ultracode this refactor"),
        )
    }

    @Test
    fun emptyTextSplitsToNothing() {
        assertEquals(emptyList<ActivationPiece>(), splitOnActivation(""))
    }

    @Test
    fun plainTextIsOneInactivePiece() {
        assertEquals(
            listOf(ActivationPiece("hello there", false)),
            splitOnActivation("hello there"),
        )
    }
}
