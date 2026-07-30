package to.eyed.spettro.mobile.core.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import to.eyed.spettro.mobile.core.SpettroJson

/**
 * Typed wrappers over every client -> host request in the Spettro Remote
 * protocol. Built on [RemoteClient.request]; higher layers get real types
 * where the data is the host's, and raw [JsonElement]s where it is the
 * agent's (parsed by `core.acp`).
 */
class RemoteApi(private val requester: suspend (method: String, params: JsonObject) -> JsonElement) {

    constructor(client: RemoteClient) : this({ method, params -> client.request(method, params) })

    // MARK: Handshake

    /** Rarely called directly — [RemoteClient] authenticates itself — but part of the surface. */
    suspend fun auth(request: RemoteAuthRequest): RemoteAuthResult {
        val params = SpettroJson.encodeToJsonElement(RemoteAuthRequest.serializer(), request) as JsonObject
        return decode(requester(RemoteMethod.AUTH, params), RemoteAuthResult.serializer())
    }

    // MARK: Chats

    suspend fun chatsList(): List<ChatSummary> =
        decode(requester(RemoteMethod.CHATS_LIST, EMPTY), ChatListResult.serializer()).chats

    suspend fun chatsOpen(chatID: String): ChatOpenResult =
        decode(
            requester(RemoteMethod.CHATS_OPEN, buildJsonObject { put("chatID", chatID) }),
            ChatOpenResult.serializer(),
        )

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
    suspend fun prompt(chatID: String, blocks: List<JsonObject>) {
        requester(
            RemoteMethod.CHATS_PROMPT,
            buildJsonObject {
                put("chatID", chatID)
                putJsonArray("blocks") { blocks.forEach { add(it) } }
            },
        )
    }

    suspend fun cancel(chatID: String) {
        requester(RemoteMethod.CHATS_CANCEL, buildJsonObject { put("chatID", chatID) })
    }

    /** Exactly one of [stringValue]/[boolValue] should be set, matching the option's kind. */
    suspend fun config(chatID: String, configID: String, stringValue: String? = null, boolValue: Boolean? = null) {
        requester(
            RemoteMethod.CHATS_CONFIG,
            buildJsonObject {
                put("chatID", chatID)
                put("configID", configID)
                stringValue?.let { put("stringValue", it) }
                boolValue?.let { put("boolValue", it) }
            },
        )
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
