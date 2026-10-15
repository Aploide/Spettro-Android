package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.core.acp.AcpCommand
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.acp.AcpUsage
import to.eyed.spettro.mobile.model.ChatMessage
import to.eyed.spettro.mobile.model.ToolCallItem
import to.eyed.spettro.mobile.model.TranscriptItem

/**
 * Fake data for the chat screen previews — mirrors the iOS preview fixtures
 * so both platforms are eyeballed against the same conversation.
 */
internal object ChatPreviewData {

    val userMessage = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.User,
            text = "Fix the crash when opening a chat while the host is restarting.",
        )
    )

    val assistantMessage = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.Assistant,
            text = """
                Found it — the client dereferences `openChat` before the resume
                handshake finishes. Two changes:

                1. Guard the navigation until `agentReady` flips.
                2. Re-emit the stored transcript on reconnect.

                ```kotlin
                if (!client.agentReady) return
                navigator.open(chatId)
                ```

                The stored transcript path already dedupes replayed chunks, so
                nothing else changes.
            """.trimIndent(),
        )
    )

    val streamingAssistant = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.Assistant,
            text = "Reading the resume path now — the handshake completes in `RemoteClient.start()` and",
            isStreaming = true,
        )
    )

    val reasoning = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.Reasoning,
            text = "The crash log points at a null session in ChatBody. The resume " +
                "path clears openChat before reattaching, so the navigation race " +
                "is the likely culprit. I should check RemoteClient.start() first.",
        )
    )

    val streamingReasoning = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.Reasoning,
            text = "Considering whether the resume handshake can complete before the UI navigates…",
            isStreaming = true,
        )
    )

    val infoNotice = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.Notice(isError = false),
            text = "Session resumed from the host.",
        )
    )

    val errorNotice = TranscriptItem.Message(
        ChatMessage(
            role = ChatMessage.Role.Notice(isError = true),
            text = "The agent stopped: connection to the host was lost.",
        )
    )

    val readTool = ToolCallItem(
        id = "tool-read",
        title = """read {"path":"/Users/dev/spettro/Core/Remote/RemoteClient.swift"}""",
        kind = "read",
        status = AcpToolStatus.COMPLETED,
        output = "final class RemoteClient {\n    private(set) var state: RemoteState = .unpaired\n    // 214 more lines\n}",
    )

    val executeTool = ToolCallItem(
        id = "tool-exec",
        title = """bash {"command":"./gradlew :app:compileDebugKotlin"}""",
        kind = "execute",
        status = AcpToolStatus.COMPLETED,
        output = "> Task :app:compileDebugKotlin\nBUILD SUCCESSFUL in 41s\n17 actionable tasks: 3 executed, 14 up-to-date",
    )

    val runningTool = ToolCallItem(
        id = "tool-running",
        title = """grep {"pattern":"agentReady","path":"app/src"}""",
        kind = "search",
        status = AcpToolStatus.IN_PROGRESS,
    )

    val failedTool = ToolCallItem(
        id = "tool-failed",
        title = """bash {"command":"./gradlew :app:testDebugUnitTest"}""",
        kind = "execute",
        status = AcpToolStatus.FAILED,
        output = "> Task :app:testDebugUnitTest FAILED\nRemoteClientTest > resumeRace FAILED\n    kotlin.AssertionError at RemoteClientTest.kt:88",
    )

    val editTool = ToolCallItem(
        id = "tool-edit",
        title = """edit {"path":"app/src/main/java/to/eyed/spettro/mobile/MainActivity.kt"}""",
        kind = "edit",
        status = AcpToolStatus.COMPLETED,
        diffs = listOf(
            ToolCallItem.ToolDiff(
                path = "app/src/main/java/to/eyed/spettro/mobile/MainActivity.kt",
                oldText = "fun open(chatId: String) {\n    navigator.open(chatId)\n}",
                newText = "fun open(chatId: String) {\n    if (!client.agentReady) return\n    navigator.open(chatId)\n}",
            ),
        ),
    )

    val agentTool = ToolCallItem(
        id = "tool-agent",
        title = """agent {"agent":"explore","task":"Find every caller of navigator.open and check for agentReady guards"}""",
        kind = "think",
        status = AcpToolStatus.COMPLETED,
        output = """{"agent":"explore","status":"ok","summary":"Three call sites; only `ChatListScreen` guards on `agentReady`. The deep-link path in `MainActivity` and the notification tap in `RootView` both navigate unconditionally."}""",
    )

    val runningAgentTool = ToolCallItem(
        id = "tool-agent-running",
        title = """agent {"agent":"plan","task":"Draft a fix for the resume race"}""",
        kind = "think",
        status = AcpToolStatus.IN_PROGRESS,
    )

    val transcript: List<TranscriptItem> = listOf(
        userMessage,
        reasoning,
        TranscriptItem.Tool(readTool),
        TranscriptItem.Tool(executeTool),
        TranscriptItem.Tool(editTool),
        TranscriptItem.Tool(agentTool),
        assistantMessage,
        infoNotice,
    )

    val streamingTranscript: List<TranscriptItem> = listOf(
        userMessage,
        streamingReasoning,
        TranscriptItem.Tool(runningTool),
        TranscriptItem.Tool(runningAgentTool),
        streamingAssistant,
    )

    private val bigOld = (1..40).joinToString("\n") { "    val line$it = compute($it)" }
    private val bigNew = bigOld
        .replace("    val line6 = compute(6)", "    val line6 = compute(6, cached = true)")
        .replace("    val line31 = compute(31)", "    val line31 = compute(31)\n    check(line31 > 0)")

    /**
     * A turn's tools as the desktop draws them: three reads and a search
     * folded into one line, an edit touching two places, a failed command
     * with its exit status, and a command waiting on the user's approval.
     */
    val toolsTranscript: List<TranscriptItem> = listOf(
        userMessage,
        TranscriptItem.Tool(readTool),
        TranscriptItem.Tool(readTool.copy(id = "tool-read-2", title = """file-read {"path":"/Users/dev/spettro/Core/Remote/RemoteApi.kt"}""", output = "a\nb\nc")),
        TranscriptItem.Tool(ToolCallItem(id = "tool-grep", title = """grep {"pattern":"agentReady","path":"/Users/dev/spettro/app/src"}""", kind = "search", status = AcpToolStatus.COMPLETED, output = "a.kt:12\nb.kt:40\nc.kt:7")),
        TranscriptItem.Tool(
            ToolCallItem(
                id = "tool-edit-2",
                title = """file-edit {"path":"/Users/dev/spettro/app/src/main/java/Resume.kt"}""",
                kind = "edit",
                status = AcpToolStatus.COMPLETED,
                diffs = listOf(ToolCallItem.ToolDiff("/Users/dev/spettro/app/src/main/java/Resume.kt", bigOld, bigNew)),
            ),
        ),
        TranscriptItem.Tool(failedTool.copy(output = failedTool.output + "\n[exit status 1]")),
        TranscriptItem.Tool(
            ToolCallItem(
                id = "tool-await",
                title = """bash {"command":"git push --force-with-lease origin fix/resume"}""",
                kind = "execute",
                status = AcpToolStatus.PENDING,
            ),
        ),
    )

    /** A turn that ended on an error: the card offers Try again. */
    val failedTurnTranscript: List<TranscriptItem> = listOf(
        userMessage,
        TranscriptItem.Tool(readTool),
        TranscriptItem.Message(
            ChatMessage(
                role = ChatMessage.Role.Notice(isError = true),
                text = "The model provider is overloaded. Wait a moment, then try again.",
                endsTurn = true,
            ),
        ),
    )

    val plan = listOf(
        AcpPlanEntry("Reproduce the resume crash", status = "completed"),
        AcpPlanEntry("Guard navigation on agentReady", status = "completed"),
        AcpPlanEntry("Re-emit transcript on reconnect", status = "in_progress"),
        AcpPlanEntry("Add a regression test", status = "pending"),
    )

    val usage = AcpUsage(used = 74_000, size = 200_000, tokensUsed = 210_000)

    val usageHot = AcpUsage(used = 186_000, size = 200_000, tokensUsed = 480_000)

    val commands = listOf(
        AcpCommand("compact", "Compact the conversation context"),
        AcpCommand("review", "Review the pending diff before applying"),
        AcpCommand("test", "Run the project's test suite", hint = "optional filter"),
        AcpCommand("commit", "Commit staged changes with a generated message"),
    )

    val configOptions = listOf(
        AcpConfigOption(
            id = "mode",
            name = "Mode",
            description = "What the agent is allowed to do this turn.",
            category = "mode",
            kind = AcpConfigOption.Kind.Select(
                current = "coding",
                groups = listOf(
                    AcpConfigOption.OptionGroup(
                        name = null,
                        options = listOf(
                            AcpConfigOption.Opt("Plan", "plan", "Read-only exploration and planning"),
                            AcpConfigOption.Opt("Coding", "coding", "Edit files and run commands"),
                            AcpConfigOption.Opt("Ask", "ask", "Answer questions without touching the repo"),
                        ),
                    ),
                ),
            ),
        ),
        AcpConfigOption(
            id = "model",
            name = "Model",
            category = "model",
            kind = AcpConfigOption.Kind.Select(
                current = "gpt-5",
                groups = listOf(
                    AcpConfigOption.OptionGroup(
                        name = "OpenAI",
                        options = listOf(
                            AcpConfigOption.Opt("GPT-5", "gpt-5"),
                            AcpConfigOption.Opt("GPT-5 mini", "gpt-5-mini"),
                        ),
                    ),
                    AcpConfigOption.OptionGroup(
                        name = "Anthropic",
                        options = listOf(
                            AcpConfigOption.Opt("Claude Sonnet", "claude-sonnet"),
                        ),
                    ),
                ),
            ),
        ),
        AcpConfigOption(
            id = "permission",
            name = "Permissions",
            description = "How much the agent may do without asking.",
            category = "permission",
            kind = AcpConfigOption.Kind.Select(
                current = "yolo",
                groups = listOf(
                    AcpConfigOption.OptionGroup(
                        name = null,
                        options = listOf(
                            // As the CLI names them; the sheet says them the desktop's way.
                            AcpConfigOption.Opt("Ask first", "ask-first", "Ask before every tool call"),
                            AcpConfigOption.Opt("Restricted", "restricted", "Auto-approve inside the project"),
                            AcpConfigOption.Opt("YOLO", "yolo", "Automatically approve all tool, path, and command requests"),
                        ),
                    ),
                ),
            ),
        ),
        thinkingOption("high"),
        ultraOption(on = false),
    )

    /**
     * The CLI's thinking option (internal/acp/config_options.go): Off, Low …
     * Max. Without [offered], the Off choice is left out, as a CLI that
     * doesn't list it would.
     */
    fun thinkingOption(current: String, offered: Boolean = true) = AcpConfigOption(
        id = "thinking",
        name = "Thinking",
        description = "Extended-thinking effort",
        category = "thought_level",
        kind = AcpConfigOption.Kind.Select(
            current = current,
            groups = listOf(
                AcpConfigOption.OptionGroup(
                    name = null,
                    options = listOf(
                        AcpConfigOption.Opt("Off", "off"),
                        AcpConfigOption.Opt("Low", "low"),
                        AcpConfigOption.Opt("Medium", "medium"),
                        AcpConfigOption.Opt("High", "high"),
                        AcpConfigOption.Opt("X-High", "x-high"),
                        AcpConfigOption.Opt("Max", "max"),
                    ).filter { offered || it.value != "off" },
                ),
            ),
        ),
    )

    /** The CLI's ultracode switch, with the note it carries when Ask first suspends it. */
    fun ultraOption(on: Boolean, suspended: Boolean = false) = AcpConfigOption(
        id = "ultra",
        name = "Ultra",
        description = if (suspended) {
            "Ultracode: substantive tasks run as dynamic workflows (suspended under Ask first — workflows need Restricted or YOLO)"
        } else {
            "Ultracode: substantive tasks run as dynamic workflows"
        },
        kind = AcpConfigOption.Kind.Bool(current = on),
    )

    /**
     * [configOptions] with thinking at [thinking], Ultra [ultra] and the
     * permission at [permission] — the thinking slider's harness scenes.
     */
    fun configOptionsWith(
        thinking: String,
        ultra: Boolean,
        permission: String = "yolo",
        offered: Boolean = true,
    ): List<AcpConfigOption> =
        configOptions.map { option ->
            when (option.id) {
                "thinking" -> thinkingOption(thinking, offered)
                "ultra" -> ultraOption(on = ultra, suspended = ultra && permission == "ask-first")
                "permission" -> option.copy(
                    kind = (option.kind as AcpConfigOption.Kind.Select).copy(current = permission),
                )
                else -> option
            }
        }

    val CONFIG_SUMMARY = ConfigSummary("Coding · GPT-5 · Don’t ask", warning = "Don’t ask", tailStart = "Coding · GPT-5".length)
}
