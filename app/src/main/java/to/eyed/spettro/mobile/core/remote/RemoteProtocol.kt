package to.eyed.spettro.mobile.core.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The Spettro Remote wire protocol (Protocol B), mirroring
 * `Spettro/Core/Remote/RemoteProtocol.swift`. Key spellings are exact:
 * `hostID`, `chatID`, `deviceID`, `promptID`, `configID` are capital-ID.
 *
 * Payloads that carry agent (ACP) data cross this layer as raw
 * [JsonElement]/[JsonObject] — `core.acp` parses them, not this package.
 */
object RemoteProtocolInfo {
    /** Bumped when the wire surface changes incompatibly. */
    const val VERSION = 1

    /** Bonjour service type hosts advertise under. */
    const val BONJOUR_SERVICE_TYPE = "_spettro-remote._tcp"

    /** TXT record keys on the Bonjour advertisement. */
    object TxtKey {
        const val HOST_ID = "hostid"
        const val HOST_NAME = "name"
        const val HOST_KIND = "kind"
        const val PROTOCOL_VERSION = "v"
    }
}

/** Every method in the protocol, namespaced under `_spettro/remote/`. */
object RemoteMethod {
    // Handshake.
    const val HELLO = "_spettro/remote/hello"
    const val AUTH = "_spettro/remote/auth"

    // Chats.
    const val CHATS_LIST = "_spettro/remote/chats/list"
    const val CHATS_OPEN = "_spettro/remote/chats/open"
    const val CHATS_CLOSE = "_spettro/remote/chats/close"
    const val CHATS_NEW = "_spettro/remote/chats/new"
    const val CHATS_DELETE = "_spettro/remote/chats/delete"
    const val CHATS_FLAG = "_spettro/remote/chats/flag"

    // Driving a turn.
    const val CHATS_PROMPT = "_spettro/remote/chats/prompt"
    const val CHATS_CANCEL = "_spettro/remote/chats/cancel"
    const val CHATS_CONFIG = "_spettro/remote/chats/config"

    // Host -> client notifications.
    const val CHAT_UPDATE = "_spettro/remote/chat/update"
    const val CHAT_USER = "_spettro/remote/chat/user"
    const val CHAT_STATE = "_spettro/remote/chat/state"
    const val CHAT_REMOVED = "_spettro/remote/chat/removed"
    const val HOST_STATE = "_spettro/remote/host/state"

    // Blocking prompts.
    const val PERMISSION_ASK = "_spettro/remote/permission/ask"
    const val PERMISSION_REPLY = "_spettro/remote/permission/reply"
    const val PERMISSION_RESOLVED = "_spettro/remote/permission/resolved"
    const val QUESTION_ASK = "_spettro/remote/question/ask"
    const val QUESTION_REPLY = "_spettro/remote/question/reply"
    const val QUESTION_RESOLVED = "_spettro/remote/question/resolved"

    // Agent passthrough.
    const val AGENT_CALL = "_spettro/remote/agent/call"
    const val AGENT_NOTIFICATION = "_spettro/remote/agent/notification"

    // Projects.
    const val PROJECTS_LIST = "_spettro/remote/projects/list"
}

/**
 * Auth failure codes, and which of them are permanent (retrying can never
 * fix them — only a fresh pairing can).
 */
object RemoteAuthError {
    /** Unknown device and no pairing window open. */
    const val NOT_PAIRED = -33001

    /** The MAC did not verify — wrong key or stale QR. */
    const val BAD_PROOF = -33002

    /** The pairing window closed before the auth arrived. */
    const val PAIRING_EXPIRED = -33003

    /** The client speaks a protocol version this host cannot serve. */
    const val VERSION_MISMATCH = -33004

    /** The user revoked this device on the host. */
    const val REVOKED = -33005

    fun isAuthCode(code: Int): Boolean = code in REVOKED..NOT_PAIRED

    /** True for verdicts that stay wrong no matter how often we retry. */
    fun isPermanent(code: Int): Boolean =
        code == NOT_PAIRED || code == VERSION_MISMATCH || code == REVOKED
}

// MARK: - Handshake

