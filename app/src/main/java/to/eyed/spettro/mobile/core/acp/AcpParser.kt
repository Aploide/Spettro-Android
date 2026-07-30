package to.eyed.spettro.mobile.core.acp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Entry points for parsing raw ACP payloads (delivered by the protocol layers
 * as [JsonObject]s) into the typed models of this package.
 *
 * Everything is decoded by hand: the discriminated unions carry their variant
 * tag in a sibling field, and several payloads arrive in two encodings (the
 * ACP wire shape vs. the Swift host's synthesized Codable shape).
 */
object AcpParser {

    // MARK: Session updates

    /**
     * Decodes one `session/update` / `chat/update` payload (the `update`
     * object whose tag field is `sessionUpdate`). Null for unknown tags.
     */
    fun parseSessionUpdate(update: JsonObject): AcpSessionUpdate? {
        return when (update["sessionUpdate"].asString ?: return null) {
            "agent_message_chunk" -> AcpSessionUpdate.MessageChunk(chunkText(update))
            "agent_thought_chunk" -> AcpSessionUpdate.ThoughtChunk(chunkText(update))
            "tool_call" -> AcpToolCallEvent.parse(update)?.let(AcpSessionUpdate::ToolCall)
            "tool_call_update" -> AcpToolCallEvent.parse(update)?.let(AcpSessionUpdate::ToolCallUpdate)
            "available_commands_update" -> AcpSessionUpdate.CommandsUpdate(
                (update["availableCommands"].asArr ?: emptyList()).mapNotNull(AcpCommand::parse)
            )
            "config_option_update" -> AcpSessionUpdate.ConfigUpdate(
                (update["configOptions"].asArr ?: emptyList()).mapNotNull(AcpConfigOption::parseAcp)
            )
            "plan" -> AcpSessionUpdate.Plan(
                (update["entries"].asArr ?: emptyList()).mapNotNull(AcpPlanEntry::parse)
            )
            "usage_update" -> AcpUsage.parse(update)?.let(AcpSessionUpdate::Usage)
            else -> null
        }
    }

    private fun chunkText(obj: JsonObject): String =
        obj["content"].asObj?.get("text").asString ?: ""

    // MARK: Config options (both shapes)

    /** ACP wire shape: `{type?, currentValue, options}`. */
    fun parseConfigOptionsAcp(arr: JsonArray): List<AcpConfigOption> =
        arr.mapNotNull(AcpConfigOption::parseAcp)

    /** Swift-synthesized shape: `{kind: {select: ...} | {boolean: ...}}`. */
    fun parseConfigOptionsStored(arr: JsonArray): List<AcpConfigOption> =
        arr.mapNotNull(AcpConfigOption::parseStored)

    // MARK: Commands / plan / usage (each parser accepts both encodings)

    fun parseCommands(arr: JsonArray): List<AcpCommand> = arr.mapNotNull(AcpCommand::parse)

    fun parsePlan(arr: JsonArray): List<AcpPlanEntry> = arr.mapNotNull(AcpPlanEntry::parse)

    fun parseUsage(element: JsonElement?): AcpUsage? = AcpUsage.parse(element)

    // MARK: Agent-originated requests

    /** Parses a `session/request_permission` params object. */
    fun parsePermissionRequest(req: JsonObject): AcpPermissionRequest? =
        AcpPermissionRequest.parse(req)

    /** Parses a `_spettro/question/ask` params object (v1 flat or v2 form). */
    fun parseQuestionRequest(req: JsonObject): AcpQuestionRequest? =
        AcpQuestionRequest.parse(req)

    /**
     * The question a permission request is really asking (mirrored into its
     * `_meta`), or null for an ordinary permission prompt.
     */
    fun parseQuestionFromPermission(permission: AcpPermissionRequest): AcpQuestionRequest? =
        AcpQuestionRequest.fromPermission(permission)

    // MARK: Persistence

    /** Decodes a `StoredSession` snapshot (the `chat` object of `chats/open`). */
    fun parseStoredSession(obj: JsonObject): StoredSessionData = StoredSessionData.parse(obj)
}
