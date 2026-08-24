package to.eyed.spettro.mobile.model

import androidx.compose.runtime.Immutable

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import to.eyed.spettro.mobile.core.SpettroJson
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.acp.asBool
import to.eyed.spettro.mobile.core.acp.asInt
import to.eyed.spettro.mobile.core.acp.asString

/**
 * The presentation models for one conversation's transcript: a flat, ordered
 * list of items (user/assistant messages, streamed reasoning, and tool calls).
 * Ported from TranscriptModels.swift.
 */

/** Current time as an ISO-8601 UTC string, the app-wide timestamp format. */
fun nowIso(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date())
}

/**
 * [nowIso]'s inverse: epoch millis for a timestamp this app wrote, or null for
 * anything it did not. Used to run a live clock off a tool call's start time,
 * which the CLI sets once and never updates.
 */
fun epochMillisOrNull(iso: String): Long? = try {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    fmt.parse(iso)?.time
} catch (_: Exception) {
    null
}

/**
 * A chat message bubble: the user's prompt, the assistant's answer, streamed
 * reasoning, or a local system notice.
 */
@Immutable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val attachments: List<ImageAttachment> = emptyList(),
    val isStreaming: Boolean = false,
    /** ISO-8601 string. */
    val timestamp: String = nowIso(),
) {
    sealed class Role {
        data object User : Role()
        data object Assistant : Role()
        data object Reasoning : Role()
        data class Notice(val isError: Boolean) : Role()
    }
}

/** A Material icon family for a tool row, chosen from the ACP tool kind. */
enum class ToolIcon(val materialIconName: String) {
    READ("Description"),
    EDIT("Edit"),
    DELETE("Delete"),
    MOVE("DriveFileMove"),
    SEARCH("Search"),
    EXECUTE("Terminal"),
    THINK("Psychology"),
    FETCH("Language"),
    SWITCH_MODE("Autorenew"),
    OTHER("Build");

    companion object {
        fun from(kind: String?): ToolIcon = when (kind) {
            "read" -> READ
            "edit" -> EDIT
            "delete" -> DELETE
            "move" -> MOVE
            "search" -> SEARCH
            "execute" -> EXECUTE
            "think" -> THINK
            "fetch" -> FETCH
            "switch_mode" -> SWITCH_MODE
            else -> OTHER
        }
    }
}

