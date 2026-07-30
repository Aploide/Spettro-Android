package to.eyed.spettro.mobile.acp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpParser
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest

private fun obj(text: String): JsonObject = SpettroJson.parseToJsonElement(text).jsonObject

class AcpPermissionQuestionTest {

    // MARK: Permission requests

    @Test
    fun permissionRequestParses() {
        val req = AcpParser.parsePermissionRequest(
            obj(
                """
                {"sessionId":"sess-1",
                 "toolCall":{"title":"bash {\"command\":\"rm -rf build\"}","kind":"execute",
                             "rawInput":{"command":"rm -rf build"}},
                 "options":[
                   {"optionId":"allow","name":"Allow","kind":"allow_once"},
                   {"optionId":"always","name":"Always allow","kind":"allow_always"},
                   {"optionId":"reject","name":"Reject","kind":"reject_once"}]}
                """.trimIndent()
            )
        )
        assertNotNull(req)
        req!!
        assertEquals("sess-1", req.sessionId)
        assertEquals("bash {\"command\":\"rm -rf build\"}", req.title)
        assertEquals("execute", req.toolKind)
        assertEquals(3, req.options.size)
        assertEquals("allow_once", req.options[0].kind)
        assertFalse(req.isQuestion)
        assertNull(AcpParser.parseQuestionFromPermission(req))
    }

    @Test
    fun permissionRequestRequiresSessionIdAndToolCall() {
        assertNull(AcpParser.parsePermissionRequest(obj("""{"toolCall":{"title":"x"}}""")))
        assertNull(AcpParser.parsePermissionRequest(obj("""{"sessionId":"s"}""")))
    }

    @Test
    fun permissionRequestMissingTitleFallsBack() {
        val req = AcpParser.parsePermissionRequest(
            obj("""{"sessionId":"s","toolCall":{}}""")
        )
        assertEquals("Permission requested", req!!.title)
    }

    @Test
    fun questionInPermissionIsDetectedAndParsed() {
        val req = AcpParser.parsePermissionRequest(
            obj(
                """
                {"sessionId":"sess-2",
                 "toolCall":{"title":"Question"},
                 "options":[
                   {"optionId":"opt-a","name":"Postgres","kind":"allow_once",
                    "_meta":{"spettro.app/isRecommended":true}},
                   {"optionId":"custom","name":"Type my own","kind":"allow_once",
                    "_meta":{"spettro.app/isCustomInput":true}}],
                 "_meta":{"spettro.app/question":{
                   "question":"Which database?",
                   "allowCustomInput":true,
                   "options":[{"id":"opt-a","label":"Postgres","isRecommended":true}]}}}
                """.trimIndent()
            )
        )!!
        assertTrue(req.isQuestion)
        assertTrue(req.options[0].isRecommended)
        assertTrue(req.options[1].isCustomInput)

        val question = AcpParser.parseQuestionFromPermission(req)!!
        assertEquals(1, question.questions.size)
        assertEquals("Which database?", question.questions[0].question)
        assertEquals(
            AcpQuestionRequest.Transport.Permission("custom"),
            question.transport,
        )
        assertTrue(question.questions[0].options[0].isRecommended)
    }

    @Test
    fun questionInPermissionCustomIdFallsBackToPayloadFlag() {
        val req = AcpParser.parsePermissionRequest(
            obj(
                """
                {"sessionId":"s","toolCall":{"title":"Q"},
                 "options":[{"optionId":"a","name":"A","kind":"allow_once"}],
                 "_meta":{"spettro.app/question":{
                   "question":"Pick","allowCustomInput":true,
                   "options":[{"id":"a","label":"A"}]}}}
                """.trimIndent()
            )
        )!!
        val question = AcpParser.parseQuestionFromPermission(req)!!
        assertEquals(
            AcpQuestionRequest.Transport.Permission("custom"),
            question.transport,
        )
    }

    // MARK: Question requests (extension transport)

