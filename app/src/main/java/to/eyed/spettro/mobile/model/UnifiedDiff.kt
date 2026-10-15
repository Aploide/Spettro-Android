package to.eyed.spettro.mobile.model

/**
 * A real line diff for the edit rows: what changed, where, with line
 * numbers — a line-for-line port of the desktop's unifiedDiff.ts.
 *
 * The CLI sends an edit as the whole file before and after the call
 * (oldText absent for a created file), so the numbers are the file's own.
 * The shared head and tail are peeled off first — that alone is the whole
 * diff for nearly every edit an agent makes — and the middle goes through an
 * LCS table. A middle too big to tabulate cheaply is shown as replaced
 * wholesale rather than stalling the transcript; the numbers stay right, only
 * the pairing is coarser.
 */
object UnifiedDiff {
    enum class Kind { CONTEXT, ADDED, REMOVED }

    data class Line(
        val kind: Kind,
        val text: String,
        /** 1-based line in the old file; null on an added line. */
        val oldNo: Int?,
        /** 1-based line in the new file; null on a removed line. */
        val newNo: Int?,
        /** The file's last line, with no newline after it. */
        val noNewline: Boolean = false,
    )

    /** `@@ -oldStart,oldLines +newStart,newLines @@`, 1-based as in git. */
    data class Hunk(
        val oldStart: Int,
        val oldLines: Int,
        val newStart: Int,
        val newLines: Int,
        val lines: List<Line>,
    )

    data class Diff(val hunks: List<Hunk>, val added: Int, val removed: Int) {
        /** Total lines the diff renders, separators not counted. */
        val lineCount: Int get() = hunks.sumOf { it.lines.size }
    }

    /** Cells of LCS table we are willing to fill. */
    private const val MAX_TABLE = 4_000_000L

    private data class Src(val text: String, val noNewline: Boolean)

    private data class Op(val kind: Kind, val line: Src)

    /**
     * Splits a file into lines. A final line without a trailing newline is
     * marked, so "a\nb" and "a\nb\n" differ in their last line the way git says.
     */
    private fun splitLines(text: String?): List<Src> {
        if (text.isNullOrEmpty()) return emptyList()
        val parts = text.split("\n").toMutableList()
        val endsWithNewline = parts.last() == ""
        if (endsWithNewline) parts.removeAt(parts.size - 1)
        return parts.mapIndexed { i, t ->
            Src(
                text = if (t.endsWith("\r")) t.dropLast(1) else t,
                noNewline = !endsWithNewline && i == parts.size - 1,
            )
        }
    }

    /** The edit script for the region between the shared head and tail. */
    private fun middleOps(a: List<Src>, b: List<Src>): List<Op> {
        val n = a.size
        val m = b.size
        val ops = ArrayList<Op>()
        if (n == 0 || m == 0 || n.toLong() * m > MAX_TABLE) {
            a.forEach { ops.add(Op(Kind.REMOVED, it)) }
            b.forEach { ops.add(Op(Kind.ADDED, it)) }
            return ops
        }
        // lcs[i * (m + 1) + j] = LCS length of a[i:] and b[j:].
        val w = m + 1
        val lcs = IntArray((n + 1) * w)
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i * w + j] = if (a[i] == b[j]) {
                    lcs[(i + 1) * w + j + 1] + 1
                } else {
                    maxOf(lcs[(i + 1) * w + j], lcs[i * w + j + 1])
                }
            }
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (a[i] == b[j]) {
                ops.add(Op(Kind.CONTEXT, b[j]))
                i++
                j++
            } else if (lcs[(i + 1) * w + j] >= lcs[i * w + j + 1]) {
                // On a tie, removals go first: a replaced block reads as
                // "these went, these came", not as an interleaving.
                ops.add(Op(Kind.REMOVED, a[i++]))
            } else {
                ops.add(Op(Kind.ADDED, b[j++]))
            }
        }
        while (i < n) ops.add(Op(Kind.REMOVED, a[i++]))
        while (j < m) ops.add(Op(Kind.ADDED, b[j++]))
        return ops
    }

    /**
     * The unified diff of two texts, in hunks with [context] unchanged lines
     * on either side of each change. Changes closer than twice the context
     * share a hunk, as in `diff -u`. Identical texts give no hunks.
     */
    fun of(oldText: String?, newText: String, context: Int = 3): Diff {
        val a = splitLines(oldText)
        val b = splitLines(newText)

        var head = 0
        while (head < a.size && head < b.size && a[head] == b[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++

        val ops = ArrayList<Op>()
        for (k in 0 until head) ops.add(Op(Kind.CONTEXT, b[k]))
        ops.addAll(middleOps(a.subList(head, a.size - tail), b.subList(head, b.size - tail)))
        for (k in b.size - tail until b.size) ops.add(Op(Kind.CONTEXT, b[k]))

        // Number every op once, then cut hunks out of the numbered list.
        val lines = ArrayList<Line>(ops.size)
        var oldNo = 0
        var newNo = 0
        var added = 0
        var removed = 0
        for (op in ops) {
            val o = if (op.kind != Kind.ADDED) ++oldNo else null
            val nn = if (op.kind != Kind.REMOVED) ++newNo else null
            if (op.kind == Kind.ADDED) added++
            if (op.kind == Kind.REMOVED) removed++
            lines.add(Line(op.kind, op.line.text, o, nn, op.line.noNewline))
        }

        val hunks = ArrayList<Hunk>()
        var k = 0
        while (k < lines.size) {
            if (lines[k].kind == Kind.CONTEXT) {
                k++
                continue
            }
            val start = maxOf(0, k - context)
            // Extend over changes until a run of unchanged lines long enough
            // to close the hunk (more than both hunks' context put together).
            var end = k
            var scan = k
            while (scan < lines.size) {
                if (lines[scan].kind != Kind.CONTEXT) {
                    end = scan
                    scan++
                    continue
                }
                var run = 0
                while (scan + run < lines.size && lines[scan + run].kind == Kind.CONTEXT) run++
                if (scan + run >= lines.size || run > 2 * context) break
                scan += run
            }
            val stop = minOf(lines.size, end + 1 + context)
            hunks.add(toHunk(lines.subList(start, stop).toList()))
            k = stop
        }
        return Diff(hunks, added, removed)
    }

    private fun toHunk(lines: List<Line>): Hunk {
        var oldLines = 0
        var newLines = 0
        var oldStart = 0
        var newStart = 0
        for (line in lines) {
            if (line.oldNo != null) {
                if (oldLines == 0) oldStart = line.oldNo
                oldLines++
            }
            if (line.newNo != null) {
                if (newLines == 0) newStart = line.newNo
                newLines++
            }
        }
        // A side with no lines at all only happens when that side is an
        // empty file (created, or emptied): git prints it as `-0,0`.
        return Hunk(oldStart, oldLines, newStart, newLines, lines)
    }
}
