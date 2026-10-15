package to.eyed.spettro.mobile.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Token-budget directives: "+500k", "+1.5m" written into a message.
 *
 * A direct port of `budgetDirectiveRe`, `ParseBudgetDirective` and
 * `BudgetDirectiveSpans` in the CLI's internal/agent/workflow_budget.go (by way
 * of the desktop app's `shared/workflowBudget.ts`), for the same reason
 * [workflowActivationSpans] is one: the composer lights a directive up as it is
 * typed, and that is only honest if the match is the one that decides the
 * budget. A "+500k" the run would ignore must stay dark.
 *
 * A directive only does something when workflows are on for the message (the
 * keyword, or Ultra), so callers decide whether to look for one at all — see
 * [budgetDirectivesLive].
 */

/**
 * The Go pattern is `(?:^|\s)(\+(\d+(?:\.\d+)?)\s?([kKmM]))\b`, and every
 * class in it is spelled out here rather than trusted to mean the same thing:
 *
 *  - Go's `\s` is ASCII whitespace only (`[\t\n\f\r ]`); a no-break space
 *    before the "+" must not count.
 *  - Go's `\d` is ASCII. On a device `java.util.regex` is backed by ICU, whose
 *    `\d` takes every script's digits, so a full-width "+１k" would light.
 *  - The `\b` after the suffix sits after a letter, so it can only mean "the
 *    next character is not an ASCII word character" — which is what is
 *    written, because ICU's `\b` is Unicode-aware and would let "+500ké" go.
 *
 * It has to stand on its own — the start of the message or whitespace before
 * it — so "a+500k salary" and "C++" never read as one, and the suffix is
 * mandatory so a bare "+5" in arithmetic stays arithmetic.
 */
private val BUDGET_DIRECTIVE =
    Regex("""(?:^|[\t\n\u000C\r ])(\+([0-9]+(?:\.[0-9]+)?)[\t\n\u000C\r ]?([kKmM]))(?![A-Za-z0-9_])""")

/** maxBudgetDirective: a billion tokens. Anything larger is a typo. */
private const val MAX_BUDGET_DIRECTIVE = 1_000_000_000.0

private data class Directive(val span: ActivationSpan, val number: String, val suffix: String)

private fun directives(text: String): List<Directive> {
    if (text.isEmpty()) return emptyList()
    return BUDGET_DIRECTIVE.findAll(text).map { match ->
        // The directive is the match minus the whitespace before it, and
        // always ends where the match does (the lookahead is zero-width).
        val directive = match.groups[1]!!.range
        Directive(
            span = ActivationSpan(directive.first, directive.last + 1),
            number = match.groupValues[2],
            suffix = match.groupValues[3],
        )
    }.toList()
}

/** Where each directive in [text] is, without the whitespace before it. */
fun budgetDirectiveSpans(text: String): List<ActivationSpan> = directives(text).map { it.span }

/**
 * The token budget a directive in [text] asks for, or null when there is
 * none. When several appear, the last one wins: it is the user's most recent
 * word on the matter.
 */
fun parseBudgetDirective(text: String): Long? {
    val last = directives(text).lastOrNull() ?: return null
    var n = last.number.toDoubleOrNull() ?: return null
    if (!n.isFinite() || n <= 0) return null
    n *= if (last.suffix.lowercase() == "m") 1_000_000.0 else 1_000.0
    if (n < 1) return null
    return minOf(n, MAX_BUDGET_DIRECTIVE).toLong()
}

/**
 * Whether a directive in this message would be honoured: workflows are on for
 * it, through the message's own words or a standing Ultra. The TUI asks the
 * same question (budgetDirectivesLive in internal/tui/timers.go).
 */
fun budgetDirectivesLive(text: String, ultraActive: Boolean): Boolean {
    if (ultraActive) return true
    return text.contains('+') && workflowRequested(text)
}

/**
 * Every lit range of [text]: the activating phrases, plus the budget
 * directives when [budgets] says they are live. Ordered and non-overlapping.
 * The two kinds never overlap in practice ("+" is in no activating phrase),
 * but they are merged rather than trusted not to, since overlapping ranges
 * would style the same characters twice.
 */
fun workflowInputSpans(text: String, budgets: Boolean): List<ActivationSpan> {
    if (text.isEmpty()) return emptyList()
    val spans = workflowActivationSpans(text) + if (budgets) budgetDirectiveSpans(text) else emptyList()
    if (spans.isEmpty()) return emptyList()
    val sorted = spans.sortedWith(compareBy<ActivationSpan> { it.start }.thenByDescending { it.end })
    val merged = mutableListOf(sorted[0])
    for (span in sorted.drop(1)) {
        val last = merged[merged.size - 1]
        if (span.start <= last.end) {
            if (span.end > last.end) merged[merged.size - 1] = last.copy(end = span.end)
            continue
        }
        merged += span
    }
    return merged
}

/**
 * [splitOnActivation] plus the budget directives, when [budgets] says they are
 * live: every lit range of [text] and the prose between them, in order,
 * covering the whole string exactly once.
 */
fun splitWorkflowInput(text: String, budgets: Boolean): List<ActivationPiece> {
    if (text.isEmpty()) return emptyList()
    val spans = workflowInputSpans(text, budgets)
    if (spans.isEmpty()) return listOf(ActivationPiece(text, false))
    val pieces = mutableListOf<ActivationPiece>()
    var cursor = 0
    for (span in spans) {
        if (span.start > cursor) pieces += ActivationPiece(text.substring(cursor, span.start), false)
        pieces += ActivationPiece(text.substring(span.start, span.end), true)
        cursor = span.end
    }
    if (cursor < text.length) pieces += ActivationPiece(text.substring(cursor), false)
    return pieces
}

/**
 * A token count the way a directive spells it — 500k, 1.5m — as the CLI's
 * compactTokens (internal/acp/workflow.go) prints a run's budget: that is how
 * the user asked for it.
 *
 * The rounding is done on the double the division produces, not on the exact
 * decimal, because that is what the desktop's `toFixed` rounds: 1 005 000 is
 * 1.00499… as a double and both say "1m".
 */
fun compactTokens(n: Long): String = when {
    n >= 1_000_000 -> trimDecimals(fixed(n / 1_000_000.0, 2)) + "m"
    n >= 1_000 -> trimDecimals(fixed(n / 1_000.0, 1)) + "k"
    else -> n.toString()
}

/** `Number.prototype.toFixed`, locale-free. */
private fun fixed(value: Double, digits: Int): String =
    BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString()

private fun trimDecimals(text: String): String =
    if (text.contains('.')) text.trimEnd('0').removeSuffix(".") else text
