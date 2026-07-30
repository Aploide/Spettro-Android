package to.eyed.spettro.mobile.core.acp

import androidx.compose.runtime.Immutable

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Typed models for the ACP payloads Spettro drives, parsed by hand because
 * their variant tags live in sibling fields and several of them arrive in two
 * different JSON encodings:
 *
 * - the raw ACP wire shape inside `chat/update` payloads, and
 * - the Swift-synthesized Codable shape inside `chats/open` results and
 *   `StoredSession` snapshots.
 *
 * Both parse into this one set of models.
 */

// MARK: - Config options

/**
 * A single session configuration option (mode / model / permission / thinking
 * select, or a boolean toggle like "ultra").
 */
@Immutable
data class AcpConfigOption(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val kind: Kind,
) {
    sealed class Kind {
        data class Select(val current: String, val groups: List<OptionGroup>) : Kind()
        data class Bool(val current: Boolean) : Kind()
    }

    data class Opt(
        val name: String,
        val value: String,
        val description: String? = null,
    )

    data class OptionGroup(
        /** null for ungrouped selects; the group's display name otherwise. */
        val name: String?,
        val options: List<Opt>,
    )

    /** The label of the currently selected option, for compact display. */
    val currentLabel: String
        get() = when (kind) {
            is Kind.Select -> {
                kind.groups.firstNotNullOfOrNull { g ->
                    g.options.firstOrNull { it.value == kind.current }?.name
                } ?: kind.current
            }
            is Kind.Bool -> if (kind.current) "On" else "Off"
        }

    companion object {
        /**
         * The ACP wire shape:
         * `{id, name, description?, category?, type?("boolean"), currentValue,
         *   options: [{name,value,description?}] or [{name, options:[...]}]}`.
         * Grouping is detected by the first element carrying a nested
         * `options` array.
         */
        fun parseAcp(element: JsonElement?): AcpConfigOption? {
            val obj = element.asObj ?: return null
            val id = obj["id"].asString ?: return null
            val name = obj["name"].asString ?: return null
            val description = obj["description"].asString
            val category = obj["category"].asString

            if (obj["type"].asString == "boolean") {
                val current = obj["currentValue"].asBool ?: false
                return AcpConfigOption(id, name, description, category, Kind.Bool(current))
            }

            // Default to select.
            val current = obj["currentValue"].asString ?: ""
            val groups = mutableListOf<OptionGroup>()
            val optionsArray = obj["options"].asArr
            if (optionsArray != null) {
                val first = optionsArray.firstOrNull().asObj
                if (first != null && first.containsKey("options")) {
                    for (g in optionsArray) {
                        val gObj = g.asObj ?: continue
                        val opts = (gObj["options"].asArr ?: emptyList())
                            .mapNotNull(::parseOpt)
                        groups.add(OptionGroup(gObj["name"].asString, opts))
                    }
                } else {
                    groups.add(OptionGroup(null, optionsArray.mapNotNull(::parseOpt)))
                }
            }
            return AcpConfigOption(id, name, description, category, Kind.Select(current, groups))
        }

        /**
         * The Swift-synthesized Codable shape used by `chats/open` and
         * `StoredSession`:
         * `{id, name, description?, category?,
         *   kind: {select: {current, groups: [{name?, options: [...]}]}}
         *       | {boolean: {current}}}`.
         */
        fun parseStored(element: JsonElement?): AcpConfigOption? {
            val obj = element.asObj ?: return null
            val id = obj["id"].asString ?: return null
            val name = obj["name"].asString ?: return null
            val description = obj["description"].asString
            val category = obj["category"].asString
            val kindObj = obj["kind"].asObj ?: return null

            kindObj["select"].asObj?.let { sel ->
                val current = sel["current"].asString ?: ""
                val groups = (sel["groups"].asArr ?: emptyList()).mapNotNull { g ->
                    val gObj = g.asObj ?: return@mapNotNull null
                    OptionGroup(
                        name = gObj["name"].asString,
                        options = (gObj["options"].asArr ?: emptyList()).mapNotNull(::parseOpt),
                    )
                }
                return AcpConfigOption(id, name, description, category, Kind.Select(current, groups))
            }
            kindObj["boolean"].asObj?.let { b ->
                val current = b["current"].asBool ?: false
                return AcpConfigOption(id, name, description, category, Kind.Bool(current))
            }
            return null
        }

        private fun parseOpt(element: JsonElement?): Opt? {
            val obj = element.asObj ?: return null
            val name = obj["name"].asString ?: return null
            val value = obj["value"].asString ?: return null
            return Opt(name, value, obj["description"].asString)
        }
    }
}

// MARK: - Available commands

/** One slash command the agent advertises. */
@Immutable
data class AcpCommand(
    val name: String,
    val description: String = "",
    val hint: String? = null,
) {
    companion object {
        /**
         * Parses either encoding: the ACP wire shape
         * `{name, description, input: {hint}}` or the Swift-synthesized shape
         * `{name, description, hint}`.
         */
        fun parse(element: JsonElement?): AcpCommand? {
            val obj = element.asObj ?: return null
            val name = obj["name"].asString ?: return null
            return AcpCommand(
                name = name,
                description = obj["description"].asString ?: "",
                hint = obj["input"].asObj?.get("hint").asString ?: obj["hint"].asString,
            )
        }
    }
}

