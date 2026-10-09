package to.eyed.spettro.mobile.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest

/**
 * What an approval prompt says, worked out from the request alone so it can
 * be tested without drawing anything. Port of the desktop's `prompts.ts`
 * (and `permissionSentence` from its AppModel).
 *
 * The CLI describes an approval in pieces: the whole command or URL in a
 * fenced block, a plain reason line, sometimes a second fenced block listing
 * what "Always allow" would remember — and a diff block for a file change.
 * The options come with ACP kinds; the prompt puts them in one fixed order
 * whatever order they arrive in, so the destructive choice never lands where
 * Allow was a moment ago.
 */
object Prompts {

    // MARK: Title

    /**
     * The request with what it leaves to its card filled in: the card's kind
     * when it names none, and a title — the card's, else a sentence written
     * from the kind ([permissionSentence]) — when it came with none.
     */
    fun AcpPermissionRequest.describedBy(card: ToolCallItem?): AcpPermissionRequest {
        val kind = toolKind ?: card?.kind
        val described = copy(toolKind = kind)
        if (title.isNotEmpty()) return described
        val cardTitle = card?.title?.takeIf { it.isNotEmpty() }
        return described.copy(title = cardTitle ?: permissionSentence(described))
    }

    /**
     * A title for a request that came with none and whose card isn't here —
     * written from the tool kind, naming the file when there is one.
     */
    fun permissionSentence(request: AcpPermissionRequest): String {
        val file = fileName(request)
        if (request.isCompact) return "Compact the conversation"
        return when (request.toolKind) {
            "execute" -> "Run a command"
            "edit" -> if (file != null) "Edit $file" else "Edit a file"
            "delete" -> if (file != null) "Delete $file" else "Delete a file"
            "move" -> if (file != null) "Move $file" else "Move a file"
            "read" -> if (file != null) "Read $file" else "Read a file"
            "search" -> "Search the project"
            "fetch" -> "Open a web page"
            else -> "Allow this action"
        }
    }

    // MARK: Options

    enum class OptionRole { ALLOW_ONCE, ALLOW_ALWAYS, REJECT, OTHER }

    data class CardOption(
        val option: AcpPermissionRequest.Option,
        val role: OptionRole,
        /** The button's own words. */
        val label: String,
        /**
         * The CLI's scope for "Always allow" ("Always allow this command"),
         * shown under the button; null when it would only repeat the label.
         */
        val caption: String?,
    )

    private val ALWAYS = Regex("\\b(always|remember)\\b")
    private val REJECT = Regex("\\b(deny|reject|decline|no|don't|cancel)\\b")
    private val ALLOW = Regex("\\b(allow|approve|yes|ok|run)\\b")
    private val BARE_ALWAYS_ALLOW = Regex("^always allow$", RegexOption.IGNORE_CASE)

    /**
     * The option's role from its ACP kind, else from its name: an agent that
     * leaves the kind out still says "Deny" or "Always allow".
     */
    fun optionRole(option: AcpPermissionRequest.Option): OptionRole {
        when (option.kind) {
            "allow_once" -> return OptionRole.ALLOW_ONCE
            "allow_always" -> return OptionRole.ALLOW_ALWAYS
            "reject_once", "reject_always" -> return OptionRole.REJECT
        }
        val name = option.name.lowercase()
        return when {
            ALWAYS.containsMatchIn(name) -> OptionRole.ALLOW_ALWAYS
            REJECT.containsMatchIn(name) -> OptionRole.REJECT
            ALLOW.containsMatchIn(name) -> OptionRole.ALLOW_ONCE
            else -> OptionRole.OTHER
        }
    }

    /**
     * The prompt's buttons, in the one order they always take: Allow once,
     * Always allow, Deny, then anything else the agent offered. The
     * compaction prompt keeps the CLI's own words ("Compact now" / "Continue
     * without compacting"); every tool approval gets the standard ones.
     */
    fun orderedOptions(request: AcpPermissionRequest): List<CardOption> =
        request.options
            .map { it to optionRole(it) }
            .sortedBy { (_, role) -> role.ordinal } // stable: ties keep the agent's order
            .map { (option, role) ->
                when {
                    request.isCompact || role == OptionRole.OTHER -> CardOption(option, role, option.name, null)
                    role == OptionRole.ALLOW_ALWAYS -> {
                        val name = option.name.trim()
                        val scoped = name.isNotEmpty() && !BARE_ALWAYS_ALLOW.matches(name)
                        CardOption(option, role, "Always allow", if (scoped) option.name else null)
                    }
                    role == OptionRole.ALLOW_ONCE -> CardOption(option, role, "Allow once", null)
                    else -> CardOption(option, role, "Deny", null)
                }
            }

    // MARK: Headline

    /** The file an approval is about, by name: the diff's path, else the first location. */
    private fun fileName(request: AcpPermissionRequest): String? {
        val path = request.diffs.firstOrNull()?.path ?: request.locations.firstOrNull()?.path
        return path?.let(::baseName)
    }

    /** The last component of a `/` or `\` path. */
    fun baseName(path: String): String =
        path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path

