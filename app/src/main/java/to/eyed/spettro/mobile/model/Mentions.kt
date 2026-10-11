package to.eyed.spettro.mobile.model

/**
 * @-mentions in the composer — a port of the desktop's mentions.ts: finding
 * the "@query" under the caret, ranking the project's files against it, and
 * keeping track of which mentions are still in the text. Pure, so the parts
 * that decide *what* gets sent are tested without a screen: a menu that
 * looks right but inserts the wrong path is the kind of bug a screenshot
 * never shows.
 */
object Mentions {
    data class Query(val start: Int, val query: String)

    data class Inserted(val text: String, val caret: Int)

    /**
     * The "@query" the caret is in, if any: an @ at the start of the text or
     * after whitespace, then no whitespace up to the caret. An e-mail
     * address ("me@host") is not a mention.
     */
    fun at(text: String, caret: Int): Query? {
        var i = caret - 1
        while (i >= 0) {
            val ch = text[i]
            if (ch == '@') {
                if (i > 0 && !text[i - 1].isWhitespace()) return null
                return Query(i, text.substring(i + 1, caret))
            }
            if (ch.isWhitespace()) return null
            i--
        }
        return null
    }

    /**
     * The draft with the "@query" at [start] replaced by [path], and where
     * the caret goes after it (past the space that closes the mention).
     */
    fun insert(text: String, start: Int, caret: Int, path: String): Inserted {
        val after = text.substring(caret)
        val spacer = if (after.startsWith(" ")) "" else " "
        val inserted = "@$path$spacer"
        return Inserted(text.substring(0, start) + inserted + after, start + inserted.length + if (spacer.isEmpty()) 1 else 0)
    }

    private val BOUNDARY = Regex("[/_\\-. ]")

    /**
     * How well [path] matches [query], or -1 when it doesn't: every query
     * character must appear in order (case-insensitive). Matches in the file
     * name beat matches spread over folders, a run of adjacent characters
     * beats scattered ones, and a match at the start of a word beats one
     * inside it.
     */
    fun score(path: String, query: String): Double {
        if (query.isEmpty()) return 0.0
        val p = path.lowercase()
        val q = query.lowercase()
        val nameStart = p.lastIndexOf('/') + 1
        // A plain substring of the name is the strongest signal there is.
        val inName = p.indexOf(q, nameStart)
        if (inName >= 0) return 1000 - (inName - nameStart) * 2.0 - (p.length - nameStart) * 0.1
        val inPath = p.indexOf(q)
        if (inPath >= 0) return 700 - inPath * 0.5 - p.length * 0.05

        var score = 0.0
        var pi = 0
        var run = 0
        for (ch in q) {
            val at = p.indexOf(ch, pi)
            if (at < 0) return -1.0
            val boundary = at == 0 || BOUNDARY.matches(p[at - 1].toString()) || path[at] in 'A'..'Z'
            run = if (at == pi && pi > 0) run + 1 else 0
            score += 10 + run * 8 + (if (boundary) 12 else 0) + (if (at >= nameStart) 6 else 0)
            pi = at + 1
        }
        return score - p.length * 0.05
    }

    /** The best [limit] files for [query]; with no query, the shortest paths first (the top of the project). */
    fun rank(files: List<String>, query: String, limit: Int = 8): List<String> {
        if (query.isEmpty()) {
            return files.sortedWith(compareBy<String> { it.length }.thenBy { it }).take(limit)
        }
        return files.asSequence()
            .map { it to score(it, query) }
            .filter { it.second >= 0 }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private val ENDS_MENTION = Regex("[\\s,;:!?)\\]}'\"`.]")

    /**
     * The mentions still written in [text] as "@path" (each once). A mention
     * the user deleted or edited away is no longer sent as a file.
     */
    fun live(text: String, mentions: List<String>): List<String> =
        mentions.distinct().filter { isWritten(text, "@$it") }

    private fun isWritten(text: String, token: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(token, from)
            if (at < 0) return false
            val after = text.getOrNull(at + token.length)
            if (after == null || ENDS_MENTION.matches(after.toString())) return true
            from = at + 1
        }
    }
}
