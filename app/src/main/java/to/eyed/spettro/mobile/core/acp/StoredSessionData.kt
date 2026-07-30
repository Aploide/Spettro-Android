package to.eyed.spettro.mobile.core.acp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The decoded shape of one persisted chat session (`StoredSession` in the
 * Swift host's SessionStore.swift), as delivered by `chats/open`.
 *
 * Transcript items are externally tagged (`{"message": {...}}` /
 * `{"tool": {...}}`); Swift's synthesized Codable additionally nests the
 * payload under `"_0"`, so both forms are accepted.
 */
data class StoredSessionData(
    val id: String,
    val acpSessionId: String? = null,
    val projectPath: String = "",
    val title: String = "",
    val createdAt: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val items: List<StoredItemData> = emptyList(),
    /** Last-known session config, in the synthesized shape. Null for old snapshots. */
    val configOptions: List<AcpConfigOption>? = null,
) {
    companion object {
        fun parse(obj: JsonObject): StoredSessionData = StoredSessionData(
            id = obj["id"].asString ?: "",
            acpSessionId = obj["acpSessionId"].asString,
            projectPath = obj["projectPath"].asString ?: "",
            title = obj["title"].asString ?: "",
            createdAt = obj["createdAt"].asString ?: "",
            isPinned = obj["isPinned"].asBool ?: false,
            isArchived = obj["isArchived"].asBool ?: false,
            items = (obj["items"].asArr ?: emptyList()).mapNotNull(StoredItemData::parse),
            configOptions = obj["configOptions"].asArr
                ?.mapNotNull(AcpConfigOption::parseStored),
        )
    }
}

/** One persisted transcript entry. */
sealed class StoredItemData {

    data class Message(
        val id: String,
        /** user | assistant | reasoning | notice */
        val role: String,
        val noticeIsError: Boolean = false,
        val text: String = "",
        val attachments: List<StoredAttachmentData> = emptyList(),
        val timestamp: String = "",
    ) : StoredItemData()

    data class Tool(
        val id: String,
        val title: String,
        val kind: String? = null,
        val status: String = "unknown",
        val output: String = "",
        val diffs: List<StoredDiffData> = emptyList(),
        val locations: List<String> = emptyList(),
        val argsJSON: String? = null,
        val timestamp: String = "",
    ) : StoredItemData()

    companion object {
        fun parse(element: JsonElement?): StoredItemData? {
            val obj = element.asObj ?: return null
            obj["message"].asObj?.let { return parseMessage(unwrap(it)) }
            obj["tool"].asObj?.let { return parseTool(unwrap(it)) }
            return null
        }

        /** Swift's enum synthesis nests the unlabeled payload under "_0". */
        private fun unwrap(obj: JsonObject): JsonObject = obj["_0"].asObj ?: obj

        private fun parseMessage(obj: JsonObject): Message? {
            val id = obj["id"].asString ?: return null
            return Message(
                id = id,
                role = obj["role"].asString ?: "user",
                noticeIsError = obj["noticeIsError"].asBool ?: false,
                text = obj["text"].asString ?: "",
                attachments = (obj["attachments"].asArr ?: emptyList()).mapNotNull { att ->
                    val a = att.asObj ?: return@mapNotNull null
                    StoredAttachmentData(
                        id = a["id"].asString ?: return@mapNotNull null,
                        dataBase64 = a["data"].asString ?: return@mapNotNull null,
                        mimeType = a["mimeType"].asString ?: "image/jpeg",
                    )
                },
                timestamp = obj["timestamp"].asString ?: "",
            )
        }

        private fun parseTool(obj: JsonObject): Tool? {
            val id = obj["id"].asString ?: return null
            return Tool(
                id = id,
                title = obj["title"].asString ?: "Tool call",
                kind = obj["kind"].asString,
                status = obj["status"].asString ?: "unknown",
                output = obj["output"].asString ?: "",
                diffs = (obj["diffs"].asArr ?: emptyList()).mapNotNull { d ->
                    val diff = d.asObj ?: return@mapNotNull null
                    StoredDiffData(
                        path = diff["path"].asString ?: return@mapNotNull null,
                        oldText = diff["oldText"].asString,
                        newText = diff["newText"].asString ?: "",
                    )
                },
                locations = (obj["locations"].asArr ?: emptyList()).mapNotNull { it.asString },
                argsJSON = obj["argsJSON"].asString,
                timestamp = obj["timestamp"].asString ?: "",
            )
        }
    }
}

/** One persisted image attachment; [dataBase64] is standard (padded) base64. */
data class StoredAttachmentData(
    val id: String,
    val dataBase64: String,
    val mimeType: String,
)

data class StoredDiffData(
    val path: String,
    val oldText: String? = null,
    val newText: String,
)
