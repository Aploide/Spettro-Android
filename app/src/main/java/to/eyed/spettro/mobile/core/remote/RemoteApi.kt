package to.eyed.spettro.mobile.core.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import to.eyed.spettro.mobile.core.SpettroJson

/**
 * Typed wrappers over every client -> host request in the Spettro Remote
 * protocol. Built on [RemoteClient.request]; higher layers get real types
 * where the data is the host's, and raw [JsonElement]s where it is the
 * agent's (parsed by `core.acp`).
 */
class RemoteApi(
    private val requester: suspend (method: String, params: JsonObject) -> JsonElement,
    /** Like [requester], plus where on the wire the reply sat; null when the transport can't say. */
    private val markedRequester: suspend (method: String, params: JsonObject) -> Pair<JsonElement, FrameMark?> =
        { method, params -> requester(method, params) to null },
) {

    constructor(client: RemoteClient) : this(
        requester = { method, params -> client.request(method, params) },
        markedRequester = { method, params -> client.requestMarked(method, params) },
    )

    // MARK: Handshake

    /** Rarely called directly — [RemoteClient] authenticates itself — but part of the surface. */
    suspend fun auth(request: RemoteAuthRequest): RemoteAuthResult {
        val params = SpettroJson.encodeToJsonElement(RemoteAuthRequest.serializer(), request) as JsonObject
        return decode(requester(RemoteMethod.AUTH, params), RemoteAuthResult.serializer())
    }

    // MARK: Chats

    suspend fun chatsList(): List<ChatSummary> =
        decode(requester(RemoteMethod.CHATS_LIST, EMPTY), ChatListResult.serializer()).chats

    suspend fun chatsOpen(chatID: String): ChatOpenResult = chatsOpenMarked(chatID).first

    /**
     * [chatsOpen], also returning the reply's place on the wire: the
     * snapshot already holds every update that arrived before it, and none
     * that arrived after.
     */
    suspend fun chatsOpenMarked(chatID: String): Pair<ChatOpenResult, FrameMark?> {
        val (result, mark) = markedRequester(RemoteMethod.CHATS_OPEN, buildJsonObject { put("chatID", chatID) })
        return decode(result, ChatOpenResult.serializer()) to mark
    }

    suspend fun chatsClose(chatID: String) {
        requester(RemoteMethod.CHATS_CLOSE, buildJsonObject { put("chatID", chatID) })
    }

    suspend fun chatsNew(projectPath: String): ChatSummary =
        decode(
            requester(RemoteMethod.CHATS_NEW, buildJsonObject { put("projectPath", projectPath) }),
            ChatSummary.serializer(),
        )

    suspend fun chatsDelete(chatID: String) {
        requester(RemoteMethod.CHATS_DELETE, buildJsonObject { put("chatID", chatID) })
    }

    suspend fun chatsFlag(chatID: String, isPinned: Boolean? = null, isArchived: Boolean? = null) {
        requester(
            RemoteMethod.CHATS_FLAG,
            buildJsonObject {
                put("chatID", chatID)
                isPinned?.let { put("isPinned", it) }
                isArchived?.let { put("isArchived", it) }
            },
        )
    }

    // MARK: Driving a turn

    /** `blocks` are ACP content blocks (`{"type":"text",...}` / `{"type":"image",...}`), verbatim. */
    /** [mentions]: project-relative files the text @-mentions (feature `mentions`); left off when empty. */
    suspend fun prompt(chatID: String, blocks: List<JsonObject>, mentions: List<String> = emptyList()) {
        requester(
            RemoteMethod.CHATS_PROMPT,
            buildJsonObject {
                put("chatID", chatID)
                putJsonArray("blocks") { blocks.forEach { add(it) } }
                if (mentions.isNotEmpty()) putJsonArray("mentions") { mentions.forEach { add(it) } }
            },
        )
    }

    /** `chats/rename` (feature `rename`): every screen hears the new title as a chat/state. */
    suspend fun chatsRename(chatID: String, title: String) {
        requester(
            RemoteMethod.CHATS_RENAME,
            buildJsonObject {
                put("chatID", chatID)
                put("title", title)
            },
        )
    }

    /** `projects/files` (feature `projectFiles`): what the @-mention menu lists for a folder. */
    suspend fun projectFiles(projectPath: String): List<String> {
        val result = requester(RemoteMethod.PROJECT_FILES, buildJsonObject { put("projectPath", projectPath) })
        val files = (result as? JsonObject)?.get("files") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return files.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    }

    suspend fun cancel(chatID: String) {
        requester(RemoteMethod.CHATS_CANCEL, buildJsonObject { put("chatID", chatID) })
    }

    /**
     * Exactly one of [stringValue]/[boolValue] should be set, matching the
     * option's kind. A host with [RemoteFeature.CONFIG_RESULT] answers once
     * the set is done, with the chat's options after it (or an error, having
     * rolled back); an older one answers `null` at once, before trying.
     */
    suspend fun config(
        chatID: String,
        configID: String,
        stringValue: String? = null,
        boolValue: Boolean? = null,
    ): ChatConfigResult {
        val result = requester(
            RemoteMethod.CHATS_CONFIG,
            buildJsonObject {
                put("chatID", chatID)
                put("configID", configID)
                stringValue?.let { put("stringValue", it) }
                boolValue?.let { put("boolValue", it) }
            },
        )
        return if (result is JsonObject) decode(result, ChatConfigResult.serializer()) else ChatConfigResult()
    }

    // MARK: Blocking prompts

    /** Null [selectedOptionID] means the user dismissed the prompt. */
    suspend fun permissionReply(promptID: String, selectedOptionID: String? = null) {
        requester(
            RemoteMethod.PERMISSION_REPLY,
            buildJsonObject {
                put("promptID", promptID)
                selectedOptionID?.let { put("selectedOptionID", it) }
            },
        )
    }

    /** Null [answers] means the user declined the whole form. */
    suspend fun questionReply(promptID: String, answers: JsonElement? = null) {
        requester(
            RemoteMethod.QUESTION_REPLY,
            buildJsonObject {
                put("promptID", promptID)
                if (answers != null && answers !is JsonNull) put("answers", answers)
            },
        )
    }

    // MARK: Agent passthrough

    /** Forwards a `_spettro/...` call to the agent untouched and returns its raw result. */
    suspend fun agentCall(method: String, params: JsonElement? = null): JsonElement =
        requester(
            RemoteMethod.AGENT_CALL,
            buildJsonObject {
                put("method", method)
                if (params != null && params !is JsonNull) put("params", params)
            },
        )

    // MARK: Projects

    suspend fun projectsList(): List<RemoteProject> =
        decode(requester(RemoteMethod.PROJECTS_LIST, EMPTY), ProjectsListResult.serializer()).projects

    // MARK: Plumbing

    private fun <T> decode(element: JsonElement, serializer: kotlinx.serialization.DeserializationStrategy<T>): T =
        SpettroJson.decodeFromJsonElement(serializer, element)

    private companion object {
        val EMPTY = JsonObject(emptyMap())
    }
}
