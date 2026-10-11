package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpToolImage
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/** The desktop's toolGroups.ts, folded the same way. */
class ToolGroupsTest {

    private fun tool(id: String, title: String, kind: String, status: AcpToolStatus = AcpToolStatus.COMPLETED) =
        ToolCallItem(id = id, title = title, kind = kind, status = status)

    private fun row(tool: ToolCallItem) = TranscriptRow.Item(TranscriptItem.Tool(tool))

    private fun message(text: String) =
        TranscriptRow.Item(TranscriptItem.Message(ChatMessage(id = text, role = ChatMessage.Role.Assistant, text = text)))

    private val read1 = tool("r1", "file-read {}", "read")
    private val read2 = tool("r2", "file-read {}", "read")
    private val grep = tool("g1", "grep {}", "search")
    private val ls = tool("l1", "ls {}", "search")

    @Test
    fun onlyFinishedReadsSearchesAndListingsFold() {
        assertTrue(ToolGroups.isGroupable(read1))
        assertTrue(ToolGroups.isGroupable(grep))
        assertTrue(ToolGroups.isGroupable(ls))
        assertFalse(ToolGroups.isGroupable(tool("r", "file-read {}", "read", AcpToolStatus.IN_PROGRESS)))
        assertFalse(ToolGroups.isGroupable(tool("e", "file-edit {}", "edit")))
        assertFalse(ToolGroups.isGroupable(tool("v", "view-image {}", "read")))
        assertFalse(ToolGroups.isGroupable(read1.copy(images = listOf(AcpToolImage("x", "image/png")))))
    }

    @Test
    fun twoOrMoreConsecutiveFoldIntoOneRow() {
        val folded = ToolGroups.fold(listOf(row(read1), row(read2), row(grep)))
        assertEquals(1, folded.size)
        val group = folded[0] as DisplayRow.Tools
        assertEquals("tools-tool-r1", group.id)
        assertEquals(listOf("r1", "r2", "g1"), group.tools.map { it.id })
    }

    @Test
    fun oneReadIsJustARow() {
        val folded = ToolGroups.fold(listOf(row(read1), message("hi")))
        assertEquals(listOf("tool-r1", "msg-hi"), folded.map { it.id })
        assertTrue(folded.all { it is DisplayRow.Row })
    }

    @Test
    fun anythingBetweenEndsTheRun() {
        val edit = tool("e1", "file-edit {}", "edit")
        val running = tool("r3", "file-read {}", "read", AcpToolStatus.IN_PROGRESS)
        val folded = ToolGroups.fold(
            listOf(row(read1), row(read2), row(edit), row(grep), message("m"), row(ls), row(running)),
        )
        assertEquals(listOf("tools-tool-r1", "tool-e1", "tool-g1", "msg-m", "tool-l1", "tool-r3"), folded.map { it.id })
    }

    @Test
    fun summaryFollowsFirstSeenOrder() {
        assertEquals("Read 2 files, searched 1 pattern", ToolGroups.summary(listOf(read1, grep, read2)))
        assertEquals("Searched 1 pattern, read 1 file", ToolGroups.summary(listOf(grep, read1)))
        assertEquals("Listed 1 folder, read 2 files", ToolGroups.summary(listOf(ls, read1, read2)))
    }

    @Test
    fun theNewestUserMessageIsFoundPastTheRowsAfterIt() {
        val user = { id: String ->
            TranscriptRow.Item(TranscriptItem.Message(ChatMessage(id = id, role = ChatMessage.Role.User, text = id)))
        }
        val rows = ToolGroups.fold(listOf(user("u1"), message("a1"), user("u2"), row(read1), row(read2), message("a2")))
        assertEquals("msg-u2", newestUserMessageId(rows))
        assertEquals(null, newestUserMessageId(ToolGroups.fold(listOf(message("a"), row(read1)))))
        assertEquals(null, newestUserMessageId(emptyList()))
    }
}
