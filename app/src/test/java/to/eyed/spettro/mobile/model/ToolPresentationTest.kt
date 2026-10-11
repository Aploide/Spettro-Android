package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/** Ported from the desktop's toolPresentation.test.ts (the row's verb, argument and note). */
class ToolPresentationTest {

    private fun tool(
        title: String,
        kind: String?,
        args: String? = null,
        status: AcpToolStatus = AcpToolStatus.COMPLETED,
        output: String = "",
        denied: Boolean = false,
    ) = ToolCallItem(id = "t", title = title, kind = kind, status = status, output = output, argsJSON = args, denied = denied)

    private fun verb(title: String, kind: String) = tool(title, kind).displayName

    @Test
    fun namesAToolByItsAcpKind() {
        assertEquals("Bash", verb("bash {}", "execute"))
        assertEquals("Read", verb("file-read {}", "read"))
        assertEquals("Search", verb("grep {}", "search"))
        assertEquals("Search", verb("glob {}", "search"))
        assertEquals("Fetch", verb("web-fetch {}", "fetch"))
    }

    @Test
    fun tellsTheMembersOfAKindApart() {
        assertEquals("Write", verb("file-write {}", "edit"))
        assertEquals("Edit", verb("file-edit {}", "edit"))
        assertEquals("List", verb("ls {}", "search"))
        assertEquals("Web search", verb("web-search {}", "fetch"))
        assertEquals("Terminal", verb("pty-start {}", "execute"))
        assertEquals("View", verb("view-image {}", "read"))
        assertEquals("Stop job", verb("job-kill {}", "execute"))
    }

    @Test
    fun tellsAThinkingAgentCallFromOtherThinkingCalls() {
        assertEquals("Agent", verb("agent code#1: x", "think"))
        assertEquals("Todos", verb("todo-write {}", "think"))
        assertEquals("Think", verb("goal-complete {}", "think"))
    }

    @Test
    fun capitalisesAnUnknownToolsOwnName() {
        assertEquals("Mcp-github_search", verb("mcp-github_search {}", "other"))
    }

    @Test
    fun middleTruncateKeepsBothEnds() {
        assertEquals("npm test", ToolPresentation.middleTruncate("npm test", 20))
        val cut = ToolPresentation.middleTruncate("src/components/settings/forms/SaveButton.tsx", 20)
        assertEquals(20, cut.codePointCount(0, cut.length))
        assertTrue(cut.startsWith("src/compo"))
        assertTrue(cut.endsWith("Button.tsx"))
        assertTrue("…" in cut)
        assertEquals("🙂🙂…🙂🙂", ToolPresentation.middleTruncate("🙂".repeat(10), 5))
    }

    @Test
    fun rowArgumentShowsWhatTheCallWasAbout() {
        assertEquals("SaveButton.tsx", tool("file-read {}", "read", """{"path":"/home/u/app/src/components/SaveButton.tsx"}""").rowArgument)
        assertEquals("npm test", tool("bash {}", "execute", """{"command":"npm test"}""").rowArgument)
        assertEquals("cd web …", tool("bash {}", "execute", """{"command":"cd web\nnpm test"}""").rowArgument)
        assertEquals("SaveButton in src", tool("grep {}", "search", """{"pattern":"SaveButton","path":"/home/u/app/src"}""").rowArgument)
        assertEquals("https://example.com", tool("[code#3] web-fetch {}", "fetch", """{"url":"https://example.com"}""").rowArgument)
    }

    @Test
    fun rowArgumentCutsALongCommandInTheMiddle() {
        val long = "npm run build -- --filter ${"x".repeat(100)} --verbose"
        val text = tool("bash {}", "execute", """{"command":"$long"}""").rowArgument
        assertEquals(ToolPresentation.ROW_ARGUMENT_MAX, text.codePointCount(0, text.length))
        assertTrue(text.endsWith("--verbose"))
    }

    @Test
    fun theAskUserRowReadsAskAndTheQuestion() {
        val t = tool("Ask the user", "other", """{"questions":[{"header":"Filename","question":"Which filename should I use?"}]}""")
        assertEquals("Ask", t.displayName)
        assertEquals("Which filename should I use?", t.rowArgument)
    }

    @Test
    fun rowMetaSaysWhatCameBack() {
        assertEquals("3 lines", tool("file-read {}", "read", output = "a\nb\nc\n").rowMeta)
        assertEquals("2 results", tool("grep {}", "search", output = "a.ts:1\nb.ts:2").rowMeta)
        assertEquals("no results", tool("grep {}", "search", output = "No matches found").rowMeta)
        assertEquals("exit 1", tool("bash {}", "execute", status = AcpToolStatus.FAILED, output = "FAIL src/a.test.ts\n[exit status 1]").rowMeta)
        assertEquals("failed", tool("bash {}", "execute", status = AcpToolStatus.FAILED, output = "killed").rowMeta)
        assertNull(tool("bash {}", "execute", output = "ok").rowMeta)
        assertEquals("denied", tool("file-write {}", "edit", status = AcpToolStatus.FAILED, denied = true).rowMeta)
        assertNull(tool("file-read {}", "read", status = AcpToolStatus.IN_PROGRESS, output = "a\nb").rowMeta)
    }

    @Test
    fun exitCodeReadsTheEndOfTheOutput() {
        assertEquals(2, ToolPresentation.exitCode("boom\n[exit status 2]\n"))
        assertNull(ToolPresentation.exitCode("all good"))
    }

    @Test
    fun shortPathNamesAFileInTheProjectFromThere() {
        assertEquals("three/four/five.ts", ToolPresentation.shortPath("/one/two/three/four/five.ts"))
        assertEquals("src/a.ts", ToolPresentation.shortPath("src/a.ts"))
        assertEquals("hello.txt", ToolPresentation.shortPath("/tmp/sd-live/WP10/proj/hello.txt", "/tmp/sd-live/WP10/proj"))
        assertEquals("src/deep/x.ts", ToolPresentation.shortPath("/w/acme/src/deep/x.ts", "/w/acme/"))
        assertEquals("a/b/c.conf", ToolPresentation.shortPath("/etc/a/b/c.conf", "/w/acme"))
    }

    @Test
    fun diffStatCountsARealDiff() {
        val t = ToolCallItem(
            id = "e", title = "file-edit {}", kind = "edit", status = AcpToolStatus.COMPLETED,
            diffs = listOf(ToolCallItem.ToolDiff("a", "one\nkeep\nkeep\nkeep\ntwo\n", "ONE\nkeep\nkeep\nkeep\nTWO\n")),
        )
        assertEquals(ToolCallItem.DiffStat(added = 2, removed = 2), t.diffStat)
    }

    @Test
    fun shellCommandIsOnlyForExecute() {
        assertEquals("npm test", tool("bash {}", "execute", """{"command":"  npm test "}""").shellCommand)
        assertNull(tool("file-read {}", "read", """{"command":"x"}""").shellCommand)
    }
}
