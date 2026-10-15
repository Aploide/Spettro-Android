package to.eyed.spettro.mobile.core

// Error messages a person can act on — a port of the desktop's
// src/shared/humanize.ts, so a phone and the computer it drives say the same
// thing about the same failure.
//
// Errors arrive here from the host's RPC refusals, a provider's 401 body
// relayed by the CLI, a socket that never opened. Printed as they came they
// say what broke to whoever wrote the code ("Couldn't open chat: Internal
// error"), and nothing to the person holding the phone. humanizeError() turns
// any of them into one sentence that says what happened, one that says what
// to do, and at most one action. The raw text is kept for "Show details".

/** The one next step an error offers; a surface that can't perform it shows no button. */
enum class ErrorActionKind { Retry, Restart, Reinstall, Update, Connect, Models }

data class ErrorAction(val kind: ErrorActionKind, val label: String)

data class HumanError(
    /** Short, sentence case, no trailing period: "Your API key was rejected". */
    val title: String,
    /** What to do about it, as a full sentence. */
    val detail: String,
    val action: ErrorAction?,
    /** The message as it arrived, minus routing noise — for "Show details". */
    val raw: String,
    /** False when no pattern matched and [detail] is the raw message itself. */
    val known: Boolean,
) {
    /** "Title. Detail" — one line, for surfaces with room for only one. */
    val sentence: String get() = if (known) "$title. $detail" else detail
}

/** A local model server being probed: refused connections then mean "it isn't running". */
data class HumanizeContext(val endpoint: String? = null, val serverName: String? = null)

/** Exception wrappers prefix their own noise; nobody needs to read it. */
fun stripErrorNoise(message: String): String = message
    .replace(Regex("^Error invoking remote method '[^']+':\\s*"), "")
    .replace(Regex("^(?:[A-Za-z.]*Exception|(?:Acp)?Error):\\s*"), "")
    .trim()

private class Rule(val test: Regex, val build: (MatchResult, HumanizeContext) -> Triple<String, String, ErrorAction?>)

private fun rule(pattern: String, build: (MatchResult, HumanizeContext) -> Triple<String, String, ErrorAction?>) =
    Rule(Regex(pattern, RegexOption.IGNORE_CASE), build)

private fun act(kind: ErrorActionKind, label: String) = ErrorAction(kind, label)

