package to.eyed.spettro.mobile.model

import to.eyed.spettro.mobile.core.acp.AcpCommand

/**
 * The slash commands in words a non-developer can choose between — a port of
 * the desktop's commandCopy.ts.
 *
 * The CLI describes its commands for a terminal: lowercase, in its own
 * vocabulary ("set token budget per request"), with a usage hint beside each.
 * The palette shows the sentence below instead and no usage hint — the
 * /models one asked people to type an API key into the chat, where it would
 * sit in the transcript and the session history. A command this file doesn't
 * know keeps the CLI's text.
 */
object CommandCopy {
    private val COPY: Map<String, String> = mapOf(
        "help" to "What you can type after /",
        "mode" to "Switch between Plan, Coding and Ask",
        "plan" to "Think it through and propose a plan first",
        "models" to "See the models you can use — or pick one in the model menu",
        "permission" to "How much Spettro may do without asking",
        "thinking" to "How hard the model thinks before answering",
        "ultra" to "Turn Ultra on or off — big tasks get a team of helpers",
        "goal" to "Keep working on its own toward a goal you describe",
        "memory" to "See or change what Spettro remembers",
        "compact" to "Shorten the conversation so it can keep going",
        "clear" to "Forget this conversation so far",
        "diff" to "Show what Spettro changed in your files",
        "workflows" to "Saved workflows: list, show or run one",
        "workflow-size" to "How big Ultra’s team of helpers can get",
        "budget" to "Limit how much each request may spend",
        "loop" to "Repeat a request on a schedule",
        "tasks" to "This session’s task list",
        "stats" to "How much this session has used",
        "jobs" to "Commands still running in the background",
        "hooks" to "Scripts set to run around Spettro’s actions",
        "skills" to "The skills Spettro can use, and where each comes from",
        "permissions" to "Permission level, with troubleshooting",
    )

    /** For developers: listed after everything else, and still found by name. */
    private val ADVANCED = setOf(
        "budget", "loop", "tasks", "stats", "jobs", "hooks", "skills", "permissions", "workflow-size",
    )

    /** Commands that run as they are from the palette rather than waiting
     *  for arguments: /models' argument form takes an API key. */
    private val RUN_BARE = setOf("models")

    /** The sentence the palette shows under the command's name, or null. */
    fun description(command: AcpCommand): String? =
        COPY[command.name] ?: command.description.ifEmpty { null }

    /** Whether picking the command leaves room for arguments ("/mode ")
     *  or sends it as it is ("/clear"). */
    fun takesArguments(command: AcpCommand): Boolean =
        !command.hint.isNullOrBlank() && command.name !in RUN_BARE

    /** The everyday commands first, the developer ones after; otherwise the
     *  CLI's order (the sort is stable). */
    fun order(commands: List<AcpCommand>): List<AcpCommand> =
        commands.sortedBy { if (it.name in ADVANCED) 1 else 0 }

    /**
     * The palette's list for a search: every command, ordered, when the
     * search is empty; otherwise the ones whose name starts with it (a
     * leading "/" is fine) or whose plain-words description contains it.
     */
    fun matching(commands: List<AcpCommand>, search: String): List<AcpCommand> {
        val q = search.trim().removePrefix("/").lowercase()
        val ordered = order(commands)
        if (q.isEmpty()) return ordered
        return ordered.filter { command ->
            command.name.lowercase().startsWith(q) ||
                description(command).orEmpty().lowercase().contains(q)
        }
    }
}
