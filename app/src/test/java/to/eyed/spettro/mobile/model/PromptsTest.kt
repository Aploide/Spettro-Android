package to.eyed.spettro.mobile.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.core.acp.AcpToolLocation
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.model.Prompts.BodyBlock
import to.eyed.spettro.mobile.model.Prompts.describedBy

/** Ported from the desktop's permissionCard.test.tsx and acpGaps.test.ts. */
class PromptsTest {

    private val allow = AcpPermissionRequest.Option("allow-once", "Allow", "allow_once")
    private val always = AcpPermissionRequest.Option("allow-always", "Always allow this command", "allow_always")
    private val deny = AcpPermissionRequest.Option("deny", "Deny", "reject_once")

    private fun bash(
        title: String = "",
        kind: String? = "execute",
        texts: List<String> = emptyList(),
        options: List<AcpPermissionRequest.Option> = listOf(allow, deny),
        rawInput: kotlinx.serialization.json.JsonObject? = null,
        diffs: List<AcpToolContent.Diff> = emptyList(),
        locations: List<AcpToolLocation> = emptyList(),
        isCompact: Boolean = false,
    ) = AcpPermissionRequest(
        sessionId = "s1", title = title, toolKind = kind, texts = texts, options = options,
        rawInput = rawInput, diffs = diffs, locations = locations, isCompact = isCompact,
        toolCallId = "call-1",
    )

    // MARK: Titles

    @Test
    fun aRequestTakesTheTitleAndKindOfTheCardItAttachesTo() {
        val card = ToolCallItem(id = "call-3", title = "Edit src/a.ts", kind = "edit", status = AcpToolStatus.IN_PROGRESS)
        val described = bash(kind = null).describedBy(card)
        assertEquals("Edit src/a.ts", described.title)
        assertEquals("edit", described.toolKind)
        // Its own title and kind win.
        val own = bash(title = "Run make", kind = "execute").describedBy(card)
        assertEquals("Run make", own.title)
        assertEquals("execute", own.toolKind)
    }

    @Test
    fun withNoCardAndNoTitleTheKindWritesOne() {
        assertEquals("Run a command", bash().describedBy(null).title)
        val edit = bash(kind = "edit", diffs = listOf(AcpToolContent.Diff("/p/src/hello.txt", null, "hi")))
        assertEquals("Edit hello.txt", edit.describedBy(null).title)
        assertEquals("Read b.go", bash(kind = "read", locations = listOf(AcpToolLocation("/p/b.go", 3))).describedBy(null).title)
        assertEquals("Delete a file", bash(kind = "delete").describedBy(null).title)
        assertEquals("Search the project", bash(kind = "search").describedBy(null).title)
        assertEquals("Open a web page", bash(kind = "fetch").describedBy(null).title)
        assertEquals("Allow this action", bash(kind = null).describedBy(null).title)
        assertEquals("Compact the conversation", bash(kind = "think", isCompact = true).describedBy(null).title)
    }

    // MARK: Options

    @Test
    fun theButtonsTakeAFixedOrderWhateverOrderTheAgentSent() {
        val ordered = Prompts.orderedOptions(bash(options = listOf(deny, always, allow)))
        assertEquals(listOf("allow-once", "allow-always", "deny"), ordered.map { it.option.optionId })
        assertEquals(listOf("Allow once", "Always allow", "Deny"), ordered.map { it.label })
        // The CLI's scope goes under "Always allow", unless it only repeats it.
        assertEquals(listOf(null, "Always allow this command", null), ordered.map { it.caption })
        val bare = Prompts.orderedOptions(bash(options = listOf(always.copy(name = "Always allow"))))
        assertEquals(null, bare.single().caption)
    }

    @Test
    fun withoutKindsTheNamesSayTheRole() {
        val ordered = Prompts.orderedOptions(
            bash(
                options = listOf(
                    AcpPermissionRequest.Option("n", "Deny", ""),
                    AcpPermissionRequest.Option("a", "Always allow", ""),
                    AcpPermissionRequest.Option("y", "Allow", ""),
                ),
            ),
        )
        assertEquals(listOf("y", "a", "n"), ordered.map { it.option.optionId })
    }

    @Test
    fun theCompactionPromptKeepsTheCLIsOwnWords() {
        val request = bash(
            title = "Context nearly full (~182000/200000 tokens). Compact conversation history now?",
            kind = "think",
            isCompact = true,
            options = listOf(
                AcpPermissionRequest.Option("continue", "Continue without compacting", "reject_once"),
                AcpPermissionRequest.Option("compact", "Compact now", "allow_once"),
            ),
        )
        assertEquals(listOf("Compact now", "Continue without compacting"), Prompts.orderedOptions(request).map { it.label })
        assertEquals("This conversation is almost full", Prompts.permissionHeadline(request))
        assertTrue(Prompts.compactDetail(request).startsWith("About 182k of 200k tokens are in use. Compacting"))
        assertFalse(Prompts.showsTitle(request))
        assertEquals(emptyList<BodyBlock>(), Prompts.permissionBody(request))
    }

    // MARK: Headline and body

    @Test
    fun theHeadlineSaysWhatIsAskedByKind() {
        assertEquals("Spettro wants to run a command", Prompts.permissionHeadline(bash()))
        assertEquals(
            "Spettro wants to edit a.ts",
            Prompts.permissionHeadline(bash(kind = "edit", diffs = listOf(AcpToolContent.Diff("/p/a.ts", "a", "b")))),
        )
        val other = bash(title = "Use the mcp tool", kind = "other")
        assertEquals("Spettro needs your approval", Prompts.permissionHeadline(other))
        assertTrue(Prompts.showsTitle(other))
        assertFalse(Prompts.showsTitle(bash(title = "Run ls")))
    }

    @Test
    fun theBodyReadsFencedBlocksClippedOnesIncluded() {
        val blocks = Prompts.permissionBody(
            bash(texts = listOf("````sh\necho ```\n````\n\n[truncated: 4 KB more]", "needs approval: rm -rf dist")),
        )
        assertEquals(
            listOf(
                BodyBlock.Code("sh", "echo ```"),
                BodyBlock.Note("[truncated: 4 KB more]"),
                BodyBlock.Note("Needs your approval: rm -rf dist"),
            ),
            blocks,
        )
    }

    @Test
    fun theBodyDropsWhatTheHeadlineAndTheCommandAlreadySay() {
        assertEquals(
            listOf(BodyBlock.Code("sh", "touch review.txt")),
            Prompts.permissionBody(
                bash(
                    texts = listOf(
                        "```sh\ntouch review.txt\n```",
                        "non-whitelisted command requires approval\nneeds approval: touch review.txt",
                    ),
                ),
            ),
        )
        // A network approval shows its target, not the runtime's "network <tool>".
        assertEquals(
            listOf(BodyBlock.Code("", "https://registry.npmjs.org/react")),
            Prompts.permissionBody(bash(kind = "fetch", texts = listOf("```\nnetwork web-fetch https://registry.npmjs.org/react\n```"))),
        )
        // An older CLI sent only rawInput.
        assertEquals(
            listOf(BodyBlock.Code("sh", "ls")),
            Prompts.permissionBody(bash(rawInput = buildJsonObject { put("command", "ls") })),
        )
        // A reason on its own is a note.
        assertEquals(
            listOf(BodyBlock.Note("outside the sandbox")),
            Prompts.permissionBody(bash(kind = "edit", texts = listOf("outside the sandbox"))),
        )
    }
}
