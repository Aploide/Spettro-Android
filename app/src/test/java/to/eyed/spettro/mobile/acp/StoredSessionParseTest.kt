package to.eyed.spettro.mobile.acp

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpParser
import to.eyed.spettro.mobile.core.acp.StoredItemData

class StoredSessionParseTest {

    private val sample = """
        {
          "id": "0B54E6C1-7E27-4E5D-9E3B-111111111111",
          "acpSessionId": "acp-abc",
          "projectPath": "/Users/dev/project",
          "title": "Fix the parser",
          "createdAt": "2026-07-01T10:00:00Z",
          "isPinned": true,
          "isArchived": false,
          "items": [
            {"message": {"_0": {
              "id": "9C10", "role": "user", "noticeIsError": false,
              "text": "hello",
              "attachments": [{"id": "A1", "data": "aGVsbG8=", "mimeType": "image/jpeg"}],
              "timestamp": "2026-07-01T10:01:00Z"}}},
            {"message": {"_0": {
              "id": "9C11", "role": "notice", "noticeIsError": true,
              "text": "boom", "timestamp": "2026-07-01T10:01:30Z"}}},
            {"tool": {"_0": {
              "id": "call-9", "title": "read {\"path\":\"/a/b\"}", "kind": "read",
              "status": "completed", "output": "file text",
              "diffs": [{"path": "/a/b", "newText": "x"}],
              "locations": ["/a/b"], "argsJSON": "{\"path\":\"/a/b\"}",
              "timestamp": "2026-07-01T10:02:00Z"}}}
          ],
          "configOptions": [
            {"id": "mode", "name": "Mode",
             "kind": {"select": {"current": "code", "groups": [
               {"options": [{"name": "Code", "value": "code"}]}]}}}
          ]
        }
    """.trimIndent()

    @Test
    fun decodesFullSnapshotWithSwiftEnumWrappers() {
        val stored = AcpParser.parseStoredSession(SpettroJson.parseToJsonElement(sample).jsonObject)
        assertEquals("0B54E6C1-7E27-4E5D-9E3B-111111111111", stored.id)
        assertEquals("acp-abc", stored.acpSessionId)
        assertEquals("/Users/dev/project", stored.projectPath)
        assertEquals("Fix the parser", stored.title)
        assertTrue(stored.isPinned)
        assertEquals(3, stored.items.size)

        val msg = stored.items[0] as StoredItemData.Message
        assertEquals("user", msg.role)
        assertEquals("hello", msg.text)
        assertEquals(1, msg.attachments.size)
        assertEquals("aGVsbG8=", msg.attachments[0].dataBase64)

        val notice = stored.items[1] as StoredItemData.Message
        assertEquals("notice", notice.role)
        assertTrue(notice.noticeIsError)

        val tool = stored.items[2] as StoredItemData.Tool
        assertEquals("call-9", tool.id)
        assertEquals("completed", tool.status)
        assertEquals(1, tool.diffs.size)
        assertNull(tool.diffs[0].oldText)
        assertEquals("{\"path\":\"/a/b\"}", tool.argsJSON)

        val cfg = stored.configOptions!!
        assertEquals(1, cfg.size)
        assertEquals(
            AcpConfigOption.Kind.Select("code", listOf(
                AcpConfigOption.OptionGroup(null, listOf(AcpConfigOption.Opt("Code", "code")))
            )),
            cfg[0].kind,
        )
    }

    @Test
    fun decodesItemsWithoutUnderscoreWrapper() {
        val json = """
            {"id": "X", "projectPath": "/p", "title": "t", "createdAt": "2026-01-01T00:00:00Z",
             "isPinned": false, "isArchived": false,
             "items": [
               {"message": {"id": "m1", "role": "assistant", "noticeIsError": false,
                 "text": "answer", "timestamp": "2026-01-01T00:00:01Z"}},
               {"tool": {"id": "t1", "title": "bash", "status": "failed",
                 "output": "", "diffs": [], "locations": [],
                 "timestamp": "2026-01-01T00:00:02Z"}}]}
        """.trimIndent()
        val stored = AcpParser.parseStoredSession(SpettroJson.parseToJsonElement(json).jsonObject)
        assertEquals(2, stored.items.size)
        assertEquals("assistant", (stored.items[0] as StoredItemData.Message).role)
        assertEquals("failed", (stored.items[1] as StoredItemData.Tool).status)
        assertNull(stored.configOptions)
        assertNull(stored.acpSessionId)
    }

    @Test
    fun malformedItemsAreSkipped() {
        val json = """
            {"id": "X", "projectPath": "/p", "title": "t", "createdAt": "c",
             "isPinned": false, "isArchived": false,
             "items": [
               {"unknown": {"id": "?"}},
               {"tool": {"title": "missing id"}},
               {"message": {"id": "ok", "role": "user", "noticeIsError": false,
                 "text": "kept", "timestamp": "ts"}}]}
        """.trimIndent()
        val stored = AcpParser.parseStoredSession(SpettroJson.parseToJsonElement(json).jsonObject)
        assertEquals(1, stored.items.size)
        assertEquals("kept", (stored.items[0] as StoredItemData.Message).text)
    }
}