    /**
     * One sentence saying what is being asked, by kind. Kinds with no
     * sentence of their own say "Spettro needs your approval", and the
     * request's title says what for ([showsTitle]).
     */
    fun permissionHeadline(request: AcpPermissionRequest): String {
        if (request.isCompact) return "This conversation is almost full"
        val file = fileName(request)
        return when (request.toolKind) {
            "execute" -> "Spettro wants to run a command"
            "edit" -> if (file != null) "Spettro wants to edit $file" else "Spettro wants to edit a file"
            "delete" -> if (file != null) "Spettro wants to delete $file" else "Spettro wants to delete a file"
            "move" -> if (file != null) "Spettro wants to move $file" else "Spettro wants to move a file"
            "read" -> if (file != null) "Spettro wants to read $file" else "Spettro wants to read a file"
            "fetch" -> "Spettro wants to open a web page"
            "search" -> "Spettro wants to search"
            else -> GENERIC_HEADLINE
        }
    }

    private const val GENERIC_HEADLINE = "Spettro needs your approval"

    /** Whether the request's own title goes under the headline: only when the headline is generic. */
    fun showsTitle(request: AcpPermissionRequest): Boolean =
        !request.isCompact && permissionHeadline(request) == GENERIC_HEADLINE && request.title.isNotEmpty()

    // MARK: Body

    sealed class BodyBlock {
        data class Code(val lang: String, val text: String) : BodyBlock()
        data class Note(val text: String) : BodyBlock()
    }

    /**
     * A whole-text fenced block: ```lang\n…\n```, any fence length of three
     * or more, optionally followed by a blank line and a note.
     */
    private val FENCED = Regex("^(`{3,})([^\\n`]*)\\n([\\s\\S]*?)\\n\\1(?:\\n\\n([\\s\\S]+))?$")
    private val NETWORK = Regex("^network [\\w-]+ ([\\s\\S]+)$")
    private val NEEDS_APPROVAL = Regex("^needs approval:\\s*(.+)$", RegexOption.IGNORE_CASE)

    /**
     * The runtime's own reasons that only restate the headline: in its words,
     * jargon.
     */
    private val RESTATED_REASONS = setOf(
        "non-whitelisted command requires approval",
        "file modification requires approval",
    )

    /**
     * The approval's text content as the prompt draws it: fenced blocks as
     * code, everything else as notes, in the CLI's order. With no text and no
     * diff at all, the command from rawInput stands in — all an older CLI sent.
     */
    fun permissionBody(request: AcpPermissionRequest): List<BodyBlock> {
        val out = mutableListOf<BodyBlock>()
        fun code(lang: String, text: String) {
            // A network approval's block is the runtime's "network <tool>
            // <target>"; the headline already says what kind of access it
            // is, so the target is all the prompt shows.
            val network = if (lang.isEmpty()) NETWORK.matchEntire(text) else null
            out.add(BodyBlock.Code(lang, network?.groupValues?.get(1) ?: text))
        }
        fun note(text: String) {
            val plain = plainNote(text, out)
            if (plain.isNotEmpty()) out.add(BodyBlock.Note(plain))
        }
        for (raw in request.texts) {
            val text = raw.trimEnd()
            if (text.isEmpty()) continue
            val m = FENCED.matchEntire(text)
            if (m != null) {
                code(m.groupValues[2].trim(), m.groupValues[3])
                val after = m.groupValues[4]
                if (after.isNotEmpty()) note(after.trim())
            } else {
                note(text)
            }
        }
        if (out.isEmpty() && request.diffs.isEmpty() && !request.isCompact) {
            commandOf(request)?.let { out.add(BodyBlock.Code("sh", it)) }
        }
        return out
    }

    /**
     * A reason note in the user's words: the runtime's stock reasons are
     * dropped (the headline says it), and the "needs approval: <segments>"
     * line stays only when it names less than the whole command shown above —
     * the part of a compound command that wasn't already allowed.
     */
    private fun plainNote(text: String, before: List<BodyBlock>): String {
        val shown = before.filterIsInstance<BodyBlock.Code>().map { it.text.trim() }.toSet()
        return text.split("\n")
            .flatMap { line ->
                val trimmed = line.trim()
                if (trimmed.lowercase() in RESTATED_REASONS) return@flatMap emptyList()
                val needs = NEEDS_APPROVAL.matchEntire(trimmed)
                if (needs != null) {
                    val what = needs.groupValues[1].trim()
                    return@flatMap if (what in shown) emptyList() else listOf("Needs your approval: $what")
                }
                listOf(line)
            }
            .joinToString("\n")
            .trim()
    }

    /** rawInput.command, when the request carries one. */
    private fun commandOf(request: AcpPermissionRequest): String? {
        val command = (request.rawInput as? JsonObject)?.get("command") as? JsonPrimitive ?: return null
        return command.content.takeIf { command.isString && it.isNotBlank() }
    }

    private val COMPACT_TOKENS = Regex("~?(\\d+)\\s*/\\s*(\\d+)\\s*tokens")

    /**
     * The compaction prompt's title is the CLI's whole question ("Context
     * nearly full (~182000/200000 tokens). Compact conversation history
     * now?"); the prompt already asks it, so only the numbers are kept, as
     * words.
     */
    fun compactDetail(request: AcpPermissionRequest): String {
        val m = COMPACT_TOKENS.find(request.title)
        val lead = if (m != null) {
            "About ${tokens(m.groupValues[1].toLong())} of ${tokens(m.groupValues[2].toLong())} tokens are in use. "
        } else {
            ""
        }
        return lead +
            "Compacting replaces older messages with a summary so Spettro can keep going; " +
            "continuing without it may run out of room."
    }

    private fun tokens(n: Long): String = when {
        n >= 1_000_000 -> {
            val m = Math.round(n / 100_000.0) / 10.0
            if (m == Math.floor(m)) "${m.toLong()}M" else "${m}M"
        }
        n >= 1000 -> "${Math.round(n / 1000.0)}k"
        else -> n.toString()
    }
}