// MARK: - Plan

/** One entry of the agent's published plan (task list). */
@Immutable
data class AcpPlanEntry(
    val content: String,
    val status: String = "pending",
    val priority: String? = null,
) {
    companion object {
        fun parse(element: JsonElement?): AcpPlanEntry? {
            val obj = element.asObj ?: return null
            val content = obj["content"].asString ?: return null
            return AcpPlanEntry(
                content = content,
                status = obj["status"].asString ?: "pending",
                priority = obj["priority"].asString,
            )
        }
    }
}

// MARK: - Usage

/**
 * Live context-window accounting: how many tokens of the model's window the
 * session currently occupies.
 */
@Immutable
data class AcpUsage(
    /** Tokens currently in context. */
    val used: Int,
    /** Total context window size in tokens. */
    val size: Int,
    /** Cumulative tokens processed this session (Spettro extension). */
    val tokensUsed: Int? = null,
) {
    companion object {
        /**
         * Parses either encoding: the ACP wire shape
         * `{used, size, _meta: {"spettro.app/tokensUsed": n}}` or the
         * Swift-synthesized shape `{used, size, totalTokens}`.
         * Null unless `used` is present and `size > 0`.
         */
        fun parse(element: JsonElement?): AcpUsage? {
            val obj = element.asObj ?: return null
            val used = obj["used"].asInt ?: return null
            val size = obj["size"].asInt ?: return null
            if (size <= 0) return null
            val total = obj["_meta"].asObj?.get("spettro.app/tokensUsed").asInt
                ?: obj["totalTokens"].asInt
            return AcpUsage(used, size, total)
        }
    }
}

// MARK: - Tool calls

enum class AcpToolStatus(val rawValue: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed"),
    UNKNOWN("unknown");

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED

    companion object {
        fun from(raw: String?): AcpToolStatus =
            entries.firstOrNull { it.rawValue == raw } ?: UNKNOWN
    }
}

/** One piece of a tool call's reported content. */
@Immutable
sealed class AcpToolContent {
    data class Text(val text: String) : AcpToolContent()
    data class Diff(val path: String, val oldText: String?, val newText: String) : AcpToolContent()

    val plainText: String
        get() = when (this) {
            is Text -> text
            is Diff -> "Edited $path"
        }

    companion object {
        fun parse(element: JsonElement?): AcpToolContent? {
            val obj = element.asObj ?: return null
            return when (obj["type"].asString) {
                "diff" -> {
                    val path = obj["path"].asString ?: return null
                    val newText = obj["newText"].asString ?: return null
                    Diff(path, obj["oldText"].asString, newText)
                }
                else -> {
                    // "content" wrapper holds a nested content block, usually text.
                    obj["content"].asObj?.get("text").asString?.let { return Text(it) }
                    obj["text"].asString?.let { return Text(it) }
                    null
                }
            }
        }
    }
}

/**
 * A tool-call update parsed from either a `tool_call` (start) or
 * `tool_call_update` session notification.
 */
@Immutable
data class AcpToolCallEvent(
    val toolCallId: String,
    val title: String? = null,
    val kind: String? = null,
    /** Null when the notification carried no `status` field at all. */
    val status: AcpToolStatus? = null,
    val rawInput: JsonElement? = null,
    val content: List<AcpToolContent> = emptyList(),
    val locations: List<String> = emptyList(),
) {
    companion object {
        fun parse(obj: JsonObject): AcpToolCallEvent? {
            val id = obj["toolCallId"].asString ?: return null
            val content = (obj["content"].asArr ?: emptyList()).mapNotNull(AcpToolContent::parse)
            val locations = (obj["locations"].asArr ?: emptyList())
                .mapNotNull { it.asObj?.get("path").asString }
            return AcpToolCallEvent(
                toolCallId = id,
                title = obj["title"].asString,
                kind = obj["kind"].asString,
                status = if (obj.containsKey("status")) AcpToolStatus.from(obj["status"].asString) else null,
                rawInput = obj["rawInput"],
                content = content,
                locations = locations,
            )
        }
    }
}

// MARK: - Session update (agent -> client streaming)

/** The decoded variants of a `session/update` notification we act on. */
sealed class AcpSessionUpdate {
    data class MessageChunk(val text: String) : AcpSessionUpdate()
    data class ThoughtChunk(val text: String) : AcpSessionUpdate()
    data class ToolCall(val event: AcpToolCallEvent) : AcpSessionUpdate()
    data class ToolCallUpdate(val event: AcpToolCallEvent) : AcpSessionUpdate()
    data class CommandsUpdate(val commands: List<AcpCommand>) : AcpSessionUpdate()
    data class ConfigUpdate(val options: List<AcpConfigOption>) : AcpSessionUpdate()
    data class Plan(val entries: List<AcpPlanEntry>) : AcpSessionUpdate()
    data class Usage(val usage: AcpUsage) : AcpSessionUpdate()
}
