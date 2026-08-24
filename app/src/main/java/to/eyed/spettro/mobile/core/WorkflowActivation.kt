package to.eyed.spettro.mobile.core

/**
 * Which phrases turn workflows on for a turn.
 *
 * A direct port of `workflowActivationRes` and `WorkflowActivationSpans` in the
 * CLI's internal/agent/workflow.go, and it has to stay one: the highlight the
 * user sees while typing is only honest if it is driven by the same match that
 * decides whether the workflow tool is actually injected. A UI that lit up
 * "use a workflow" while the CLI ignored it would be promising a mode the run
 * never enters — which is worse than not highlighting at all.
 *
 * Every pattern is a phrase that only makes sense as a request for this
 * feature. "our deploy workflow" and ".github/workflows" must stay quiet.
 *
 * The Go patterns use `\b` and `\w` throughout. Java's defaults for both are
 * ASCII-only, exactly like Go's `regexp` and JavaScript's, so the translation
 * is literal — no `UNICODE_CHARACTER_CLASS`, which would widen `\w` and make
 * the three front-ends disagree on accented input.
 */

/** The shorthand. Spelled out rather than interpolated so a search for the
 *  word finds this file. */
const val WORKFLOW_KEYWORD = "ultracode"

private val ACTIVATION_PATTERNS: List<Regex> = listOf(
    Regex("""\bultracode\b""", RegexOption.IGNORE_CASE),
    // "use a workflow", "write a multi-agent workflow", "set up a workflow".
    // An indefinite article only: "run the workflow" almost always means a CI
    // job or an already-named saved script.
    Regex(
        """\b(?:use|using|run|write|author|make|create|build|set ?up|start|launch|kick off|""" +
            """do this as|do it as)\s+(?:a|an|another)\s+(?:new\s+)?""" +
            """(?:multi[- ]?agent\s+|orchestration\s+)?workflow\b""",
        RegexOption.IGNORE_CASE,
    ),
    // "with workflows", "via workflows"
    Regex("""\b(?:use|using|run|with|via)\s+workflows\b""", RegexOption.IGNORE_CASE),
    // an explicit reference to the tool itself
    Regex("""\bworkflow tool\b""", RegexOption.IGNORE_CASE),
    // "fan this out across sub-agents"
    Regex(
        """\bfan\s+(?:this|that|it|them|the \w+)?\s*out\s+(?:across|over|to|into)\s+""" +
            """(?:\w+\s+){0,2}(?:sub-?)?agents?\b""",
        RegexOption.IGNORE_CASE,
    ),
    // "orchestrate this with subagents"
    Regex(
        """\borchestrate\s+(?:\w+\s+){0,3}(?:with|using|across|over)\s+""" +
            """(?:\w+\s+){0,2}(?:sub-?)?agents?\b""",
        RegexOption.IGNORE_CASE,
    ),
    // "multi-agent orchestration"
    Regex(
        """\bmulti[- ]?agent\s+(?:orchestration|workflow|pipeline|run)\b""",
        RegexOption.IGNORE_CASE,
    ),
)

/** A half-open [start, end) range of the text that activates workflows. */
data class ActivationSpan(val start: Int, val end: Int)

/**
 * Every activating phrase in [text], ordered and non-overlapping.
 *
 * The patterns overlap by design — "use a workflow" and "workflow tool" both
 * match inside "use a workflow tool" — so they are merged before being
 * returned. A renderer handed overlapping ranges would style the same
 * characters twice and nest its own markup inside itself.
 */
fun workflowActivationSpans(text: String): List<ActivationSpan> {
    if (text.isEmpty()) return emptyList()

    val found = mutableListOf<ActivationSpan>()
    for (pattern in ACTIVATION_PATTERNS) {
        for (match in pattern.findAll(text)) {
            if (match.value.isEmpty()) continue
            found += ActivationSpan(match.range.first, match.range.last + 1)
        }
    }
    if (found.isEmpty()) return emptyList()

    // Earliest first; on a tie the longer span leads, so the merge below
    // absorbs the shorter one instead of the other way round.
    found.sortWith(compareBy<ActivationSpan> { it.start }.thenByDescending { it.end })

    val merged = mutableListOf(found[0])
    for (span in found.drop(1)) {
        val last = merged[merged.size - 1]
        if (span.start <= last.end) {
            if (span.end > last.end) merged[merged.size - 1] = last.copy(end = span.end)
            continue
        }
        merged += span
    }
    return merged
}

/** True when this text opts the turn into workflows. */
fun workflowRequested(text: String): Boolean = workflowActivationSpans(text).isNotEmpty()

/**
 * True when the user typed the keyword itself, which the CLI treats as a
 * standing yes to spending on a run rather than a request to be confirmed.
 * Kept distinct from [workflowRequested] for the same reason the CLI keeps
 * `WorkflowPreapproved` distinct: the difference decides whether the run stops
 * to ask.
 */
fun workflowPreapproved(text: String): Boolean =
    ACTIVATION_PATTERNS[0].containsMatchIn(text)

/**
 * A piece of the text for rendering: the activating phrases and the prose
 * between them, in order, covering the whole string exactly once.
 *
 * Currently exercised only by the tests. The composer styles the spans in place
 * through a `VisualTransformation` and has no need to cut the string up, and
 * sent messages are drawn plain on purpose — but this is half of the matcher's
 * ported API, its behaviour is pinned against the same vectors as the rest, and
 * any surface that renders activation as separate runs rather than styled spans
 * will want it.
 */
data class ActivationPiece(val text: String, val active: Boolean)

fun splitOnActivation(text: String): List<ActivationPiece> {
    val spans = workflowActivationSpans(text)
    if (spans.isEmpty()) {
        return if (text.isEmpty()) emptyList() else listOf(ActivationPiece(text, false))
    }

    val pieces = mutableListOf<ActivationPiece>()
    var cursor = 0
    for (span in spans) {
        if (span.start > cursor) {
            pieces += ActivationPiece(text.substring(cursor, span.start), false)
        }
        pieces += ActivationPiece(text.substring(span.start, span.end), true)
        cursor = span.end
    }
    if (cursor < text.length) pieces += ActivationPiece(text.substring(cursor), false)
    return pieces
}
