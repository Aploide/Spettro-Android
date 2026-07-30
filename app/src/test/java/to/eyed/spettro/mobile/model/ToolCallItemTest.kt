package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

class ToolCallItemTest {

    private fun item(
        title: String,
        kind: String? = null,
        argsJSON: String? = null,
        output: String = "",
        locations: List<String> = emptyList(),
        diffs: List<ToolCallItem.ToolDiff> = emptyList(),
    ) = ToolCallItem(
        id = "t1", title = title, kind = kind, status = AcpToolStatus.COMPLETED,
        output = output, diffs = diffs, locations = locations, argsJSON = argsJSON,
    )

    // MARK: Title parsing

    @Test
    fun bashTitleWithAgentPrefixShowsCommand() {
        val t = item("""[agent#1] bash {"command":"ls -la"}""", kind = "execute")
        assertEquals("Terminal", t.displayName)
        assertEquals("[agent#1] ls -la", t.displayDetail)
        assertEquals(ToolIcon.EXECUTE, t.icon)
    }

    @Test
    fun argsJsonPreferredOverTruncatedTitleArgs() {
        // The CLI truncates inline args at 120 chars, leaving invalid JSON.
        val t = item(
            """bash {"command":"echo somethin""",
            kind = "execute",
            argsJSON = """{"command":"echo something very long that got truncated"}""",
        )
        assertEquals("echo something very long that got truncated", t.displayDetail)
    }

    @Test
    fun readToolShowsShortPath() {
        val t = item(
            """read {"path":"/Users/me/project/src/main/App.kt"}""",
            kind = "read",
        )
        assertEquals("Read", t.displayName)
        assertEquals("src/main/App.kt", t.displayDetail)
    }

    @Test
    fun searchPatternCombinesWithPath() {
        val t = item(
            """grep {"pattern":"TODO","path":"/Users/me/project/src"}""",
            kind = "search",
        )
        assertEquals("Search", t.displayName)
        assertEquals("TODO in me/project/src", t.displayDetail)
    }

    @Test
    fun lsIsListedAsList() {
        val t = item("""ls {"path":"/a"}""", kind = "search")
        assertEquals("List", t.displayName)
    }

    @Test
    fun titleWithoutArgsUsesLocationsForDetail() {
        val t = item("read", kind = "read", locations = listOf("/very/deep/nested/dir/file.txt"))
        assertEquals("nested/dir/file.txt", t.displayDetail)
    }

    @Test
    fun unknownToolShowsCompactKeyValuePairsNeverRawJson() {
        val t = item("""mytool {"count":3,"flag":true,"ratio":1.5}""")
        assertEquals("Mytool", t.displayName)
        // Sorted keys, string/int/bool only (the 1.5 double is skipped, like iOS).
        assertEquals("count: 3, flag: true", t.displayDetail)
    }

    @Test
    fun newlinesInDetailAreFlattened() {
        val t = item("""bash {"command":"ls\ncd /tmp"}""", kind = "execute")
        assertEquals("ls ⏎ cd /tmp", t.displayDetail)
    }

    @Test
    fun plainTitleWithoutBraceOrArgs() {
        val t = item("Tool call")
        assertEquals("", t.displayDetail) // name == title -> empty detail
        assertEquals("Tool Call", t.displayName)
    }

    // MARK: Sub-agent extraction

    @Test
    fun subAgentCallFromArgs() {
        val t = item(
            """agent {"agent":"explore","task":"find the bug"}""",
            kind = "think",
        )
        assertEquals("Agent", t.displayName)
        assertEquals(ToolCallItem.SubAgentCall("explore", "find the bug"), t.subAgentCall)
        assertEquals("explore: find the bug", t.displayDetail)
    }

    @Test
    fun subAgentCallFromTitleFormWithoutArgs() {
        val t = item("agent explore: check the tests")
        assertEquals(ToolCallItem.SubAgentCall("explore", "check the tests"), t.subAgentCall)
    }

    @Test
    fun nonAgentToolHasNoSubAgentCall() {
        assertNull(item("""bash {"command":"ls"}""", kind = "execute").subAgentCall)
        // "agentic" does not start with "agent " and is not "agent".
        assertNull(item("agentic-search").subAgentCall?.task)
    }

