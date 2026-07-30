package to.eyed.spettro.mobile.model

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpParser
import to.eyed.spettro.mobile.core.acp.AcpSessionUpdate
import to.eyed.spettro.mobile.core.acp.AcpToolCallEvent
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

class ChatSessionTest {

    private fun session() = ChatSession(chatId = "c1", projectPath = "/Users/dev/myproject")

    private fun ChatSession.lastMessage(): ChatMessage =
        (items.value.last() as TranscriptItem.Message).message

    private fun ChatSession.assistantBubbles(): List<ChatMessage> =
        items.value.filterIsInstance<TranscriptItem.Message>()
            .map { it.message }
            .filter { it.role == ChatMessage.Role.Assistant }

    // MARK: Streaming basics

    @Test
    fun assistantChunksAccumulateIntoOneStreamingBubble() {
        val s = session()
        s.appendAssistant("Hel")
        s.appendAssistant("lo")
        assertEquals(1, s.assistantBubbles().size)
        assertEquals("Hello", s.lastMessage().text)
        assertTrue(s.lastMessage().isStreaming)
        s.endStreaming()
        assertFalse(s.lastMessage().isStreaming)
    }

    @Test
    fun reasoningStreamsSeparatelyAndClosesWhenAnswerStarts() {
        val s = session()
        s.appendReasoning("thinking ")
        s.appendReasoning("hard")
        assertEquals("thinking hard", s.lastMessage().text)
        s.appendAssistant("Answer")
        val reasoning = (s.items.value[0] as TranscriptItem.Message).message
        assertEquals(ChatMessage.Role.Reasoning, reasoning.role)
        assertFalse(reasoning.isStreaming)
        assertEquals("Answer", s.lastMessage().text)
    }

    // MARK: Replay suppression — the three guards

    @Test
    fun guard1_identicalRedeliveryIsDropped() {
        val s = session()
        s.appendAssistant("The final answer.")
        s.endStreaming()
        s.appendAssistant("The final answer.")
        assertEquals(1, s.assistantBubbles().size)
        assertEquals("The final answer.", s.lastMessage().text)
        assertFalse(s.lastMessage().isStreaming)
    }

    @Test
    fun guard2_strictPrefixRedeliveryReplacesInsteadOfDuplicating() {
        val s = session()
        s.appendAssistant("The final")
        s.endStreaming()
        s.appendAssistant("The final answer, but longer.")
        assertEquals(1, s.assistantBubbles().size)
        assertEquals("The final answer, but longer.", s.lastMessage().text)
    }

    @Test
    fun guard3_multiChunkTailReplayIsSuppressed() {
        val s = session()
        s.appendAssistant("Result: xyxy")
        s.endStreaming()
        // Re-delivery keeps re-stating the tail of the finished bubble,
        // chunk by chunk; each chunk extends the accumulated replay tail.
        s.appendAssistant("xy")            // "Result: xyxy".endsWith("xy")
        s.appendAssistant("xy")            // endsWith("xyxy")
        assertEquals(1, s.assistantBubbles().size)
        assertEquals("Result: xyxy", s.lastMessage().text)
        // A chunk that breaks the tail match starts a genuinely new bubble.
        s.appendAssistant("New message")
        assertEquals(2, s.assistantBubbles().size)
        assertEquals("New message", s.lastMessage().text)
        assertTrue(s.lastMessage().isStreaming)
    }

    @Test
    fun guard3_singleTailChunkSuppressedThenFreshTextAppends() {
        val s = session()
        s.appendAssistant("Hello world.")
        s.endStreaming()
        s.appendAssistant("world.")        // tail re-statement -> dropped
        assertEquals(1, s.assistantBubbles().size)
        s.appendAssistant("Unrelated")
        assertEquals(2, s.assistantBubbles().size)
    }

    @Test
    fun freshChunkAfterFinishedBubbleStartsNewBubble() {
        val s = session()
        s.appendAssistant("First answer")
        s.endStreaming()
        s.appendAssistant("Second answer")
        assertEquals(2, s.assistantBubbles().size)
    }

