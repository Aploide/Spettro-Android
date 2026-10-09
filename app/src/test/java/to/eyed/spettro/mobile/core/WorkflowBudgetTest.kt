package to.eyed.spettro.mobile.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "+500k" budget directive, against the Go implementation.
 *
 * `go-budget-directives.json` (test resources) is the desktop app's
 * `tests/go-budget-directives.json` verbatim: a golden file produced by Go's
 * regexp engine running the CLI's own pattern — the generator reads
 * `budgetDirectiveRe` straight out of internal/agent/workflow_budget.go, runs
 * that file's ParseBudgetDirective and BudgetDirectiveSpans over each case and
 * dumps the result (`pattern` in the file is what it read). If the Go pattern
 * changes and the port does not, this fails — the only way to notice, because
 * both sides keep working perfectly on their own while disagreeing about what
 * a user asked for.
 *
 * To regenerate, regenerate the desktop's copy and copy it here unchanged.
 */
class WorkflowBudgetTest {

    private data class Case(val spans: List<ActivationSpan>, val tokens: Long?)

    private val golden: JsonObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("go-budget-directives.json")
            ?: error("go-budget-directives.json is missing from the test resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        SpettroJson.parseToJsonElement(text).jsonObject
    }

    private val cases: Map<String, Case> by lazy {
        golden["cases"]!!.jsonObject.mapValues { (_, value) ->
            val obj = value.jsonObject
            Case(
                spans = obj["spans"]!!.jsonArray.map { pair ->
                    val (start, end) = (pair as JsonArray).map { (it as JsonPrimitive).content.toInt() }
                    ActivationSpan(start, end)
                },
                tokens = obj["tokens"]!!.jsonPrimitive.longOrNull,
            )
        }
    }

    @Test
    fun wasGeneratedFromThePatternThisPortWasWrittenAgainst() {
        assertEquals(
            """(?:^|\s)(\+(\d+(?:\.\d+)?)\s?([kKmM]))\b""",
            golden["pattern"]!!.jsonPrimitive.content,
        )
        assertTrue(cases.size > 20)
    }

    @Test
    fun agreesWithTheGoImplementationOnEveryCase() {
        // Every case is in the Basic Multilingual Plane, where Go's rune
        // offsets and Kotlin's UTF-16 ones are the same numbers.
        for ((text, want) in cases) {
            assertEquals("spans for ${quote(text)}", want.spans, budgetDirectiveSpans(text))
            assertEquals("tokens for ${quote(text)}", want.tokens, parseBudgetDirective(text))
        }
    }

    /** Only beside workflows: the message's own words, or a standing Ultra. */
    @Test
    fun aDirectiveCountsOnlyBesideWorkflows() {
        assertFalse(budgetDirectivesLive("review this +500k", ultraActive = false))
        assertTrue(budgetDirectivesLive("ultracode: review this +500k", ultraActive = false))
        assertTrue(budgetDirectivesLive("review this +500k", ultraActive = true))
    }

    @Test
    fun lightsTheDirectiveOnlyWhenItCounts() {
        fun lit(text: String, budgets: Boolean): List<String> =
            splitWorkflowInput(text, budgets).filter { it.active }.map { it.text }
        assertEquals(listOf("ultracode", "+1.5m"), lit("ultracode +1.5m go", true))
        assertEquals(listOf("ultracode"), lit("ultracode +1.5m go", false))
        // Covers the whole string exactly once, whatever is lit.
        val text = "use a workflow +200k and +300k"
        assertEquals(text, splitWorkflowInput(text, true).joinToString("") { it.text })
        assertEquals(emptyList<ActivationPiece>(), splitWorkflowInput("", true))
    }

    /** Spelled the way the CLI prints a budget (workflow.go compactTokens). */
    @Test
    fun compactTokensSpellsABudgetTheWayTheCliPrintsIt() {
        assertEquals("500k", compactTokens(500_000))
        assertEquals("1.5m", compactTokens(1_500_000))
        assertEquals("1m", compactTokens(1_000_000))
        assertEquals("1.25m", compactTokens(1_250_000))
        assertEquals("1.5k", compactTokens(1_500))
        assertEquals("950", compactTokens(950))
    }

    private fun quote(text: String): String =
        "\"" + text.replace("\t", "\\t").replace("\n", "\\n").replace("\u00A0", "\\u00a0") + "\""
}
