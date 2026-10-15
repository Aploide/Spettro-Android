package to.eyed.spettro.mobile.acp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpParser
import to.eyed.spettro.mobile.core.acp.AcpSessionUpdate
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.core.acp.AcpToolImage
import to.eyed.spettro.mobile.core.acp.AcpToolLocation
import to.eyed.spettro.mobile.core.acp.AcpToolStatus

private fun obj(text: String): JsonObject = SpettroJson.parseToJsonElement(text).jsonObject

class AcpSessionUpdateParseTest {

    @Test
    fun agentMessageChunk() {
        val update = AcpParser.parseSessionUpdate(
            obj("""{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Hello"}}""")
        )
        assertEquals(AcpSessionUpdate.MessageChunk("Hello"), update)
    }

    @Test
    fun agentThoughtChunk() {
        val update = AcpParser.parseSessionUpdate(
            obj("""{"sessionUpdate":"agent_thought_chunk","content":{"type":"text","text":"thinking..."}}""")
        )
        assertEquals(AcpSessionUpdate.ThoughtChunk("thinking..."), update)
    }

    @Test
    fun chunkWithoutContentIsEmptyText() {
        val update = AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"agent_message_chunk"}"""))
        assertEquals(AcpSessionUpdate.MessageChunk(""), update)
    }

    @Test
    fun toolCall() {
        val update = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"tool_call","toolCallId":"call-1",
                 "title":"[agent#1] bash {\"command\":\"ls -la\"}","kind":"execute",
                 "status":"in_progress","rawInput":{"command":"ls -la"},
                 "locations":[{"path":"/tmp/project/file.txt"}],
                 "content":[{"type":"content","content":{"type":"text","text":"listing"}}]}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.ToolCall
        val e = update.event
        assertEquals("call-1", e.toolCallId)
        assertEquals("[agent#1] bash {\"command\":\"ls -la\"}", e.title)
        assertEquals("execute", e.kind)
        assertEquals(AcpToolStatus.IN_PROGRESS, e.status)
        assertEquals(listOf(AcpToolLocation("/tmp/project/file.txt")), e.locations)
        assertEquals(listOf<AcpToolContent>(AcpToolContent.Text("listing")), e.content)
    }

    @Test
    fun toolCallWithoutIdIsNull() {
        assertNull(AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"tool_call","title":"x"}""")))
    }

    @Test
    fun toolCallUpdateWithDiffAndBareText() {
        val update = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"tool_call_update","toolCallId":"call-2","status":"completed",
                 "content":[
                   {"type":"diff","path":"/a/b.txt","oldText":"old","newText":"new"},
                   {"text":"bare"}]}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.ToolCallUpdate
        val e = update.event
        assertEquals(AcpToolStatus.COMPLETED, e.status)
        assertEquals(
            listOf(
                AcpToolContent.Diff("/a/b.txt", "old", "new"),
                AcpToolContent.Text("bare"),
            ),
            e.content,
        )
    }

    @Test
    fun toolCallWithoutStatusFieldHasNullStatus() {
        val update = AcpParser.parseSessionUpdate(
            obj("""{"sessionUpdate":"tool_call_update","toolCallId":"call-3"}""")
        ) as AcpSessionUpdate.ToolCallUpdate
        assertNull(update.event.status)
    }

    @Test
    fun availableCommandsUpdate() {
        val update = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"available_commands_update","availableCommands":[
                  {"name":"init","description":"Initialize","input":{"hint":"scope"}},
                  {"name":"plan","description":"Plan mode"}]}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.CommandsUpdate
        assertEquals(2, update.commands.size)
        assertEquals("init", update.commands[0].name)
        assertEquals("scope", update.commands[0].hint)
        assertNull(update.commands[1].hint)
    }

    @Test
    fun planDefaultsStatusToPending() {
        val update = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"plan","entries":[
                  {"content":"Read files","status":"completed","priority":"high"},
                  {"content":"Write code"}]}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.Plan
        assertEquals("completed", update.entries[0].status)
        assertEquals("high", update.entries[0].priority)
        assertEquals("pending", update.entries[1].status)
    }

    @Test
    fun usageUpdateReadsMetaTokens() {
        val update = AcpParser.parseSessionUpdate(
            obj(
                """{"sessionUpdate":"usage_update","used":12000,"size":200000,
                    "_meta":{"spettro.app/tokensUsed":54321}}"""
            )
        ) as AcpSessionUpdate.Usage
        assertEquals(12000, update.usage.used)
        assertEquals(200000, update.usage.size)
        assertEquals(54321, update.usage.tokensUsed ?: 0)
    }

    @Test
    fun usageUpdateRequiresPositiveSize() {
        assertNull(AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"usage_update","used":10,"size":0}""")))
        assertNull(AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"usage_update","used":10}""")))
    }

    @Test
    fun unknownTagIsNull() {
        assertNull(AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"totally_new_thing","x":1}""")))
        assertNull(AcpParser.parseSessionUpdate(obj("""{"noTag":true}""")))
    }

    @Test
    fun configOptionUpdateParsesAcpShape() {
        val update = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"config_option_update","configOptions":[
                  {"id":"mode","name":"Mode","currentValue":"code",
                   "options":[{"name":"Code","value":"code"},{"name":"Plan","value":"plan"}]}]}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.ConfigUpdate
        assertEquals(1, update.options.size)
        assertTrue(update.options[0].kind is to.eyed.spettro.mobile.core.acp.AcpConfigOption.Kind.Select)
    }

    // MARK: Tool details the desktop keeps (acpGaps.test.ts "tool output")

    @Test
    fun toolCallKeepsImagesAtMostFourRawOutputAndLocationLines() {
        val image = """{"type":"content","content":{"type":"image","data":"iVBOR","mimeType":"image/png"}}"""
        val update = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"tool_call_update","toolCallId":"call-1",
                 "content":[{"type":"content","content":{"type":"text","text":"shot taken"}},
                   $image,$image,$image,$image,$image],
                 "rawOutput":{"output":"the full output"},
                 "locations":[{"path":"/p/a.go","line":42},{"path":"/p/b.go"}]}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.ToolCallUpdate
        val e = update.event
        assertEquals(listOf<AcpToolContent>(AcpToolContent.Text("shot taken")), e.content)
        assertEquals(4, e.images.size)
        assertEquals(AcpToolImage("iVBOR", "image/png"), e.images[0])
        assertEquals("the full output", e.rawOutput)
        assertEquals(listOf(AcpToolLocation("/p/a.go", 42), AcpToolLocation("/p/b.go")), e.locations)
    }

    @Test
    fun rawOutputMayBeABareString() {
        val update = AcpParser.parseSessionUpdate(
            obj("""{"sessionUpdate":"tool_call_update","toolCallId":"c","rawOutput":"plain"}""")
        ) as AcpSessionUpdate.ToolCallUpdate
        assertEquals("plain", update.event.rawOutput)
    }

    @Test
    fun workflowMetaIsCarriedAndNothingElseOfMeta() {
        val card = AcpParser.parseSessionUpdate(
            obj(
                """
                {"sessionUpdate":"tool_call_update","toolCallId":"workflow-wf_1","status":"in_progress",
                 "_meta":{"spettro.app/workflow":{"version":1,"runId":"wf_1","status":"paused"},
                          "other.app/thing":1}}
                """.trimIndent()
            )
        ) as AcpSessionUpdate.ToolCallUpdate
        assertEquals(obj("""{"version":1,"runId":"wf_1","status":"paused"}"""), card.event.workflowMeta)
        val plain = AcpParser.parseSessionUpdate(
            obj("""{"sessionUpdate":"tool_call","toolCallId":"call-1","_meta":{"other.app/thing":1}}""")
        ) as AcpSessionUpdate.ToolCall
        assertNull(plain.event.workflowMeta)
        // Not an object: dropped.
        val odd = AcpParser.parseSessionUpdate(
            obj("""{"sessionUpdate":"tool_call","toolCallId":"w","_meta":{"spettro.app/workflow":"nope"}}""")
        ) as AcpSessionUpdate.ToolCall
        assertNull(odd.event.workflowMeta)
    }

    @Test
    fun currentModeUpdate() {
        assertEquals(
            AcpSessionUpdate.ModeUpdate("ask"),
            AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"current_mode_update","currentModeId":"ask"}""")),
        )
        assertNull(AcpParser.parseSessionUpdate(obj("""{"sessionUpdate":"current_mode_update"}""")))
    }
}