// Order matters: the first match wins, so the specific rules sit above the
// general ones (a 401 from a provider is about the key, not the network).
private val RULES = listOf(
    rule("prompt has no text content") { _, _ ->
        Triple(
            "Add a few words to send with your image",
            "Spettro needs some text alongside an image to know what you want.",
            null,
        )
    },
    rule("\\bEACCES\\b|permission denied.*spawn|spawn.*permission denied") { _, _ ->
        Triple(
            "Spettro’s helper app can’t be started",
            "This computer isn’t allowing it to run. Reinstalling it usually fixes this.",
            act(ErrorActionKind.Reinstall, "Reinstall"),
        )
    },
    rule("\\bspawn\\b.*\\bENOENT\\b|\\bENOENT\\b.*\\bspawn\\b") { _, _ ->
        Triple(
            "Spettro’s helper app is missing",
            "It may have been moved or deleted. Reinstall it to keep going.",
            act(ErrorActionKind.Reinstall, "Reinstall"),
        )
    },
    rule("keeps stopping") { _, _ ->
        Triple(
            "Spettro keeps stopping",
            "Its engine quit several times in a row. The details below may say why.",
            act(ErrorActionKind.Restart, "Try again"),
        )
    },
    rule("quit before answering") { _, _ ->
        Triple(
            "That file doesn’t seem to be Spettro",
            "It quit as soon as it started. Choose another file, or install Spettro.",
            act(ErrorActionKind.Reinstall, "Install Spettro"),
        )
    },
    rule("exited(?: unexpectedly)?\\s*\\((?:exit )?code (-?\\d+)\\)|\\bexit(?:ed)? (?:with )?(?:code|status) (-?\\d+)") { _, _ ->
        Triple(
            "Spettro stopped unexpectedly",
            "Its engine quit. Starting it again usually helps.",
            act(ErrorActionKind.Restart, "Restart"),
        )
    },
    rule("malformed initialize|missing sessionId|did not answer the handshake|unexpected (?:response|reply)") { _, _ ->
        Triple(
            "Spettro’s helper app didn’t answer as expected",
            "It may be out of date. Updating it usually fixes this.",
            act(ErrorActionKind.Update, "Update"),
        )
    },
    rule("no API endpoint configured|no (?:active )?(?:provider|model) (?:is )?(?:configured|selected|set)") { _, _ ->
        Triple(
            "No model is connected",
            "Connect a model in Settings › Models & providers, then try again.",
            act(ErrorActionKind.Connect, "Connect a model"),
        )
    },
    rule("unknown model|model[^.\\n]{0,40}not (?:found|available|supported)|no such model|model_not_found") { _, _ ->
        Triple(
            "That model isn’t available",
            "The provider may have removed it. Pick another model and try again.",
            act(ErrorActionKind.Models, "Choose a model"),
        )
    },
    rule(
        "\\b401\\b|\\b403\\b|invalid[ _-]?(?:api[ _-]?)?key|incorrect api key|api key (?:is )?(?:invalid|missing|not valid)" +
            "|unauthori[sz]ed|authentication[_ ]?error|forbidden",
    ) { _, _ ->
        Triple(
            "Your API key was rejected",
            "Check the key in Settings › Models & providers, or connect another provider.",
            act(ErrorActionKind.Connect, "Open Models & providers"),
        )
    },
    rule("\\b529\\b|overloaded") { _, _ ->
        Triple(
            "The model provider is overloaded",
            "It’s turning requests away right now. Nothing was lost — try again in a moment.",
            act(ErrorActionKind.Retry, "Try again"),
        )
    },
    rule("\\b429\\b|rate[ _-]?limit|too many requests|quota|insufficient[_ ]credits|credit balance") { match, _ ->
        val credits = Regex("quota|credit", RegexOption.IGNORE_CASE).containsMatchIn(match.value)
        if (credits) {
            Triple(
                "You’re out of credits with this provider",
                "Add credits in the provider’s console, or switch to another model.",
                act(ErrorActionKind.Models, "Choose a model"),
            )
        } else {
            Triple(
                "You’ve hit the provider’s rate limit",
                "Wait a minute, then try again.",
                act(ErrorActionKind.Retry, "Try again"),
            )
        }
    },
    rule(
        "\\b(?:ECONNREFUSED|ECONNRESET|ENOTFOUND|EAI_AGAIN|ETIMEDOUT|ENETUNREACH|EHOSTUNREACH)\\b" +
            "|network (?:is )?(?:down|unreachable|error)|offline|could not resolve host|getaddrinfo|fetch failed|socket hang up",
    ) { match, ctx ->
        val endpoint = ctx.endpoint
        if (endpoint != null) {
            val where = endpoint.replace(Regex("^https?://"), "").replace(Regex("/.*$"), "")
            Triple(
                "Nothing is running at $where",
                ctx.serverName?.let { "Is $it open? Start it, then try again." }
                    ?: "Start your local model server, then try again.",
                act(ErrorActionKind.Retry, "Try again"),
            )
        } else {
            val refused = Regex("ECONNREFUSED|ECONNRESET", RegexOption.IGNORE_CASE).containsMatchIn(match.value)
            Triple(
                if (refused) "Spettro couldn’t connect" else "You appear to be offline",
                "Check your internet connection, then try again.",
                act(ErrorActionKind.Retry, "Try again"),
            )
        }
    },
    rule("timed out|timeout") { _, _ ->
        Triple(
            "That took too long",
            "Spettro gave up waiting. Check your connection, then try again.",
            act(ErrorActionKind.Retry, "Try again"),
        )
    },
    rule("^(?:internal error|invalid params|method not found)\\.?$") { _, _ ->
        Triple(
            "Something went wrong inside Spettro",
            "Try again. If it keeps happening, the details below can help.",
            act(ErrorActionKind.Retry, "Try again"),
        )
    },
)

/** The longest raw message a fallback puts on screen. */
private const val FALLBACK_LIMIT = 280

/** [message] as one sentence of what happened, one of what to do, and at most one action. */
fun humanizeError(message: String?, ctx: HumanizeContext = HumanizeContext()): HumanError {
    val raw = stripErrorNoise(message.orEmpty())
    for (rule in RULES) {
        val match = rule.test.find(raw) ?: continue
        val (title, detail, action) = rule.build(match, ctx)
        return HumanError(title, detail, action, raw, known = true)
    }
    // Unknown: the message itself is the best detail there is — its first
    // line, cut to something that fits.
    val firstLine = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    val detail = when {
        // Nothing to show behind "Show details", so don't point there.
        firstLine.isEmpty() -> "Try again. If it keeps happening, check Spettro on your computer."
        firstLine.length > FALLBACK_LIMIT -> firstLine.take(FALLBACK_LIMIT - 1) + "…"
        else -> firstLine
    }
    return HumanError("Something went wrong", asSentence(detail), null, raw, known = false)
}

/**
 * [message] as one line for the transcript (the desktop's humanSentence): a
 * recognised failure's title and what to do; otherwise the message itself,
 * as a sentence.
 */
fun humanSentence(message: String?, ctx: HumanizeContext = HumanizeContext()): String {
    val human = humanizeError(message, ctx)
    return if (human.known) "${human.title}. ${human.detail}" else human.detail
}

private fun asSentence(text: String): String {
    val t = text.replaceFirstChar { it.uppercaseChar() }
    return if (t.last() in ".!?…)") t else "$t."
}
