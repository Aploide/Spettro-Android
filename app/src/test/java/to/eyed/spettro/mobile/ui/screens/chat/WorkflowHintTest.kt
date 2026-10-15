package to.eyed.spettro.mobile.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.workflowInputSpans

/**
 * The composer's word on a message that asks for workflows — port of the
 * desktop's `tests/workflowHint.test.tsx`, over the pure reading the composer
 * renders from. Under Ask first the CLI refuses every workflow
 * (internal/agent/workflow.go), so a lit phrase would promise a run that
 * cannot happen: it is marked but muted, and the line under the field says
 * why and offers the way out. A "+500k" directive lights only when it would
 * be honoured, and the line says what budget it sets.
 */
class WorkflowHintTest {

    private fun options(permission: String, ultra: Boolean): List<AcpConfigOption> = listOf(
        AcpConfigOption(
            id = "permission",
            name = "Permission",
            kind = AcpConfigOption.Kind.Select(
                current = permission,
                groups = listOf(
                    AcpConfigOption.OptionGroup(
                        name = null,
                        options = listOf(
                            AcpConfigOption.Opt("Ask first", "ask-first"),
                            AcpConfigOption.Opt("Restricted", "restricted"),
                            AcpConfigOption.Opt("YOLO", "yolo"),
                        ),
                    ),
                ),
            ),
        ),
        AcpConfigOption(id = "ultra", name = "Ultra", kind = AcpConfigOption.Kind.Bool(ultra)),
    )

    private fun read(text: String, permission: String, ultra: Boolean): WorkflowDraft =
        workflowDraft(text, workflowGate(options(permission, ultra)))

    /** What the composer lights for [text] under [draft]. */
    private fun lit(text: String, draft: WorkflowDraft): List<String> =
        workflowInputSpans(text, draft.budgets).map { text.substring(it.start, it.end) }

    @Test
    fun readsTheGateOffTheChatsOptions() {
        assertEquals(
            WorkflowGate(ultraOn = true, askFirst = true, canRestrict = true),
            workflowGate(options("ask-first", ultra = true)),
        )
        assertEquals(WorkflowGate(), workflowGate(emptyList()))
        // No "restricted" to offer: the hint has no way out to show.
        val bare = listOf(
            AcpConfigOption(
                id = "permission",
                name = "Permission",
                kind = AcpConfigOption.Kind.Select(
                    "ask-first",
                    listOf(AcpConfigOption.OptionGroup(null, listOf(AcpConfigOption.Opt("Ask first", "ask-first")))),
                ),
            ),
        )
        assertFalse(workflowGate(bare).canRestrict)
    }

    @Test
    fun underAskFirstMutesThePhraseAndSaysWorkflowsArePaused() {
        val text = "ultracode: review the changes"
        val draft = read(text, "ask-first", ultra = false)
        assertTrue(draft.muted)
        assertTrue(draft.pausedByAskFirst)
        assertEquals(listOf("ultracode"), lit(text, draft))
    }

    @Test
    fun underAskFirstABudgetForTheStandingUltraStillSaysWorkflowsArePaused() {
        // Ultra is saved but suspended under Ask first; a "+500k" aimed at it
        // would be ignored, and the line has to say so rather than stay silent.
        val draft = read("review the changes +500k", "ask-first", ultra = true)
        assertTrue(draft.pausedByAskFirst)
        assertNull(draft.budgetTokens)
    }

    @Test
    fun underAskFirstSaysNothingAboutAMessageThatDoesNotAskForWorkflows() {
        val draft = read("fix the typo in README", "ask-first", ultra = false)
        assertFalse(draft.pausedByAskFirst)
        assertNull(draft.budgetTokens)
    }

    @Test
    fun withWorkflowsAllowedLightsThePhraseAtFullStrengthWithNoWarning() {
        val draft = read("ultracode: review the changes", "restricted", ultra = false)
        assertFalse(draft.muted)
        assertFalse(draft.pausedByAskFirst)
    }

    @Test
    fun lightsABudgetDirectiveBesideTheKeywordAndNamesTheBudget() {
        val text = "ultracode +500k: review the changes"
        val draft = read(text, "restricted", ultra = false)
        assertEquals(listOf("ultracode", "+500k"), lit(text, draft))
        assertEquals(500_000L, draft.budgetTokens)
    }

    @Test
    fun honoursADirectiveAloneWhenUltraIsOnAndIgnoresItWhenItIsOff() {
        val text = "review the changes +1.5m"
        val on = read(text, "yolo", ultra = true)
        assertEquals(listOf("+1.5m"), lit(text, on))
        assertEquals(1_500_000L, on.budgetTokens)
        val off = read(text, "yolo", ultra = false)
        assertEquals(emptyList<String>(), lit(text, off))
        assertNull(off.budgetTokens)
        assertFalse(off.pausedByAskFirst)
    }
}