    // MARK: Titles

    @Test
    fun derivedTitleTakesFirstLineCappedAt48() {
        assertEquals("Fix the bug", ChatSession.derivedTitle("Fix the bug\nin the parser"))
        assertEquals(48, ChatSession.derivedTitle("x".repeat(100)).length)
        assertEquals("trimmed", ChatSession.derivedTitle("  trimmed\nrest"))
        assertEquals("", ChatSession.derivedTitle("   "))
    }

    @Test
    fun firstUserMessageSetsTitleFromPlaceholder() {
        val s = session()
        assertEquals("myproject", s.title.value)
        s.appendUserMessage("Refactor the networking layer please")
        assertEquals("Refactor the networking layer please", s.title.value)
        s.appendUserMessage("Second message")
        assertEquals("Refactor the networking layer please", s.title.value)
    }

    @Test
    fun attachmentOnlyPromptTitlesAsImage() {
        val s = session()
        s.appendUserMessage("", listOf(ImageAttachment(base64Data = "aGk=")))
        assertEquals("Image", s.title.value)
    }

    // MARK: Tool events

    @Test
    fun toolCallThenUpdateMergesByToolCallId() {
        val s = session()
        val start = AcpParser.parseSessionUpdate(
            SpettroJson.parseToJsonElement(
                """{"sessionUpdate":"tool_call","toolCallId":"t1",
                    "title":"bash {\"command\":\"ls\"}","kind":"execute","status":"in_progress",
                    "rawInput":{"command":"ls"}}"""
            ).jsonObject
        )!!
        s.apply(start)
        val update = AcpParser.parseSessionUpdate(
            SpettroJson.parseToJsonElement(
                """{"sessionUpdate":"tool_call_update","toolCallId":"t1","status":"completed",
                    "content":[{"type":"content","content":{"type":"text","text":"file.txt"}}]}"""
            ).jsonObject
        )!!
        s.apply(update)

        assertEquals(1, s.items.value.size)
        val tool = (s.items.value[0] as TranscriptItem.Tool).tool
        assertEquals(AcpToolStatus.COMPLETED, tool.status)
        assertEquals("file.txt", tool.output)
        assertEquals("bash {\"command\":\"ls\"}", tool.title)      // kept from start
        assertEquals("""{"command":"ls"}""", tool.argsJSON)         // kept from start
    }

    @Test
    fun toolUpdateWithoutStartUpsertsAsCompleted() {
        val s = session()
        s.applyToolEvent(AcpToolCallEvent(toolCallId = "orphan"), isStart = false)
        val tool = (s.items.value[0] as TranscriptItem.Tool).tool
        assertEquals("Tool call", tool.title)
        assertEquals(AcpToolStatus.COMPLETED, tool.status)
    }

    @Test
    fun toolStartWithoutStatusDefaultsToInProgress() {
        val s = session()
        s.applyToolEvent(AcpToolCallEvent(toolCallId = "t2", title = "read"), isStart = true)
        assertEquals(
            AcpToolStatus.IN_PROGRESS,
            (s.items.value[0] as TranscriptItem.Tool).tool.status,
        )
    }

    @Test
    fun toolDiffContentBecomesDiffsAndOutput() {
        val s = session()
        s.applyToolEvent(
            AcpToolCallEvent(
                toolCallId = "t3",
                content = listOf(AcpToolContent.Diff("/a/b.txt", "x", "y")),
            ),
            isStart = false,
        )
        val tool = (s.items.value[0] as TranscriptItem.Tool).tool
        assertEquals(listOf(ToolCallItem.ToolDiff("/a/b.txt", "x", "y")), tool.diffs)
        assertEquals("Edited /a/b.txt", tool.output)
    }

    // MARK: Config

    @Test
    fun emptyConfigUpdateIsIgnored() {
        val s = session()
        val option = AcpConfigOption(
            id = "mode", name = "Mode",
            kind = AcpConfigOption.Kind.Select(
                "code",
                listOf(AcpConfigOption.OptionGroup(null, listOf(AcpConfigOption.Opt("Code", "code")))),
            ),
        )
        s.applyConfigUpdate(listOf(option))
        s.apply(AcpSessionUpdate.ConfigUpdate(emptyList()))
        assertEquals(listOf(option), s.configOptions.value)
    }