    @Test
    fun subAgentResultFromValidJson() {
        val t = item(
            """agent {"agent":"explore"}""",
            output = """{"agent":"explore","status":"done","summary":"All good"}""",
        )
        assertEquals(ToolCallItem.SubAgentResult("done", "All good"), t.subAgentResult)
    }

    @Test
    fun subAgentResultSalvagedFromTruncatedJson() {
        val t = item(
            """agent {"agent":"explore"}""",
            output = """{"agent":"explore","status":"ok","summary":"Found the bug in the pars""",
        )
        assertEquals(
            ToolCallItem.SubAgentResult("ok", "Found the bug in the pars"),
            t.subAgentResult,
        )
    }

    @Test
    fun subAgentResultUnescapesSalvagedText() {
        val t = item(
            """agent {"agent":"x"}""",
            output = """{"summary":"line one\nline two\t(tab)""",
        )
        assertEquals("line one\nline two\t(tab)", t.subAgentResult?.summary)
    }

    @Test
    fun subAgentResultNullWithoutOutput() {
        assertNull(item("""agent {"agent":"x"}""").subAgentResult)
    }

    // MARK: Paths and diffs

    @Test
    fun shortPathKeepsShortPathsIntact() {
        assertEquals("/a/b/c", ToolCallItem.shortPath("/a/b/c"))
        assertEquals("b/c/d", ToolCallItem.shortPath("/a/b/c/d"))
        assertEquals("relative.txt", ToolCallItem.shortPath("relative.txt"))
    }

    @Test
    fun diffStatCountsOnlyChangedLines() {
        val diff = ToolCallItem.ToolDiff(
            path = "/f",
            oldText = "a\nb\nc\nd",
            newText = "a\nX\nY\nc\nd",
        )
        // Common prefix "a", common suffix "c","d": old changed = [b], new = [X, Y].
        assertEquals(Pair(listOf("b"), listOf("X", "Y")), diff.changedLines)
        val t = item("edit", diffs = listOf(diff))
        assertEquals(ToolCallItem.DiffStat(added = 2, removed = 1), t.diffStat)
    }

    @Test
    fun pureInsertionDoesNotCountWholeFile() {
        val diff = ToolCallItem.ToolDiff(
            path = "/f",
            oldText = "a\nb",
            newText = "a\nNEW\nb",
        )
        assertEquals(Pair(emptyList<String>(), listOf("NEW")), diff.changedLines)
        assertEquals(
            ToolCallItem.DiffStat(added = 1, removed = 0),
            item("edit", diffs = listOf(diff)).diffStat,
        )
    }

    @Test
    fun newFileCountsAllLinesAdded() {
        val diff = ToolCallItem.ToolDiff(path = "/f", oldText = null, newText = "1\n2\n3")
        assertEquals(
            ToolCallItem.DiffStat(added = 3, removed = 0),
            item("write", diffs = listOf(diff)).diffStat,
        )
    }

    @Test
    fun noDiffsMeansNoStat() {
        assertNull(item("bash").diffStat)
    }

    // MARK: Icons

    @Test
    fun iconMapping() {
        assertEquals(ToolIcon.READ, ToolIcon.from("read"))
        assertEquals(ToolIcon.EDIT, ToolIcon.from("edit"))
        assertEquals(ToolIcon.DELETE, ToolIcon.from("delete"))
        assertEquals(ToolIcon.MOVE, ToolIcon.from("move"))
        assertEquals(ToolIcon.SEARCH, ToolIcon.from("search"))
        assertEquals(ToolIcon.EXECUTE, ToolIcon.from("execute"))
        assertEquals(ToolIcon.THINK, ToolIcon.from("think"))
        assertEquals(ToolIcon.FETCH, ToolIcon.from("fetch"))
        assertEquals(ToolIcon.SWITCH_MODE, ToolIcon.from("switch_mode"))
        assertEquals(ToolIcon.OTHER, ToolIcon.from(null))
        assertEquals(ToolIcon.OTHER, ToolIcon.from("mystery"))
    }

    // MARK: Transcript ids

    @Test
    fun transcriptItemIdsAreStable() {
        val msg = ChatMessage(id = "abc", role = ChatMessage.Role.User, text = "hi")
        assertEquals("msg-abc", TranscriptItem.Message(msg).id)
        assertEquals("tool-t1", TranscriptItem.Tool(item("x")).id)
    }
}
