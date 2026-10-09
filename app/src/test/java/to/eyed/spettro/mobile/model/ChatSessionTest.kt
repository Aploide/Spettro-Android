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
import to.eyed.spettro.mobile.core.acp.AcpToolImage
import to.eyed.spettro.mobile.core.acp.AcpToolLocation
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

    // MARK: Tool calls (desktop chatSession.test.ts)

    private fun tool(id: String, title: String? = null, status: AcpToolStatus? = null) =
        AcpToolCallEvent(toolCallId = id, title = title, status = status)

    private fun ChatSession.tools(): List<ToolCallItem> =
        items.value.filterIsInstance<TranscriptItem.Tool>().map { it.tool }

    @Test
    fun aWorkflowCardsMetadataIsReplacedWholeByEachUpdateThatCarriesIt() {
        val s = session()
        val running = SpettroJson.parseToJsonElement("""{"version":1,"status":"running"}""").jsonObject
        val paused = SpettroJson.parseToJsonElement("""{"version":1,"status":"paused"}""").jsonObject
        s.applyToolEvent(AcpToolCallEvent("workflow-wf_1", title = "workflow audit", workflowMeta = running), isStart = true)
        assertEquals(running, s.tools()[0].workflow)
        s.applyToolEvent(AcpToolCallEvent("workflow-wf_1", workflowMeta = paused), isStart = false)
        assertEquals(paused, s.tools()[0].workflow)
        s.applyToolEvent(AcpToolCallEvent("workflow-wf_1", status = AcpToolStatus.COMPLETED), isStart = false)
        assertEquals(paused, s.tools()[0].workflow)
    }

    @Test
    fun toolImagesRawOutputAndLocationsAreKept() {
        val s = session()
        val image = AcpToolImage("iVBOR", "image/png")
        s.applyToolEvent(
            AcpToolCallEvent("call-1", title = "shot", locations = listOf(AcpToolLocation("/p/a.go", 4))),
            isStart = true,
        )
        s.applyToolEvent(
            AcpToolCallEvent("call-1", images = listOf(image), rawOutput = "everything"),
            isStart = false,
        )
        val t = s.tools().single()
        assertEquals(listOf(image), t.images)
        assertEquals("everything", t.rawOutput)
        assertEquals(listOf(AcpToolLocation("/p/a.go", 4)), t.locations)
    }


    // MARK: Restoring mid-turn

    private fun snapshot(items: String) = AcpParser.parseStoredSession(
        SpettroJson.parseToJsonElement(
            """{"id":"c1","projectPath":"/p","title":"t","createdAt":"2026-01-01T00:00:00Z",
                "isPinned":false,"isArchived":false,"items":[$items]}"""
        ).jsonObject
    )

    private fun userItem(id: String, steering: String? = null) =
        """{"message":{"_0":{"id":"$id","role":"user","noticeIsError":false,"text":"$id",
            "timestamp":"ts"${if (steering != null) ",\"steering\":\"$steering\"" else ""}}}}"""

    private fun toolItem(id: String, status: String = "completed") =
        """{"tool":{"_0":{"id":"$id","title":"$id","status":"$status","output":"","diffs":[],
            "locations":[],"timestamp":"ts"}}}"""

    private fun assistantItem(text: String, streaming: Boolean? = null) =
        """{"message":{"_0":{"id":"a-$text","role":"assistant","noticeIsError":false,"text":"$text",
            "timestamp":"ts"${if (streaming != null) ",\"isStreaming\":$streaming" else ""}}}}"""

    @Test
    fun aChatOpenedMidAnswerKeepsWritingTheSameBubble() {
        // A host without snapshotV2 doesn't say what is streaming; the
        // answer at the tail is.
        val s = session()
        s.restoreFrom(snapshot(listOf(userItem("u1"), assistantItem("Half an ")).joinToString(",")), isBusy = true)
        s.appendAssistant("answer.")
        assertEquals(listOf("Half an answer."), s.assistantBubbles().map { it.text })
    }

    @Test
    fun aSnapshotThatSaysWhatIsStreamingIsBelieved() {
        val s = session()
        s.restoreFrom(
            snapshot(listOf(userItem("u1"), assistantItem("Interim", streaming = false), toolItem("call-1", "in_progress")).joinToString(",")),
            isBusy = true,
        )
        assertTrue(s.messages().none { it.isStreaming })
        s.restoreFrom(snapshot(listOf(userItem("u1"), assistantItem("Going", streaming = true)).joinToString(",")), isBusy = true)
        s.appendAssistant(" on")
        assertEquals(listOf("Going on"), s.assistantBubbles().map { it.text })
    }

    @Test
    fun anIdleSnapshotHasNothingStreamingAndNoSteerWaiting() {
        val s = session()
        s.restoreFrom(
            snapshot(listOf(userItem("u1"), userItem("u2", "sending"), userItem("u3", "delivered"), assistantItem("Done", streaming = true)).joinToString(",")),
            isBusy = false,
        )
        assertTrue(s.messages().none { it.isStreaming })
        assertEquals(listOf(null, null, ChatMessage.Steering.DELIVERED), s.messages().take(3).map { it.steering })
    }

    private fun ChatSession.messages(): List<ChatMessage> =
        items.value.filterIsInstance<TranscriptItem.Message>().map { it.message }

    // MARK: Interim prose and reasoning timing

    @Test
    fun interimProseIsFinishedOnceTheAgentMovesOnToAToolCall() {
        val s = session()
        s.appendAssistant("Let me look at the form.")
        s.applyToolEvent(tool("c1", "file-read {}", AcpToolStatus.IN_PROGRESS), isStart = true)
        assertFalse(s.messages()[0].isStreaming)
        // …and the next prose starts a bubble of its own.
        s.appendAssistant("Fixed.")
        assertEquals(3, s.items.value.size)
        s.endStreaming()
        assertTrue(s.messages().none { it.isStreaming })
    }

    @Test
    fun reasoningIsStampedWithItsFirstAndLatestChunk() {
        var now = 1_000L
        val s = ChatSession(chatId = "c1", projectPath = "/p", clock = { now })
        s.appendReasoning("Looking at ")
        now = 9_500L
        s.appendReasoning("the form.")
        now = 12_000L
        s.appendAssistant("Fixed.")
        val reasoning = s.messages()[0]
        assertEquals(1_000L, reasoning.startedAt)
        assertEquals(9_500L, reasoning.endedAt)
        assertFalse(reasoning.isStreaming)
        assertEquals("9s", reasoning.thoughtDuration)
        // An answer is not reasoning, and is not stamped.
        assertNull(s.messages()[1].startedAt)
    }

    @Test
    fun thoughtDurationReadsLikeTheDesktop() {
        fun span(ms: Long) = ChatMessage(role = ChatMessage.Role.Reasoning, text = "", startedAt = 0, endedAt = ms)
        assertNull(span(400).thoughtDuration)
        assertEquals("12s", span(12_000).thoughtDuration)
        assertEquals("2m 5s", span(125_000).thoughtDuration)
        assertNull(ChatMessage(role = ChatMessage.Role.Reasoning, text = "").thoughtDuration)
    }

    // MARK: Steering

    @Test
    fun steeringAcknowledgementsAreStateNotProse() {
        val s = session()
        s.appendUserMessage("refactor the parser")
        s.beginRun()
        val steer = s.appendUserMessage("also keep the old API", steering = ChatMessage.Steering.SENDING)
        s.appendAssistant("→ steering queued: the running agent will see this message at its next step")
        assertEquals(steer.id, s.steeringMessage(ChatMessage.Steering.QUEUED))
        s.appendAssistant("✔ steering delivered: also keep the old API")
        assertEquals(steer.id, s.steeringMessage(ChatMessage.Steering.DELIVERED))
        assertTrue(s.assistantBubbles().isEmpty())
    }

    @Test
    fun acknowledgementsGoToTheOldestSteerInTheEarlierState() {
        val s = session()
        s.appendUserMessage("go")
        val first = s.appendUserMessage("one", steering = ChatMessage.Steering.SENDING)
        val second = s.appendUserMessage("two", steering = ChatMessage.Steering.SENDING)
        s.appendAssistant("  → steering queued: …")
        assertEquals(listOf(ChatMessage.Steering.QUEUED, ChatMessage.Steering.SENDING), listOf(first, second).map { m -> s.messages().first { it.id == m.id }.steering })
        // Delivered takes a queued one first, then a sending one.
        s.appendAssistant("✔ steering delivered: one")
        s.appendAssistant("✔ steering delivered: two")
        assertEquals(listOf(ChatMessage.Steering.DELIVERED, ChatMessage.Steering.DELIVERED), s.messages().drop(1).map { it.steering })
        // One with nothing to apply to is dropped all the same.
        s.appendAssistant("→ steering queued: nobody")
        assertTrue(s.assistantBubbles().isEmpty())
    }

    @Test
    fun theEndOfTheTurnDropsSteersStillWaiting() {
        val s = session()
        s.appendUserMessage("go")
        s.beginRun()
        s.appendUserMessage("late", steering = ChatMessage.Steering.QUEUED)
        s.appendUserMessage("read", steering = ChatMessage.Steering.DELIVERED)
        s.endRun("end_turn")
        assertEquals(listOf(null, null, ChatMessage.Steering.DELIVERED), s.messages().map { it.steering })
    }

    // MARK: Mode

    @Test
    fun currentModeUpdateMovesTheModeOption() {
        val s = session()
        s.applyConfigUpdate(
            listOf(
                AcpConfigOption(
                    id = "mode", name = "Mode",
                    kind = AcpConfigOption.Kind.Select(
                        "code",
                        listOf(AcpConfigOption.OptionGroup(null, listOf(AcpConfigOption.Opt("Code", "code"), AcpConfigOption.Opt("Ask", "ask")))),
                    ),
                ),
            )
        )
        s.pendingConfigChanges["mode"] = ConfigValue.Str("code")
        s.apply(AcpSessionUpdate.ModeUpdate("ask"))
        assertEquals(ConfigValue.Str("ask"), s.displayedConfigValues["mode"])
        assertTrue("mode" !in s.pendingConfigChanges)
    }
}
