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
                            AcpConfigOption.Opt("Ask every time", "ask"),
                            AcpConfigOption.Opt("Auto-approve edits", "edits"),
                            AcpConfigOption.Opt("YOLO", "yolo", "Never ask; run everything"),
                        ),
                    ),
                ),
            ),
        ),
        AcpConfigOption(
            id = "thinking",
            name = "Thinking",
            category = "thinking",
            kind = AcpConfigOption.Kind.Select(
                current = "medium",
                groups = listOf(
                    AcpConfigOption.OptionGroup(
                        name = null,
                        options = listOf(
                            AcpConfigOption.Opt("Low", "low"),
                            AcpConfigOption.Opt("Medium", "medium"),
                            AcpConfigOption.Opt("High", "high"),
                        ),
                    ),
                ),
            ),
        ),
        AcpConfigOption(
            id = "ultra",
            name = "Ultra",
            description = "Burn credits for the strongest available reasoning.",
            category = "ultra",
            kind = AcpConfigOption.Kind.Bool(current = false),
        ),
    )

    const val CONFIG_SUMMARY = "Coding · GPT-5 · YOLO"
}
