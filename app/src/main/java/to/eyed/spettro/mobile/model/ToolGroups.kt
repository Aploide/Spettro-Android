package to.eyed.spettro.mobile.model

import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/**
 * Folding runs of look-around calls into one line — a port of the desktop's
 * toolGroups.ts.
 *
 * An agent getting its bearings reads five files and greps twice before it
 * does anything, and as seven rows that preamble pushes the edit it led to
 * off the screen. Claude Code says "Read 5 files, searched 2 patterns" and
 * lets you open it; this is that. Only *finished* reads and searches fold —
 * a call still running stays a row of its own, so the live edge of the turn
 * is always visible — and anything between them (a message, an edit, a
 * command) ends the run, so the fold never hides the order things happened in.
 */
sealed class DisplayRow {
    abstract val id: String

    /** A row of the folded transcript, drawn as it is. */
    data class Row(val row: TranscriptRow) : DisplayRow() {
        override val id: String get() = row.id
    }

    /**
     * A folded run of reads and searches. Its id is the first call's, so the
     * row keeps its identity (and its open/closed state) as more calls join it.
     */
    data class Tools(override val id: String, val tools: List<ToolCallItem>) : DisplayRow()
}

/**
 * The id of the newest of the user's own messages in [rows], or null. When
 * it changes the user has just sent something (or steered), and whatever
 * they were reading, the transcript goes to its end to show it and the
 * answer — the desktop's ChatView re-pins on the same signal.
 */
fun newestUserMessageId(rows: List<DisplayRow>): String? = rows.lastOrNull { row ->
    val item = ((row as? DisplayRow.Row)?.row as? TranscriptRow.Item)?.item
    item is TranscriptItem.Message && item.message.role == ChatMessage.Role.User
}?.id

object ToolGroups {
    /** Calls a group never holds fewer of: one read is just a row. */
    private const val MIN_GROUP = 2

    private val GROUPED_VERBS = setOf("Read", "Search", "List")

    /** A finished read, search or listing that is nothing else besides. */
    fun isGroupable(tool: ToolCallItem): Boolean {
        if (tool.status != AcpToolStatus.COMPLETED) return false
        if (tool.kind != "read" && tool.kind != "search") return false
        if (tool.images.isNotEmpty()) return false
        if (tool.subAgentCall != null) return false
        return tool.displayName in GROUPED_VERBS
    }

    /** Folds consecutive groupable tool rows (two or more) into one group row. */
    fun fold(rows: List<TranscriptRow>): List<DisplayRow> {
        val out = ArrayList<DisplayRow>(rows.size)
        val run = ArrayList<TranscriptRow.Item>()
        fun flush() {
            if (run.size >= MIN_GROUP) {
                out.add(DisplayRow.Tools("tools-${run[0].id}", run.map { (it.item as TranscriptItem.Tool).tool }))
            } else {
                run.forEach { out.add(DisplayRow.Row(it)) }
            }
            run.clear()
        }
        for (row in rows) {
            val item = (row as? TranscriptRow.Item)?.item
            if (item is TranscriptItem.Tool && isGroupable(item.tool)) {
                run.add(row)
                continue
            }
            flush()
            out.add(DisplayRow.Row(row))
        }
        flush()
        return out
    }

    /**
     * "Read 5 files", "Searched 3 patterns", or both: "Read 2 files, searched
     * 1 pattern". Clauses follow the order their first call happened in.
     */
    fun summary(tools: List<ToolCallItem>): String {
        val counts = LinkedHashMap<String, Int>()
        for (tool in tools) counts[tool.displayName] = (counts[tool.displayName] ?: 0) + 1
        val text = counts.entries.joinToString(", ") { (verb, n) ->
            val plural = if (n == 1) "" else "s"
            when (verb) {
                "Read" -> "read $n file$plural"
                "Search" -> "searched $n pattern$plural"
                else -> "listed $n folder$plural"
            }
        }
        return text.replaceFirstChar { it.uppercase() }
    }
}