/** Host -> client, first frame on the socket. */
@Serializable
data class RemoteHello(
    val protocolVersion: Int,
    val hostID: String,
    val hostName: String,
    /** `app` (Spettro.app on macOS) or `tui` (the CLI via /remote). */
    val hostKind: String = "app",
    val hostVersion: String = "",
    /** Random per-connection nonce, base64url. */
    val challenge: String,
    /** True while the host is showing a pairing QR. */
    val pairingOpen: Boolean = false,
)

/** Client -> host auth request. */
@Serializable
data class RemoteAuthRequest(
    /** [MODE_PAIR] or [MODE_RESUME]. */
    val mode: String,
    /** Stable per-install identifier for this phone. */
    val deviceID: String,
    /** Shown in the host's paired-devices list. */
    val deviceName: String,
    /** e.g. "Android 15". */
    val platform: String,
    /** base64url(HMAC-SHA256(key, challenge || utf8(hostID))). */
    val proof: String,
    val protocolVersion: Int = RemoteProtocolInfo.VERSION,
) {
    companion object {
        const val MODE_PAIR = "pair"
        const val MODE_RESUME = "resume"
    }
}

@Serializable
data class RemoteAuthResult(
    /** base64url device key, present ONLY on a successful `pair`. */
    val deviceKey: String? = null,
    val hostID: String,
    val hostName: String,
    val hostKind: String = "app",
    /** Whether the host currently has a live agent behind it. */
    val agentReady: Boolean = false,
)

// MARK: - Chats

/** One row in the chat list. Deliberately transcript-free. */
@Serializable
data class ChatSummary(
    val id: String,
    val title: String = "",
    val projectPath: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isBusy: Boolean = false,
    val messageCount: Int = 0,
    val preview: String = "",
)

@Serializable
data class ChatListResult(
    val chats: List<ChatSummary> = emptyList(),
)

/**
 * Everything needed to render a chat. `chat` is the host's raw StoredSession
 * snapshot; the ACP-shaped fields ride through verbatim for `core.acp`.
 */
@Serializable
data class ChatOpenResult(
    val chat: JsonObject,
    val configOptions: JsonElement? = null,
    val commands: JsonElement? = null,
    val plan: JsonElement? = null,
    val usage: JsonElement? = null,
    val isBusy: Boolean = false,
)

/** A folder the host can open a chat in. */
@Serializable
data class RemoteProject(
    val path: String,
    val name: String = "",
    val chatCount: Int = 0,
)

@Serializable
data class ProjectsListResult(
    val projects: List<RemoteProject> = emptyList(),
)

// MARK: - Notification payloads

/** An image the user attached to a prompt. base64 is standard, padded. */
@Serializable
data class RemoteAttachment(
    val base64: String,
    val mimeType: String,
)

@Serializable
data class ChatUpdateParams(
    val chatID: String,
    /** The ACP `session/update` `update` object verbatim. */
    val update: JsonObject,
)

@Serializable
data class ChatUserParams(
    val chatID: String,
    val text: String = "",
    val attachments: List<RemoteAttachment> = emptyList(),
    /** ISO-8601. */
    val timestamp: String = "",
)

@Serializable
data class RemoteNotice(
    val text: String,
    val isError: Boolean = false,
)

@Serializable
data class ChatStateParams(
    val chat: ChatSummary,
    /** Present when the turn just ended. */
    val stopReason: String? = null,
    val notice: RemoteNotice? = null,
)

@Serializable
data class ChatRemovedParams(
    val chatID: String,
)

@Serializable
data class HostStateParams(
    val agentReady: Boolean = false,
    /** True when the host is going away deliberately. */
    val shuttingDown: Boolean = false,
    val message: String? = null,
)

@Serializable
data class PermissionAskParams(
    val promptID: String,
    val chatID: String? = null,
    /** The ACP request payload verbatim. */
    val request: JsonObject,
)

@Serializable
data class QuestionAskParams(
    val promptID: String,
    val chatID: String? = null,
    /** The ask-user form verbatim. */
    val request: JsonObject,
)

@Serializable
data class PromptResolvedParams(
    val promptID: String,
    /** Who resolved it, for a short "answered on Mac" note. */
    val resolvedBy: String? = null,
)

@Serializable
data class AgentNotificationParams(
    val method: String,
    val params: JsonElement? = null,
)
