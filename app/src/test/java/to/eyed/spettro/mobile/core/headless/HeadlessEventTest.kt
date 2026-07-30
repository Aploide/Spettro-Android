package to.eyed.spettro.mobile.core.headless

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson

class HeadlessEventTest {

    private fun parse(json: String): HeadlessEvent? =
        HeadlessEvent.parse(SpettroJson.parseToJsonElement(json) as JsonObject)

    @Test
    fun toolArgsAsObject_tuiShape() {
        val ev = parse(
            """{"seq":7,"kind":"tool","at":"2026-07-30T10:00:00.000000001Z","data":
               {"name":"shell","status":"success","agent":"main",
                "args":{"cmd":"ls -la","cwd":"/tmp"},"output":"ok","mode":"coding"}}"""
        )
        val tool = ev as HeadlessEvent.Tool
        assertEquals(7L, tool.seq)
        assertEquals("coding", tool.mode)
        assertEquals("shell", tool.name)
        assertEquals(HeadlessEvent.Tool.STATUS_SUCCESS, tool.status)
        assertEquals("main", tool.agent)
        assertNotNull(tool.argsJson)
        assertEquals("ls -la", tool.argsJson!!["cmd"]!!.jsonPrimitive.content)
        assertNull(tool.argsRaw)
        assertEquals("ok", tool.output)
    }

    @Test
    fun toolArgsAsString_headlessShape() {
        val ev = parse(
            """{"seq":8,"kind":"tool","at":"t","data":
               {"name":"read_file","status":"running","agent":"main",
                "args":"{\"path\":\"main.go\"}","mode":"plan"}}"""
        )
        val tool = ev as HeadlessEvent.Tool
        assertNull(tool.argsJson)
        assertEquals("""{"path":"main.go"}""", tool.argsRaw)
        assertNull(tool.output)
    }

    @Test
    fun toolArgsRaw_tuiNonJsonArgs() {
        val ev = parse(
            """{"seq":9,"kind":"tool","at":"t","data":
               {"name":"shell","status":"error","args_raw":"not json at all","mode":"coding"}}"""
        )
        val tool = ev as HeadlessEvent.Tool
        assertNull(tool.argsJson)
        assertEquals("not json at all", tool.argsRaw)
    }

    @Test
    fun askUserV2_parsesFormAndFlatFields() {
        val ev = parse(
            """{"seq":12,"kind":"ask_user","at":"t","data":{
                 "version":2,"question_id":"q-3","count":2,"active":1,
                 "questions":[
                   {"header":"Color","question":"Pick a color","multi_select":false,
                    "allow_free_response":false,
                    "options":[{"label":"Red"},{"label":"Blue","description":"cool","is_recommended":true}]},
                   {"header":"Sizes","question":"Pick sizes","multi_select":true,
                    "allow_free_response":true,
                    "options":[{"label":"S"},{"label":"M"}]}
                 ],
                 "question":"Pick sizes","options":["S","M"],
                 "context":"theme setup","default":"","allow_free_response":true,
                 "mode":"plan"}}"""
        )
        val ask = ev as HeadlessEvent.AskUser
        val form = ask.form
        assertEquals(2, form.version)
        assertEquals("q-3", form.questionId)
        assertEquals(2, form.count)
        assertEquals(1, form.active)
        assertEquals("theme setup", form.context)
        assertEquals(2, form.questions.size)

        val q0 = form.questions[0]
        assertEquals("Color", q0.header)
        assertEquals("Pick a color", q0.question)
        assertFalse(q0.multiSelect)
        assertFalse(q0.allowFreeResponse)
        assertEquals(2, q0.options.size)
        assertEquals("Blue", q0.options[1].label)
        assertEquals("cool", q0.options[1].description)
        assertTrue(q0.options[1].isRecommended)
        assertFalse(q0.options[0].isRecommended)

        val q1 = form.questions[1]
        assertEquals("Sizes", q1.header)
        assertTrue(q1.multiSelect)
        assertTrue(q1.allowFreeResponse)

        // Flat (v1-compat) fields describe the active question.
        assertEquals("Pick sizes", form.flatQuestion)
        assertEquals(listOf("S", "M"), form.flatOptions)
        assertTrue(form.allowFreeResponse)
        assertNull(form.default)
    }

    @Test
    fun askUserV1_synthesizesSingleQuestion() {
        val ev = parse(
            """{"seq":4,"kind":"ask_user","at":"t","data":{
                 "question_id":"q-1","question":"Deploy now?",
                 "options":["Yes — ship it","No"],
                 "context":"release","allow_free_response":true}}"""
        )
        val form = (ev as HeadlessEvent.AskUser).form
        assertEquals(1, form.version)
        assertEquals("q-1", form.questionId)
        assertEquals(1, form.questions.size)
        assertEquals(0, form.active)
        val q = form.questions[0]
        assertEquals("", q.header)
        assertEquals("Deploy now?", q.question)
        assertTrue(q.allowFreeResponse)
        assertEquals(2, q.options.size)
        assertEquals("Yes", q.options[0].label)
        assertEquals("ship it", q.options[0].description)
        assertEquals("No", q.options[1].label)
        assertNull(q.options[1].description)
    }

    @Test
    fun stateAndAssistantMessage() {
        val state = parse(
            """{"seq":1,"kind":"state","at":"t","data":{"thinking":true,"mode":"plan",
                "session_id":"headless-abc","messages_count":3,"tokens_used":1234}}"""
        ) as HeadlessEvent.State
        assertTrue(state.thinking)
        assertEquals("headless-abc", state.sessionId)
        assertEquals(3, state.messagesCount)
        assertEquals(1234L, state.tokensUsed)

        val msg = parse(
            """{"seq":2,"kind":"assistant_message","at":"t","data":
               {"content":"done","thinking":"pondered","meta":"gpt","tools_count":2,
                "tokens_used":99,"mode":"coding"}}"""
        ) as HeadlessEvent.AssistantMessage
        assertEquals("done", msg.content)
        assertEquals("pondered", msg.thinking)
        assertEquals("gpt", msg.meta)
        assertEquals(2, msg.toolsCount)
        assertEquals(99L, msg.tokensUsed)
    }

    @Test
    fun approvalRequestAndBanner() {
        val ap = parse(
            """{"seq":5,"kind":"approval_request","at":"t","data":
               {"tool_id":"tool-9","command":"rm -rf build","reason":"destructive",
                "segments":["rm","-rf","build"],"mode":"coding"}}"""
        ) as HeadlessEvent.ApprovalRequest
        assertEquals("tool-9", ap.toolId)
        assertEquals("rm -rf build", ap.command)
        assertEquals("destructive", ap.reason)
        assertEquals(listOf("rm", "-rf", "build"), ap.segments)

        val banner = parse(
            """{"seq":6,"kind":"banner","at":"t","data":{"text":"hello","level":"warn"}}"""
        ) as HeadlessEvent.Banner
        assertEquals("hello", banner.text)
        assertEquals("warn", banner.level)
        assertNull(banner.mode)
    }

    @Test
    fun unknownKind_isKeptGenerically() {
        val ev = parse(
            """{"seq":3,"kind":"remote_started","at":"t","data":{"cwd":"/x","mode":"plan"}}"""
        ) as HeadlessEvent.Unknown
        assertEquals("remote_started", ev.kind)
        assertEquals("plan", ev.mode)
        assertEquals("/x", ev.data["cwd"]!!.jsonPrimitive.content)

        // Not-an-event payloads are rejected, not crashed on.
        assertNull(parse("""{"foo":1}"""))
    }
}