    @Test
    fun questionV1FlatParses() {
        val q = AcpParser.parseQuestionRequest(
            obj(
                """
                {"version":1,"sessionId":"sess-3","context":"Setting up storage",
                 "question":"Which database?",
                 "options":[
                   {"id":"pg","label":"Postgres","description":"Relational"},
                   {"id":"sqlite","label":"SQLite"}],
                 "multiSelect":false}
                """.trimIndent()
            )
        )!!
        assertEquals(AcpQuestionRequest.Transport.ExtensionCall(1), q.transport)
        assertEquals("sess-3", q.sessionId)
        assertEquals("Setting up storage", q.context)
        assertEquals(1, q.questions.size)
        val item = q.questions[0]
        assertEquals("q-0", item.id)                 // defaulted
        assertEquals("Question 1", item.header)      // defaulted
        assertEquals("Which database?", item.question)
        assertEquals(2, item.options.size)
        assertFalse(item.multiSelect)
        assertFalse(item.allowCustomInput)           // has options, flag absent
    }

    @Test
    fun questionV2FormParsesWithDefaults() {
        val q = AcpParser.parseQuestionRequest(
            obj(
                """
                {"version":2,"sessionId":"sess-4",
                 "questions":[
                   {"id":"db","header":"Database","question":"Which database?",
                    "options":[{"id":"pg","label":"Postgres"}],"multiSelect":true},
                   {"question":"Anything else?"},
                   {"header":"Empty"}]}
                """.trimIndent()
            )
        )!!
        assertEquals(AcpQuestionRequest.Transport.ExtensionCall(2), q.transport)
        // The degenerate question (no text, no options) is dropped.
        assertEquals(2, q.questions.size)

        assertEquals("db", q.questions[0].id)
        assertEquals("Database", q.questions[0].header)
        assertTrue(q.questions[0].multiSelect)
        assertFalse(q.questions[0].allowCustomInput)

        // Second question: defaults + forced custom input (no options).
        assertEquals("q-1", q.questions[1].id)
        assertEquals("Question 2", q.questions[1].header)
        assertTrue(q.questions[1].allowCustomInput)
    }

    @Test
    fun questionV2FallsBackToFlatFieldsWhenQuestionsAbsent() {
        val q = AcpParser.parseQuestionRequest(
            obj("""{"version":2,"sessionId":"s","question":"Only flat?"}""")
        )!!
        assertEquals(1, q.questions.size)
        assertEquals("Only flat?", q.questions[0].question)
        assertTrue(q.questions[0].allowCustomInput) // no options -> forced
    }

    @Test
    fun questionWithNothingAnswerableIsNull() {
        assertNull(AcpParser.parseQuestionRequest(obj("""{"version":2,"sessionId":"s"}""")))
        assertNull(AcpParser.parseQuestionRequest(obj("""{"version":1,"sessionId":"s","question":"   "}""")))
    }

    @Test
    fun blankFieldsAreTreatedAsAbsent() {
        val q = AcpParser.parseQuestionRequest(
            obj(
                """
                {"version":2,"sessionId":"s","context":"  ",
                 "questions":[{"id":"","header":" ","question":"Q?",
                   "options":[{"id":"a","label":"A","description":"  "}]}]}
                """.trimIndent()
            )
        )!!
        assertNull(q.context)
        assertEquals("q-0", q.questions[0].id)
        assertEquals("Question 1", q.questions[0].header)
        assertNull(q.questions[0].options[0].description)
    }

    // MARK: Answers

    @Test
    fun answerEncodesPlainSynthesizedShape() {
        val json = AcpQuestionAnswer(
            questionId = "db",
            optionIds = listOf("pg"),
            text = "prefer managed",
        ).toJson()
        assertEquals("db", json["questionId"]!!.toString().trim('"'))
        assertEquals("""["pg"]""", json["optionIds"].toString())
        assertEquals("\"prefer managed\"", json["text"].toString())
        assertNull(json["notes"])
        assertNull(json["kind"]) // the host adds the kind tag, not us
    }

    @Test
    fun answerWithNoSelectionKeepsEmptyOptionIds() {
        val json = AcpQuestionAnswer(questionId = "q-0", text = "my own words").toJson()
        assertEquals("[]", json["optionIds"].toString())
    }
}
