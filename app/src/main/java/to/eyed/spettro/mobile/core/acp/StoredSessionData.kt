package to.eyed.spettro.mobile.core.acp

import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The decoded shape of one persisted chat session (`StoredSession` in the
 * Swift host's SessionStore.swift), as delivered by `chats/open`.
 *
 * Transcript items are externally tagged (`{"message": {...}}` /
 * `{"tool": {...}}`); Swift's synthesized Codable additionally nests the
 * payload under `"_0"`, so both forms are accepted.
 *
 * A host with the `snapshotV2` feature adds optional keys the Swift shape
 * never had (a message's steering state and reasoning span, a tool's images
 * and workflow state, …); every one of them is optional here, so a snapshot
 * from either kind of host parses.
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
        /** `sending` | `queued` | `delivered`, for a message sent mid-turn. */
        val steering: String? = null,
        /** Still arriving when the snapshot was taken. */
        val isStreaming: Boolean = false,
        /** A reasoning bubble's first and latest chunk, epoch millis. */
        val startedAt: Long? = null,
        val endedAt: Long? = null,
        /** Project-relative files the message @-mentioned. */
        val mentions: List<String> = emptyList(),
        /** A slash command's reply, in the CLI's own plain text. */
        val plain: Boolean = false,
        /** The divider a `/clear` turn leaves. */
        val contextCleared: Boolean = false,
        /** The error notice a turn ended on. */
        val endsTurn: Boolean = false,
    ) : StoredItemData()

    data class Tool(
        val id: String,
        val title: String,
        val kind: String? = null,
        val status: String = "unknown",
        val output: String = "",
        val diffs: List<StoredDiffData> = emptyList(),
        val locations: List<AcpToolLocation> = emptyList(),
        val argsJSON: String? = null,
        val timestamp: String = "",
        val images: List<AcpToolImage> = emptyList(),
        val rawOutput: String? = null,
        /** The user turned this call down in its approval. */
        val denied: Boolean = false,
        /** A workflow card's run state (`_meta["spettro.app/workflow"]`). */
        val workflow: JsonObject? = null,
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
                steering = obj["steering"].asString,
                isStreaming = obj["isStreaming"].asBool ?: false,
                startedAt = epochMillis(obj["startedAt"]),
                endedAt = epochMillis(obj["endedAt"]),
                mentions = (obj["mentions"].asArr ?: emptyList()).mapNotNull { it.asString },
                plain = obj["plain"].asBool ?: false,
                contextCleared = obj["contextCleared"].asBool ?: false,
                endsTurn = obj["endsTurn"].asBool ?: false,
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
                // Bare paths on the wire today; `{path, line}` accepted too.
                locations = (obj["locations"].asArr ?: emptyList()).mapNotNull { loc ->
                    loc.asString?.let { AcpToolLocation(it) } ?: AcpToolLocation.parse(loc)
                },
                argsJSON = obj["argsJSON"].asString,
                timestamp = obj["timestamp"].asString ?: "",
                images = (obj["images"].asArr ?: emptyList()).mapNotNull { img ->
                    val i = img.asObj ?: return@mapNotNull null
                    val data = i["data"].asString?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    AcpToolImage(data, i["mimeType"].asString ?: return@mapNotNull null)
                }.take(AcpToolImage.MAX_PER_CALL),
                rawOutput = obj["rawOutput"].asString,
                denied = obj["denied"].asBool ?: false,
                workflow = obj["workflow"].asObj,
            )
        }

        /**
         * An RFC 3339 instant (with or without fractional seconds) as epoch
         * millis; null when absent or malformed.
         */
        private fun epochMillis(element: JsonElement?): Long? {
            val text = element.asString ?: return null
            return try {
                Instant.parse(text).toEpochMilli()
            } catch (_: Exception) {
                null
            }
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
