package to.eyed.spettro.mobile.model

/**
 * The string surgery behind a tool row that isn't about one call — a port of
 * the shared helpers in the desktop's toolPresentation.ts. The per-call
 * derivations live on [ToolCallItem].
 */
object ToolPresentation {
    /** Longest argument a row shows before it is cut in the middle. */
    const val ROW_ARGUMENT_MAX = 64

    /**
     * Shortens text by cutting out its middle: "src/components/…/SaveButton.tsx".
     * Both ends of a path or a command carry the meaning, so neither is the
     * one to drop. Counts code points, so it never splits a glyph; the odd
     * character goes to the end — a file's name outranks its folder.
     */
    fun middleTruncate(text: String, max: Int): String {
        val chars = text.codePoints().toArray()
        if (chars.size <= max) return text
        if (max <= 1) return "…"
        val keep = max - 1
        val head = keep / 2
        val tail = keep - head
        fun str(points: IntArray) = String(points, 0, points.size)
        return str(chars.copyOfRange(0, head)) + "…" + str(chars.copyOfRange(chars.size - tail, chars.size))
    }

    /** The last path component ("src/a/b.ts" → "b.ts"); the path itself when it has none. */
    fun baseName(path: String): String =
        path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path

    /**
     * Collapses an absolute path to its last three components. With the
     * chat's project folder, a file inside it is named from there instead
     * ("src/app.ts") — the last three of an absolute path can start anywhere
     * and read like a path in the project that isn't one.
     */
    fun shortPath(path: String, projectPath: String? = null): String {
        if (!projectPath.isNullOrEmpty()) {
            val root = projectPath.trimEnd('/')
            if (root.isNotEmpty() && path.startsWith("$root/")) return path.substring(root.length + 1)
        }
        val parts = path.split("/").filter { it.isNotEmpty() }
        if (parts.size <= 3) return path
        return parts.takeLast(3).joinToString("/")
    }

    private val EXIT_STATUS = Regex("""\[?exit status (\d+)]?$""")

    /** The `[exit status N]` a failed shell command's output ends with; null when there is none. */
    fun exitCode(output: String): Int? {
        val end = output.trimEnd()
        return EXIT_STATUS.find(end.takeLast(128))?.groupValues?.get(1)?.toIntOrNull()
    }
}
