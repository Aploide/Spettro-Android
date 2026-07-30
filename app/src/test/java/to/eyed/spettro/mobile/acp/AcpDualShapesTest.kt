package to.eyed.spettro.mobile.acp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpParser

private fun arr(text: String): JsonArray = SpettroJson.parseToJsonElement(text).jsonArray

/**
 * The same logical payloads arrive in two encodings — the ACP wire shape and
 * the Swift-synthesized Codable shape. Both must parse into identical models.
 */
class AcpDualShapesTest {

    @Test
    fun configOptionSelectBothShapesProduceSameModel() {
        val acp = AcpParser.parseConfigOptionsAcp(
            arr(
                """
                [{"id":"model","name":"Model","description":"Active model","category":"session",
                  "currentValue":"gpt-5",
                  "options":[
                    {"name":"OpenAI","options":[
                      {"name":"GPT-5","value":"gpt-5","description":"flagship"}]},
                    {"name":"Anthropic","options":[
                      {"name":"Fable","value":"fable"}]}]}]
                """.trimIndent()
            )
        )
        val stored = AcpParser.parseConfigOptionsStored(
            arr(
                """
                [{"id":"model","name":"Model","description":"Active model","category":"session",
                  "kind":{"select":{"current":"gpt-5","groups":[
                    {"name":"OpenAI","options":[
                      {"name":"GPT-5","value":"gpt-5","description":"flagship"}]},
                    {"name":"Anthropic","options":[
                      {"name":"Fable","value":"fable"}]}]}}}]
                """.trimIndent()
            )
        )
        assertEquals(acp, stored)
        assertEquals("GPT-5", acp[0].currentLabel)
    }

    @Test
    fun configOptionUngroupedSelectAcpShape() {
        val opts = AcpParser.parseConfigOptionsAcp(
            arr(
                """
                [{"id":"mode","name":"Mode","currentValue":"plan",
                  "options":[{"name":"Code","value":"code"},{"name":"Plan","value":"plan"}]}]
                """.trimIndent()
            )
        )
        val kind = opts[0].kind as AcpConfigOption.Kind.Select
        assertEquals(1, kind.groups.size)
        assertNull(kind.groups[0].name)
        assertEquals(2, kind.groups[0].options.size)
        assertEquals("Plan", opts[0].currentLabel)
    }

    @Test
    fun configOptionBooleanBothShapes() {
        val acp = AcpParser.parseConfigOptionsAcp(
            arr("""[{"id":"ultra","name":"Ultra","type":"boolean","currentValue":true}]""")
        )
        val stored = AcpParser.parseConfigOptionsStored(
            arr("""[{"id":"ultra","name":"Ultra","kind":{"boolean":{"current":true}}}]""")
        )
        assertEquals(acp, stored)
        assertEquals(AcpConfigOption.Kind.Bool(true), acp[0].kind)
        assertEquals("On", acp[0].currentLabel)
    }

    @Test
    fun configOptionUnknownCurrentFallsBackToRawValue() {
        val opts = AcpParser.parseConfigOptionsAcp(
            arr(
                """[{"id":"m","name":"M","currentValue":"mystery",
                     "options":[{"name":"A","value":"a"}]}]"""
            )
        )
        assertEquals("mystery", opts[0].currentLabel)
    }

    @Test
    fun commandsBothShapes() {
        val acp = AcpParser.parseCommands(
            arr("""[{"name":"init","description":"Initialize project","input":{"hint":"scope"}}]""")
        )
        val stored = AcpParser.parseCommands(
            arr("""[{"name":"init","description":"Initialize project","hint":"scope"}]""")
        )
        assertEquals(acp, stored)
        assertEquals("scope", acp[0].hint)
        assertEquals("Initialize project", acp[0].description)
    }

    @Test
    fun usageBothShapes() {
        val acpObj = SpettroJson.parseToJsonElement(
            """{"used":1000,"size":128000,"_meta":{"spettro.app/tokensUsed":42}}"""
        ).jsonObject
        val storedObj = SpettroJson.parseToJsonElement(
            """{"used":1000,"size":128000,"totalTokens":42}"""
        ).jsonObject
        assertEquals(AcpParser.parseUsage(acpObj), AcpParser.parseUsage(storedObj))
        assertEquals(42, AcpParser.parseUsage(acpObj)?.tokensUsed)
    }

    @Test
    fun planParsesFromEitherSource() {
        val entries = AcpParser.parsePlan(
            arr("""[{"content":"Do the thing","status":"in_progress"},{"content":"Other"}]""")
        )
        assertEquals("in_progress", entries[0].status)
        assertEquals("pending", entries[1].status)
    }
}