    @Test
    fun applyLocalConfigValueUpdatesSelectAndBool() {
        val s = session()
        s.applyConfigUpdate(
            listOf(
                AcpConfigOption(
                    id = "mode", name = "Mode",
                    kind = AcpConfigOption.Kind.Select(
                        "code",
                        listOf(
                            AcpConfigOption.OptionGroup(
                                null,
                                listOf(AcpConfigOption.Opt("Code", "code"), AcpConfigOption.Opt("Plan", "plan")),
                            )
                        ),
                    ),
                ),
                AcpConfigOption(id = "ultra", name = "Ultra", kind = AcpConfigOption.Kind.Bool(false)),
            )
        )
        s.applyLocalConfigValue("mode", "plan")
        s.applyLocalConfigValue("ultra", true)
        assertEquals("plan", (s.option("mode")!!.kind as AcpConfigOption.Kind.Select).current)
        assertEquals(true, (s.option("ultra")!!.kind as AcpConfigOption.Kind.Bool).current)
        // Mismatched kinds are ignored.
        s.applyLocalConfigValue("mode", false)
        assertEquals("plan", (s.option("mode")!!.kind as AcpConfigOption.Kind.Select).current)
        assertEquals(
            mapOf<String, ConfigValue>(
                "mode" to ConfigValue.Str("plan"),
                "ultra" to ConfigValue.Bool(true),
            ),
            s.displayedConfigValues,
        )
    }

    // MARK: Restore

    @Test
    fun restoreFromStoredSnapshotRebuildsTranscript() {
        val stored = AcpParser.parseStoredSession(
            SpettroJson.parseToJsonElement(
                """
                {"id":"S1","acpSessionId":"acp-1","projectPath":"/p/proj","title":"My chat",
                 "createdAt":"2026-01-01T00:00:00Z","isPinned":true,"isArchived":false,
                 "items":[
                   {"message":{"_0":{"id":"m1","role":"user","noticeIsError":false,
                     "text":"hi","timestamp":"ts"}}},
                   {"message":{"_0":{"id":"m2","role":"notice","noticeIsError":true,
                     "text":"failed","timestamp":"ts"}}},
                   {"tool":{"_0":{"id":"t1","title":"bash","kind":"execute","status":"completed",
                     "output":"ok","diffs":[],"locations":[],"timestamp":"ts"}}}],
                 "configOptions":[{"id":"m","name":"M","kind":{"boolean":{"current":true}}}]}
                """.trimIndent()
            ).jsonObject
        )
        val s = session()
        s.restoreFrom(stored)
        assertEquals("acp-1", s.acpSessionId.value)
        assertEquals("My chat", s.title.value)
        assertTrue(s.isPinned.value)
        assertEquals(3, s.items.value.size)
        assertEquals(
            ChatMessage.Role.Notice(isError = true),
            (s.items.value[1] as TranscriptItem.Message).message.role,
        )
        assertEquals(
            AcpToolStatus.COMPLETED,
            (s.items.value[2] as TranscriptItem.Tool).tool.status,
        )
        assertEquals(1, s.configOptions.value.size)
        assertFalse(s.isPristine)
    }

    // MARK: Run ticker

    @Test
    fun runLifecycleTracksLiveTokens() {
        val s = session()
        assertNull(s.runStartedAt.value)
        s.setUsage(to.eyed.spettro.mobile.core.acp.AcpUsage(used = 10, size = 100, tokensUsed = 500))
        s.beginRun()
        assertTrue(s.isBusy.value)
        s.setUsage(to.eyed.spettro.mobile.core.acp.AcpUsage(used = 20, size = 100, tokensUsed = 800))
        assertEquals(300, s.liveRunTokens)
        s.endRun("end_turn")
        assertNull(s.runStartedAt.value)
        assertFalse(s.isBusy.value)
        assertEquals(0, s.liveRunTokens)
    }
}