/** A tool invocation the agent reported, with its live status and output. */
@Immutable
data class ToolCallItem(
    /** The ACP toolCallId. */
    val id: String,
    val title: String,
    val kind: String? = null,
    val status: AcpToolStatus,
    val output: String = "",
    val diffs: List<ToolDiff> = emptyList(),
    val locations: List<String> = emptyList(),
    /**
     * The full tool arguments (ACP rawInput) as JSON; the title's inline args
     * are truncated by the CLI, so this is the reliable source.
     */
    val argsJSON: String? = null,
    /** ISO-8601 string. */
    val timestamp: String = nowIso(),
) {
    data class ToolDiff(
        val path: String,
        val oldText: String? = null,
        val newText: String,
    ) {
        /**
         * The lines that actually changed: both sides with the common leading
         * and trailing lines stripped. Not a real diff, but tight enough that
         * small edits show only their changed region.
         */
        val changedLines: Pair<List<String>, List<String>>
            get() {
                val old = oldText ?: ""
                val oldLines = if (old.isEmpty()) emptyList() else old.split("\n")
                val newLines = if (newText.isEmpty()) emptyList() else newText.split("\n")
                var start = 0
                while (start < oldLines.size && start < newLines.size &&
                    oldLines[start] == newLines[start]
                ) {
                    start++
                }
                var oldEnd = oldLines.size
                var newEnd = newLines.size
                while (oldEnd > start && newEnd > start &&
                    oldLines[oldEnd - 1] == newLines[newEnd - 1]
                ) {
                    oldEnd--
                    newEnd--
                }
                return Pair(oldLines.subList(start, oldEnd), newLines.subList(start, newEnd))
            }
    }

    /** The icon for this row, chosen from the ACP tool kind. */
    val icon: ToolIcon get() = ToolIcon.from(kind)

    // MARK: Human-readable presentation

    internal data class ParsedTitle(
        val agent: String?,
        val name: String,
        val args: JsonObject?,
    )

    /**
     * The CLI titles tool calls as `name {json args}` (optionally prefixed
     * with `[agent#n] `). Split that back apart so the UI can show a verb and
     * a readable detail instead of a raw JSON blob. Prefers the untruncated
     * [argsJSON] over the title's inline args, which the CLI cuts at 120 chars.
     */
    internal fun parsedTitle(): ParsedTitle {
        var text = title
        var agent: String? = null
        if (text.startsWith("[")) {
            val close = text.indexOf(']')
            if (close >= 0) {
                agent = text.substring(1, close)
                text = text.substring(close + 1).trim()
            }
        }
        val brace = text.indexOf('{')
        if (brace < 0) {
            val name = text.trim()
            return ParsedTitle(agent, name, argsJSON?.let(::parseJsonObject))
        }
        val name = text.substring(0, brace).trim()
        val argsText = argsJSON ?: text.substring(brace)
        val args = parseJsonObject(argsText)
        return ParsedTitle(agent, if (name.isEmpty()) title else name, args)
    }

    private fun argString(args: JsonObject?, vararg keys: String): String? {
        if (args == null) return null
        for (key in keys) {
            val s = args[key].asString
            if (!s.isNullOrEmpty()) return s
        }
        return null
    }

    /** A short verb for the row label, e.g. "Terminal" / "Read". */
    val displayName: String
        get() {
            val name = parsedTitle().name
            return when (kind) {
                "execute" -> "Terminal"
                "read" -> "Read"
                "edit" -> "Edit"
                "delete" -> "Delete"
                "move" -> "Move"
                "search" -> if (name == "ls") "List" else "Search"
                "fetch" -> "Fetch"
                "think" -> if (name.startsWith("agent")) "Agent" else "Plan"
                "switch_mode" -> "Mode"
                else -> if (name.isEmpty()) "Tool" else name.split(" ")
                    .joinToString(" ") { word ->
                        word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
                    }
            }
        }

    /**
     * The one-line human detail: the shell command, the file's short path,
     * the search pattern — never raw JSON.
     */
    val displayDetail: String
        get() {
            val (agent, name, args) = parsedTitle()
            var detail: String
            when (name) {
                "bash", "shell", "exec" ->
                    detail = argString(args, "command", "cmd") ?: name
                "agent" -> {
                    val who = argString(args, "agent") ?: "agent"
                    val task = argString(args, "task") ?: ""
                    detail = if (task.isEmpty()) who else "$who: $task"
                }
                else -> {
                    val path = argString(args, "path", "file", "file_path", "filename")
                        ?: locations.firstOrNull()
                    if (path != null) {
                        detail = shortPath(path)
                        argString(args, "pattern", "query", "regex")?.let {
                            detail = "$it in $detail"
                        }
                    } else {
                        val pattern = argString(
                            args, "content", "task", "description", "pattern", "query",
                            "regex", "url", "job_id", "id", "name",
                        )
                        if (pattern != null) {
                            detail = pattern
                        } else if (args != null && args.isNotEmpty()) {
                            // Unknown tool: show compact `key: value` pairs, not JSON.
                            detail = args.entries
                                .sortedBy { it.key }
                                .mapNotNull { (key, value) ->
                                    val text = value.asString
                                        ?: value.asInt?.toString()
                                        ?: value.asBool?.toString()
                                    text?.let { "$key: $it" }
                                }
                                .joinToString(", ")
                            if (detail.isEmpty()) detail = name
                        } else {
                            detail = if (name == title) "" else name
                        }
                    }
                }
            }
            detail = detail.replace("\n", " ⏎ ")
            if (agent != null) detail = "[$agent] $detail"
            return detail
        }

    // MARK: Sub-agent calls

    data class SubAgentCall(val agent: String, val task: String? = null)

    data class SubAgentResult(val status: String, val summary: String)

    /**
     * Non-null when this tool call spins up another agent (the `agent` tool):
     * the delegated agent's name and its task, so the UI can show a dedicated
     * sub-agent card instead of a generic tool row.
     */
    val subAgentCall: SubAgentCall?
        get() {
            val (_, name, args) = parsedTitle()
            if (!(name == "agent" || name.startsWith("agent "))) return null
            argString(args, "agent")?.let {
                return SubAgentCall(it, argString(args, "task"))
            }
            // Title form "agent explore: task text" without parseable args.
            // Swift's split(separator:maxSplits:omittingEmptySubsequences:)
            // semantics, which the other front-ends also implement: an empty
            // part before the colon means the whole remainder is the name, not
            // that the agent is anonymous.
            if (name.startsWith("agent ")) {
                val rest = name.removePrefix("agent ")
                val colon = rest.indexOf(':')
                if (colon < 0) return SubAgentCall(rest, null)
                val before = rest.substring(0, colon)
                val after = rest.substring(colon + 1)
                if (before.isEmpty()) return SubAgentCall(after, null)
                return SubAgentCall(before, after.trim())
            }
            return SubAgentCall("agent", null)
        }

    /**
     * The sub-agent's reported outcome, parsed from its JSON output
     * (`{"agent":…,"status":…,"summary":…}`). The CLI truncates long outputs,
     * leaving invalid JSON; the summary field is salvaged by hand so the card
     * still shows the agent's report.
     */
    val subAgentResult: SubAgentResult?
        get() {
            if (subAgentCall == null || output.isEmpty()) return null
            parseJsonObject(output)?.let { obj ->
                val summary = obj["summary"].asString ?: obj["output"].asString ?: ""
                if (summary.isNotEmpty() || obj.containsKey("status")) {
                    return SubAgentResult(obj["status"].asString ?: "ok", summary)
                }
            }
            extractJsonString("summary", output)?.let { summary ->
                return SubAgentResult(extractJsonString("status", output) ?: "ok", summary)
            }
            return null
        }

    /**
     * Added/removed line counts across this call's diffs, with the unchanged
     * prefix/suffix trimmed so pure insertions don't count the whole file.
     */
    data class DiffStat(val added: Int, val removed: Int)

    val diffStat: DiffStat?
        get() {
            if (diffs.isEmpty()) return null
            var added = 0
            var removed = 0
            for (diff in diffs) {
                val (old, new) = diff.changedLines
                removed += old.size
                added += new.size
            }
            return DiffStat(added, removed)
        }

    companion object {
        private fun parseJsonObject(text: String): JsonObject? = try {
            SpettroJson.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        }

        /**
         * Pulls `"field":"…"` out of possibly-truncated JSON, unescaping the
         * usual sequences; reads to the closing quote or the end of the text.
         */
        internal fun extractJsonString(field: String, from: String): String? {
            val marker = "\"$field\":\""
            val start = from.indexOf(marker)
            if (start < 0) return null
            val result = StringBuilder()
            var escaped = false
            for (ch in from.substring(start + marker.length)) {
                if (escaped) {
                    when (ch) {
                        'n' -> result.append('\n')
                        't' -> result.append('\t')
                        'r' -> {}
                        else -> result.append(ch)
                    }
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    break
                } else {
                    result.append(ch)
                }
            }
            return result.toString().takeIf { it.isNotEmpty() }
        }

        /** Collapses an absolute path to its last few meaningful components. */
        fun shortPath(path: String): String {
            val parts = path.split("/").filter { it.isNotEmpty() }
            if (parts.size <= 3) return path
            return parts.takeLast(3).joinToString("/")
        }
    }
}

/** One ordered entry in a transcript, with a stable id for list diffing. */
@Immutable
sealed class TranscriptItem {
    abstract val id: String

    data class Message(val message: ChatMessage) : TranscriptItem() {
        override val id: String get() = "msg-${message.id}"
    }

    data class Tool(val tool: ToolCallItem) : TranscriptItem() {
        override val id: String get() = "tool-${tool.id}"
    }
}
