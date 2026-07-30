package to.eyed.spettro.mobile.core.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import to.eyed.spettro.mobile.core.SpettroJson

/**
 * A host -> client notification, parsed from its JSON-RPC envelope.
 * ACP-shaped payloads (`update`, `request`) stay raw [JsonObject]s —
 * `core.acp` parses those.
 */
sealed class RemoteNotification {
    data class Hello(val hello: RemoteHello) : RemoteNotification()

    /** A streamed agent update; `update` is the ACP `session/update` body verbatim. */
    data class ChatUpdate(val chatID: String, val update: JsonObject) : RemoteNotification()

    /** A prompt someone else submitted (the sender never receives this echo). */
    data class ChatUser(
        val chatID: String,
        val text: String,
        val attachments: List<RemoteAttachment>,
        val timestamp: String,
    ) : RemoteNotification()

    /** Title/busy/flags changed, a chat appeared, or a turn ended. */
    data class ChatState(
        val chat: ChatSummary,
        val stopReason: String?,
        val notice: RemoteNotice?,
    ) : RemoteNotification()

    data class ChatRemoved(val chatID: String) : RemoteNotification()

    data class HostState(
        val agentReady: Boolean,
        val shuttingDown: Boolean,
        val message: String?,
    ) : RemoteNotification()

    data class PermissionAsk(
        val promptID: String,
        val chatID: String?,
        val request: JsonObject,
    ) : RemoteNotification()

    data class PermissionResolved(val promptID: String, val resolvedBy: String?) : RemoteNotification()

    data class QuestionAsk(
        val promptID: String,
        val chatID: String?,
        val request: JsonObject,
    ) : RemoteNotification()

    data class QuestionResolved(val promptID: String, val resolvedBy: String?) : RemoteNotification()

    /** An agent-originated `_spettro/...` notification relayed by the host. */
    data class AgentNotification(val method: String, val params: JsonElement?) : RemoteNotification()

    companion object {
        /**
         * Parses one inbound notification. Returns null for unknown methods
         * or payloads that do not decode — the caller logs and moves on.
         */
        fun parse(method: String, params: JsonElement?): RemoteNotification? {
            return try {
                when (method) {
                    RemoteMethod.HELLO -> params?.let {
                        Hello(SpettroJson.decodeFromJsonElement(RemoteHello.serializer(), it))
                    }
                    RemoteMethod.CHAT_UPDATE -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(ChatUpdateParams.serializer(), it)
                        ChatUpdate(p.chatID, p.update)
                    }
                    RemoteMethod.CHAT_USER -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(ChatUserParams.serializer(), it)
                        ChatUser(p.chatID, p.text, p.attachments, p.timestamp)
                    }
                    RemoteMethod.CHAT_STATE -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(ChatStateParams.serializer(), it)
                        ChatState(p.chat, p.stopReason, p.notice)
                    }
                    RemoteMethod.CHAT_REMOVED -> params?.let {
                        ChatRemoved(SpettroJson.decodeFromJsonElement(ChatRemovedParams.serializer(), it).chatID)
                    }
                    RemoteMethod.HOST_STATE -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(HostStateParams.serializer(), it)
                        HostState(p.agentReady, p.shuttingDown, p.message)
                    }
                    RemoteMethod.PERMISSION_ASK -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(PermissionAskParams.serializer(), it)
                        PermissionAsk(p.promptID, p.chatID, p.request)
                    }
                    RemoteMethod.PERMISSION_RESOLVED -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(PromptResolvedParams.serializer(), it)
                        PermissionResolved(p.promptID, p.resolvedBy)
                    }
                    RemoteMethod.QUESTION_ASK -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(QuestionAskParams.serializer(), it)
                        QuestionAsk(p.promptID, p.chatID, p.request)
                    }
                    RemoteMethod.QUESTION_RESOLVED -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(PromptResolvedParams.serializer(), it)
                        QuestionResolved(p.promptID, p.resolvedBy)
                    }
                    RemoteMethod.AGENT_NOTIFICATION -> params?.let {
                        val p = SpettroJson.decodeFromJsonElement(AgentNotificationParams.serializer(), it)
                        AgentNotification(p.method, p.params)
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
