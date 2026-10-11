package to.eyed.spettro.mobile.screenshots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.BuildConfig
import to.eyed.spettro.mobile.core.acp.AcpAccountStatus
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.core.acp.AcpQuestionItem
import to.eyed.spettro.mobile.core.acp.AcpQuestionOption
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.core.acp.AcpUsage
import to.eyed.spettro.mobile.core.remote.HostInfo
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.model.ConfigValue
import to.eyed.spettro.mobile.model.DisplayRow
import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.model.ThinkingCommitter
import to.eyed.spettro.mobile.model.ToolGroups
import to.eyed.spettro.mobile.model.TranscriptRow
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingControl
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingFrame
import to.eyed.spettro.mobile.ui.screens.chat.workflowGate
import to.eyed.spettro.mobile.ui.DesktopModal
import to.eyed.spettro.mobile.ui.PairingLinkPrompt
import to.eyed.spettro.mobile.ui.ReconnectingPill
import to.eyed.spettro.mobile.ui.screens.chat.LocalOpenTools
import to.eyed.spettro.mobile.ui.screens.chat.ChatConfigSheet
import to.eyed.spettro.mobile.ui.screens.chat.ChatPreviewData
import to.eyed.spettro.mobile.ui.screens.chat.ConfigSummary
import to.eyed.spettro.mobile.ui.screens.chat.ChatScreen
import to.eyed.spettro.mobile.ui.screens.chat.DockPrompt
import to.eyed.spettro.mobile.ui.screens.chat.CHAT_PLACEHOLDER
import to.eyed.spettro.mobile.ui.screens.chat.RECONNECTING_PLACEHOLDER
import to.eyed.spettro.mobile.ui.screens.chat.NewSessionScreen
import to.eyed.spettro.mobile.ui.screens.chat.OrchestrationPreviewData
import to.eyed.spettro.mobile.ui.screens.chat.WorkflowGate
import to.eyed.spettro.mobile.ui.screens.home.ArchivedChatsSheet
import to.eyed.spettro.mobile.ui.screens.home.ChatListScreen
import to.eyed.spettro.mobile.ui.screens.home.DisconnectReason
import to.eyed.spettro.mobile.ui.screens.home.DisconnectedScreen
import to.eyed.spettro.mobile.ui.screens.home.PairingScreen
import to.eyed.spettro.mobile.ui.screens.home.ProjectPickerSheet
import to.eyed.spettro.mobile.ui.screens.home.previewProjects
import to.eyed.spettro.mobile.ui.screens.settings.ProvidersScreen
import to.eyed.spettro.mobile.ui.screens.settings.SettingsScreen
import to.eyed.spettro.mobile.ui.screens.settings.previewModels
import to.eyed.spettro.mobile.ui.screens.settings.previewProviders
import to.eyed.spettro.mobile.ui.screens.sheets.PermissionSheet
import to.eyed.spettro.mobile.ui.screens.sheets.QuestionSheet
import to.eyed.spettro.mobile.ui.theme.Accent
import to.eyed.spettro.mobile.ui.theme.Appearance
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.SystemBarsFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Debug-only harness for the Play Store listing: renders exactly one screen,
 * fully populated from the preview fixtures, with no client and no network.
 *
 *     adb shell am start -n to.eyed.spettro.mobile.dev/to.eyed.spettro.mobile.screenshots.ScreenshotActivity \
 *         --es scene chat --es theme dark --es form phone [--es accent mono]
 *
 * With `--es form tablet` the scenes render inside the split shell (sidebar +
 * divider + detail pane) the way RemoteNavigator lays out expanded windows,
 * and Settings presents as the desktop-style centered modal.
 *
 * The theme comes from the extras, not the phone: `theme` is dark or light,
 * `accent` is lilac (the default) or mono. Driven by
 * `tools/playstore-screenshots.sh`.
 */
class ScreenshotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scene = intent.getStringExtra(EXTRA_SCENE) ?: SCENE_CHAT
        val dark = intent.getStringExtra(EXTRA_THEME) != "light"
        val tablet = intent.getStringExtra(EXTRA_FORM) == "tablet"
        val accent = if (intent.getStringExtra(EXTRA_ACCENT) == "mono") Accent.Mono else Accent.Lilac
        val frame = SliderExtras(
            progress = intent.getStringExtra(EXTRA_PROGRESS)?.toDoubleOrNull(),
            from = intent.getStringExtra(EXTRA_FROM)?.toIntOrNull(),
            idle = intent.getStringExtra(EXTRA_IDLE)?.toDoubleOrNull(),
        )
        setContent {
            SystemBarsFor(dark = dark)
            SpettroTheme(darkTheme = dark, accent = accent) {
                CompositionLocalProvider(
                    LocalSceneTheme provides SceneTheme(dark, accent),
                    LocalSliderExtras provides frame,
                ) {
                    Scene(scene, tablet)
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
        const val EXTRA_THEME = "theme"
        const val EXTRA_FORM = "form"
        const val EXTRA_ACCENT = "accent"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_FROM = "from"
        const val EXTRA_IDLE = "idle"
        const val SCENE_CHAT = "chat"
    }
}

/**
 * The theme the extras asked for, so Settings shows that appearance and
 * accent selected rather than its defaults.
 */
private data class SceneTheme(val dark: Boolean, val accent: Accent)

private val LocalSceneTheme = staticCompositionLocalOf { SceneTheme(dark = true, accent = Accent.Lilac) }

/**
 * The thinking slider's frozen instant, from the extras: `progress` (0…1)
 * of a meteor's run from stop `from`, or lit Ultra's smoulder `idle`
 * seconds in.
 */
private data class SliderExtras(val progress: Double? = null, val from: Int? = null, val idle: Double? = null)

private val LocalSliderExtras = staticCompositionLocalOf { SliderExtras() }

// MARK: - Scenes

@Composable
private fun Scene(scene: String, tablet: Boolean) {
    // disconnected_<reason>[_repeated], e.g. disconnected_turnedaway — one
    // per DisconnectReason; "repeated" after a run of refusals.
    // disconnected_reconnecting is the drop the app rides out in place
    // instead (the "reconnecting" scene).
    if (scene.startsWith("disconnected") && scene != "disconnected_reconnecting") {
        val parts = scene.split('_')
        val reason = DisconnectReason.entries.firstOrNull {
            it.name.equals(parts.getOrNull(1), ignoreCase = true)
        } ?: DisconnectReason.HostAsleep
        DisconnectedScreen(
            reason = reason,
            hostName = "Carlo's MacBook Pro",
            onRetry = {},
            onPairAgain = {},
            onForget = {},
            onAllowLocalNetwork = {},
            refusedRepeatedly = "repeated" in parts,
        )
        return
    }
    when (scene) {
        "chat" -> Shell(tablet, items = ChatPreviewData.transcript, busy = false)
        "chat_busy" -> Shell(tablet, items = ChatPreviewData.streamingTranscript, busy = true)
        // A dropped link, ridden out in place: the chat stays, the pill
        // says so, the field stays usable and Send waits. Then the
        // @-mention menu over a project's files.
        "reconnecting", "disconnected_reconnecting" -> {
            Shell(tablet, items = ChatPreviewData.transcript, busy = false, reconnecting = true)
            ReconnectingPill(visible = true)
        }
        "mention" -> Chat(
            items = ChatPreviewData.transcript,
            busy = false,
            composerText = "compare @save",
            thinking = previewThinking(ChatPreviewData.configOptions),
            mentions = true,
        )
        // A turn that ended on an error, with Try again on its card.
        "error" -> Shell(tablet, items = ChatPreviewData.failedTurnTranscript, busy = false)
        // Tool rows: reads folded into one line, a two-hunk edit, a failed
        // command, and one waiting on approval (amber, "Needs approval").
        "tools" -> Shell(tablet, items = ChatPreviewData.toolsTranscript, busy = true, awaitingToolIds = setOf("tool-await"))
        // The same with the folded reads and the edit open: the rows they
        // stand for, and the edit as a real unified diff.
        "tools_diff" -> {
            val items = ChatPreviewData.toolsTranscript.dropLast(2)
            // The group's id comes from the fold itself, so it can't drift
            // from what the chat keys the folded row by.
            val groups = ToolGroups.fold(items.map { TranscriptRow.Item(it) })
                .filterIsInstance<DisplayRow.Tools>()
                .map { it.id }
            CompositionLocalProvider(LocalOpenTools provides groups.toSet() + "tool-edit-2") {
                Shell(tablet, items = items, busy = false)
            }
        }
        // The agent's task list, open above the composer while a turn runs.
        "todo" -> Shell(tablet, items = ChatPreviewData.streamingTranscript, busy = true, plan = longPlan)
        // Orchestration: a workflow live and settled, waiting at a checkpoint,
        // continued in a later turn and stopped, a workflow that never
        // started, and a transcript with two runs mixed into ordinary
        // conversation.
        "workflow" -> Shell(tablet, items = OrchestrationPreviewData.liveWorkflowTranscript, busy = true)
        "workflow_done" -> Shell(tablet, items = OrchestrationPreviewData.settledWorkflowTranscript, busy = false)
        "workflow_paused" -> Shell(tablet, items = OrchestrationPreviewData.pausedWorkflowTranscript, busy = false)
        "workflow_continued" -> Shell(
            tablet,
            items = OrchestrationPreviewData.continuedWorkflowTranscript,
            busy = true,
        )
        "workflow_stopped" -> Shell(tablet, items = OrchestrationPreviewData.stoppedWorkflowTranscript, busy = false)
        "workflow_failed" -> Shell(tablet, items = OrchestrationPreviewData.failedScriptTranscript, busy = false)
        "orchestration" -> Shell(tablet, items = OrchestrationPreviewData.mixedTranscript, busy = true)
        // The phrase that arms multi-agent orchestration lit in the composer,
        // with a "+500k" budget beside it and the line that names the budget;
        // then the same under Ask first, muted, with the way out.
        "activation" -> Shell(
            tablet,
            items = OrchestrationPreviewData.activationTranscript,
            busy = false,
            composerText = "then use a workflow +500k to port the settings screen too",
            workflowGate = WorkflowGate(ultraOn = false, askFirst = false, canRestrict = true),
        )
        "activation_askfirst" -> Shell(
            tablet,
            items = OrchestrationPreviewData.activationTranscript,
            busy = false,
            composerText = "ultracode: port the settings screen too",
            workflowGate = WorkflowGate(ultraOn = false, askFirst = true, canRestrict = true),
        )
        // The thinking slider, open over the chat: at rest on High; frozen
        // mid-meteor (`--es progress 0.35 [--es from 4]`, seed 7); lit
        // Ultra smouldering (`--es idle 1.5`, seed 11); Ultra paused under
        // Ask first; Off; a model that doesn't think, with Ultra still on and
        // with thinking Off; and a live one to drive by hand, its moves
        // answered by a stand-in CLI.
        "thinking" -> ThinkingScene(tablet, options = ChatPreviewData.configOptionsWith("high", ultra = false))
        "thinking_meteor" -> {
            val extras = LocalSliderExtras.current
            ThinkingScene(
                tablet,
                options = ChatPreviewData.configOptionsWith("high", ultra = true),
                frame = ThinkingFrame(meteorProgress = extras.progress ?: 0.35, meteorFrom = extras.from),
            )
        }
        "thinking_smoulder" -> ThinkingScene(
            tablet,
            options = ChatPreviewData.configOptionsWith("high", ultra = true),
            frame = ThinkingFrame(idleTime = LocalSliderExtras.current.idle ?: 1.5),
        )
        "thinking_paused" -> ThinkingScene(
            tablet,
            options = ChatPreviewData.configOptionsWith("high", ultra = true, permission = "ask-first"),
        )
        "thinking_off" -> ThinkingScene(tablet, options = ChatPreviewData.configOptionsWith("off", ultra = false))
        "thinking_inert" -> ThinkingScene(
            tablet,
            options = ChatPreviewData.configOptionsWith("high", ultra = true),
            reasons = false,
        )
        "thinking_inert_off" -> ThinkingScene(
            tablet,
            options = ChatPreviewData.configOptionsWith("off", ultra = false),
            reasons = false,
        )
        "thinking_live" -> ThinkingScene(tablet, options = ChatPreviewData.configOptionsWith("max", ultra = false), live = true)
        "list" -> if (tablet) SplitShell(items = null) else ChatList()
        "split" -> SplitShell(items = ChatPreviewData.transcript, busy = false)
        "config" -> {
            Shell(tablet, items = ChatPreviewData.transcript, busy = false)
            ChatConfigSheet(
                options = ChatPreviewData.configOptions,
                pendingValues = null,
                onSetString = { _, _ -> },
                onSetBool = { _, _ -> },
                onDismiss = {},
            )
        }
        // Approvals and questions sit inline above the composer of the chat
        // that asked (the dock); a prompt that names no chat gets the sheet.
        "permission", "prompt_dock" -> Shell(
            tablet,
            items = ChatPreviewData.transcript,
            busy = true,
            prompts = listOf(
                DockPrompt.Permission("p1", permissionRequest),
                DockPrompt.Permission("p2", editPermissionRequest),
            ),
        )
        "permission_edit" -> Shell(
            tablet,
            items = ChatPreviewData.transcript,
            busy = true,
            prompts = listOf(DockPrompt.Permission("p2", editPermissionRequest)),
        )
        "permission_sheet" -> {
            Shell(tablet, items = ChatPreviewData.transcript, busy = true)
            PermissionSheet(
                request = permissionRequest,
                chatTitle = "Fix the resume crash",
                onSelect = {},
                onDismiss = {},
            )
        }
        "question" -> Shell(
            tablet,
            items = ChatPreviewData.transcript,
            busy = true,
            prompts = listOf(DockPrompt.Question("q1", questionRequest)),
        )
        "question_sheet" -> {
            Shell(tablet, items = ChatPreviewData.transcript, busy = true)
            QuestionSheet(
                request = questionRequest,
                onSubmit = {},
                onDecline = {},
                onDismiss = {},
            )
        }
        "projects" -> {
            if (tablet) SplitShell(items = null) else ChatList()
            ProjectPickerSheet(
                projects = previewProjects(),
                selectedPath = previewProjects().first().path,
                onPick = {},
                onDismiss = {},
            )
        }
        "pairing" -> PairingScreen(
            isPairing = false,
            errorText = null,
            onScanned = {},
            onManualEntry = {},
        )
        // A spettro-pair:// link opened on a phone already paired with
        // another computer: asked first, since pairing replaces it. Then a
        // link that isn't a pairing link at all.
        "pairing_link", "pairing_link_bad" -> {
            if (tablet) SplitShell(items = null) else ChatList()
            PairingLinkPrompt(
                link = if (scene == "pairing_link") pairingLink else "spettro-pair://pair?v=1&h=studio",
                state = RemoteState.Connected(
                    host = HostInfo("host-1", "Carlo's MacBook Pro", "mac"),
                    agentReady = true,
                ),
                pairedHostID = "host-1",
                pairedHostName = "Carlo's MacBook Pro",
                onPair = {},
                onDismiss = {},
            )
        }
        "settings" -> if (tablet) {
            SplitShell(items = null)
            DesktopModal(onDismiss = {}) { Settings() }
        } else {
            Settings()
        }
        "providers" -> ProvidersScreen(
            providers = previewProviders,
            models = previewModels,
            isLoading = false,
            lastRefreshFailed = false,
            probeResult = null,
            probeSucceeded = false,
            onConnectProvider = { _, _, _ -> },
            onDisconnectProvider = {},
            onProbeLocal = { _, _ -> },
            onAddLocal = { _, _ -> },
            onRemoveLocal = {},
            onToggleFavorite = { _, _, _ -> },
            onBack = {},
        )
        "archived" -> {
            if (tablet) SplitShell(items = null) else ChatList()
            ArchivedChatsSheet(
                chats = listChats().take(3).map { it.copy(isArchived = true) },
                onOpen = {},
                onUnarchive = {},
                onDelete = {},
                onDismiss = {},
            )
        }
        // A chat with nothing in it yet: the greeting over starter prompts.
        "new_chat" -> Shell(tablet, items = emptyList(), busy = false, plan = emptyList(), usage = null, title = "New chat")
        // What "+" opens: greeting, composer, "Working in" chip, starters —
        // the split shell's empty pane on a tablet.
        "new_session" -> if (tablet) SplitShell(items = null) else NewSession(onBack = {})
        else -> Shell(tablet, items = ChatPreviewData.transcript, busy = false)
    }
}

/** A chat-centric scene: full-screen chat on phone, split shell on tablet. */
@Composable
private fun Shell(
    tablet: Boolean,
    items: List<to.eyed.spettro.mobile.model.TranscriptItem>,
    busy: Boolean,
    composerText: String = "",
    workflowGate: WorkflowGate = WorkflowGate(),
    thinking: ThinkingControl? = previewThinking(ChatPreviewData.configOptions),
    thinkingFrame: ThinkingFrame? = null,
    awaitingToolIds: Set<String> = emptySet(),
    prompts: List<DockPrompt> = emptyList(),
    plan: List<AcpPlanEntry> = ChatPreviewData.plan,
    usage: AcpUsage? = ChatPreviewData.usage,
    reconnecting: Boolean = false,
    title: String = CHAT_TITLE,
) {
    if (tablet) {
        SplitShell(
            items = items,
            busy = busy,
            awaitingToolIds = awaitingToolIds,
            prompts = prompts,
            composerText = composerText,
            workflowGate = workflowGate,
            thinking = thinking,
            thinkingFrame = thinkingFrame,
            plan = plan,
            usage = usage,
            reconnecting = reconnecting,
            title = title,
        )
    } else {
        Chat(
            items = items,
            busy = busy,
            awaitingToolIds = awaitingToolIds,
            prompts = prompts,
            composerText = composerText,
            workflowGate = workflowGate,
            thinking = thinking,
            thinkingFrame = thinkingFrame,
            plan = plan,
            usage = usage,
            reconnecting = reconnecting,
            title = title,
        )
    }
}

/**
 * The chat with its thinking slider open over [options]. A [frame] freezes
 * one instant of it; [live] answers its moves with a stand-in CLI that takes
 * each call, a beat apart, so the meteor can be driven by hand.
 */
@Composable
private fun ThinkingScene(
    tablet: Boolean,
    options: List<AcpConfigOption>,
    frame: ThinkingFrame = ThinkingFrame(),
    reasons: Boolean? = null,
    live: Boolean = false,
) {
    var current by remember { mutableStateOf(options) }
    val scope = rememberCoroutineScope()
    val queue = remember {
        ThinkingCommitter(
            scope = scope,
            ultraOnNow = { Thinking.state(current)?.ultraOn == true },
            send = { call ->
                delay(80)
                current = current.map { option ->
                    if (option.id != call.configId) return@map option
                    when (val value = call.value) {
                        is ConfigValue.Str -> option.copy(
                            kind = (option.kind as AcpConfigOption.Kind.Select).copy(current = value.value),
                        )
                        is ConfigValue.Bool -> option.copy(kind = AcpConfigOption.Kind.Bool(value.value))
                    }
                }
            },
        )
    }
    val control = ThinkingControl(
        options = current,
        reasons = reasons,
        onCommit = { stop -> if (live) queue.commit(stop) else CompletableDeferred(true) },
        onSwitchToRestricted = {},
        onTurnOffUltra = {},
    )
    val gate = workflowGate(current)
    Shell(
        tablet,
        items = ChatPreviewData.transcript,
        busy = false,
        workflowGate = gate,
        thinking = control,
        thinkingFrame = frame,
    )
}

/** A thinking chip over fixed [options], its moves going nowhere. */
private fun previewThinking(options: List<AcpConfigOption>) = ThinkingControl(
    options = options,
    reasons = null,
    onCommit = { CompletableDeferred(true) },
    onSwitchToRestricted = {},
    onTurnOffUltra = {},
)

/**
 * The tablet split shell: sidebar + divider + detail pane, as RemoteNavigator
 * lays it out. A null [items] shows the pre-selection welcome pane instead of
 * an open chat.
 */
@Composable
private fun SplitShell(
    items: List<to.eyed.spettro.mobile.model.TranscriptItem>?,
    busy: Boolean = false,
    composerText: String = "",
    workflowGate: WorkflowGate = WorkflowGate(),
    thinking: ThinkingControl? = previewThinking(ChatPreviewData.configOptions),
    thinkingFrame: ThinkingFrame? = null,
    awaitingToolIds: Set<String> = emptySet(),
    prompts: List<DockPrompt> = emptyList(),
    plan: List<AcpPlanEntry> = ChatPreviewData.plan,
    usage: AcpUsage? = ChatPreviewData.usage,
    reconnecting: Boolean = false,
    title: String = CHAT_TITLE,
) {
    val colors = LocalSpettroColors.current
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(320.dp).fillMaxHeight()) {
            ChatList(selectedChatId = if (items != null) "1" else null, inSidebar = true)
        }
        VerticalDivider(color = colors.hairline)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (items != null) {
                Chat(
                    items = items,
                    busy = busy,
                    awaitingToolIds = awaitingToolIds,
                    prompts = prompts,
                    showBack = false,
                    composerText = composerText,
                    workflowGate = workflowGate,
                    thinking = thinking,
                    thinkingFrame = thinkingFrame,
                    plan = plan,
                    usage = usage,
                    reconnecting = reconnecting,
                    title = title,
                )
            } else {
                NewSession(onBack = null)
            }
        }
    }
}

@Composable
private fun Chat(
    items: List<to.eyed.spettro.mobile.model.TranscriptItem>,
    busy: Boolean,
    showBack: Boolean = true,
    composerText: String = "",
    workflowGate: WorkflowGate = WorkflowGate(),
    thinking: ThinkingControl? = null,
    thinkingFrame: ThinkingFrame? = null,
    awaitingToolIds: Set<String> = emptySet(),
    prompts: List<DockPrompt> = emptyList(),
    reconnecting: Boolean = false,
    mentions: Boolean = false,
    plan: List<AcpPlanEntry> = ChatPreviewData.plan,
    usage: AcpUsage? = ChatPreviewData.usage,
    title: String = CHAT_TITLE,
) {
    var text by remember { mutableStateOf(composerText) }
    ChatScreen(
        title = title,
        items = items,
        isBusy = busy,
        runStartedAt = if (busy) System.currentTimeMillis() - 74_000 else null,
        liveTokens = if (busy) 9_400L else null,
        plan = plan,
        usage = usage,
        modeName = "Coding",
        // The mode's id, as the app passes it: grey under Monochrome.
        modeColorName = "coding",
        isPinned = false,
        isArchived = false,
        composerText = text,
        onComposerTextChange = { text = it },
        attachments = emptyList(),
        onRemoveAttachment = {},
        onAddImages = {},
        onSend = {},
        onStop = {},
        enabled = true,
        // The bar names the permission level the gate was read from, so the
        // Ask first scene doesn't say YOLO above a line about Ask first.
        configSummary = ConfigSummary.of(
            ChatPreviewData.configOptionsWith("high", ultra = false, permission = if (workflowGate.askFirst) "ask-first" else "yolo"),
        ),
        onConfigTap = {},
        commands = ChatPreviewData.commands,
        onCommandPick = {},
        onBack = {},
        onTogglePinned = {},
        onToggleArchived = {},
        onDelete = {},
        onRename = {},
        showBack = showBack,
        workflowGate = workflowGate,
        onSwitchPermission = {},
        thinking = thinking,
        thinkingFrame = thinkingFrame,
        projectPath = "/Users/dev/spettro",
        awaitingToolIds = awaitingToolIds,
        awaitingApproval = awaitingToolIds.isNotEmpty() || prompts.any { it is DockPrompt.Permission },
        awaitingAnswer = prompts.any { it is DockPrompt.Question },
        prompts = prompts,
        onEditMessage = { edited, _ -> text = edited },
        sendReady = !reconnecting,
        composerPlaceholder = if (reconnecting) RECONNECTING_PLACEHOLDER else CHAT_PLACEHOLDER,
        mentionFiles = if (mentions) ({ mentionFixture }) else null,
        onRetry = {},
        onOpenProviders = {},
    )
}

@Composable
private fun NewSession(onBack: (() -> Unit)?) {
    var text by remember { mutableStateOf("") }
    NewSessionScreen(
        folderName = "spettro",
        onChooseFolder = {},
        composerText = text,
        onComposerTextChange = { text = it },
        attachments = emptyList(),
        onRemoveAttachment = {},
        onAddImages = {},
        onSend = {},
        enabled = true,
        onBack = onBack,
    )
}

@Composable
private fun ChatList(selectedChatId: String? = null, inSidebar: Boolean = false) {
    ChatListScreen(
        chats = listChats(),
        selectedChatId = selectedChatId,
        archivedCount = 3,
        agentReady = true,
        hostName = "Carlo's MacBook Pro",
        isRefreshing = false,
        onRefresh = {},
        onOpenChat = {},
        onNewChat = {},
        onPin = { _, _ -> },
        onArchive = { _, _ -> },
        onDelete = {},
        onOpenSettings = {},
        onOpenArchived = {},
        inSidebar = inSidebar,
        needsYou = setOf("4"),
        onRename = { _, _ -> },
    )
}

@Composable
private fun Settings() {
    val theme = LocalSceneTheme.current
    SettingsScreen(
        appearance = if (theme.dark) Appearance.Dark else Appearance.Light,
        accent = theme.accent,
        hostName = "Carlo's MacBook Pro",
        hostKind = "mac",
        connectionLabel = "Connected over Wi-Fi",
        agentReady = true,
        account = AcpAccountStatus(
            signedIn = true,
            email = "carlo@eyed.to",
            plan = "max",
            creditsUsed = 42.0,
            creditLimit = 100.0,
            remainingCredits = 58.0,
            modelCount = 12,
        ),
        login = null,
        diagnostics = listOf(
            "browsing for _spettro-remote._tcp",
            "found Carlo's MacBook Pro at 192.168.1.24:51820",
            "hello ok, agent ready",
        ),
        appVersion = BuildConfig.VERSION_NAME,
        onDisconnect = {},
        onForget = {},
        onSignIn = {},
        onSignOut = {},
        onCancelLogin = {},
        onOpenProviders = {},
        onBack = {},
    )
}

// MARK: - Fixtures

/** The open chat's title, as the list's first row names it. */
private const val CHAT_TITLE = "Fix the resume crash"

/** ISO-8601 UTC, [minutesAgo] before now — so the list reads "2m", "1h", … */
private fun ago(minutesAgo: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(System.currentTimeMillis() - minutesAgo * 60_000L))
}

private fun listChats(): List<ChatSummary> = listOf(
    ChatSummary(
        id = "1",
        title = "Fix the resume crash",
        projectPath = "/Users/carlo/dev/spettro",
        updatedAt = ago(2),
        isPinned = true,
        isBusy = true,
        messageCount = 24,
        preview = "Guarding navigation on agentReady — running the regression test now.",
    ),
    ChatSummary(
        id = "2",
        title = "Port the pairing screen",
        projectPath = "/Users/carlo/dev/spettro",
        updatedAt = ago(48),
        messageCount = 61,
        preview = "Done — CameraX + ML Kit scanner, QR only, manual paste as a fallback.",
    ),
    ChatSummary(
        id = "3",
        title = "Trim the transcript renderer",
        projectPath = "/Users/carlo/dev/spettro",
        updatedAt = ago(190),
        messageCount = 12,
        preview = "Diff rows now collapse past 40 lines instead of re-measuring every frame.",
    ),
    ChatSummary(
        id = "4",
        title = "Investigate the flaky auth test",
        projectPath = "/Users/carlo/dev/SpettroAndroid",
        updatedAt = ago(1_500),
        isPinned = true,
        messageCount = 33,
        preview = "The retry loop was masking a race in the token refresh.",
    ),
    ChatSummary(
        id = "5",
        title = "Bonjour discovery on cold start",
        projectPath = "/Users/carlo/dev/SpettroAndroid",
        updatedAt = ago(2_900),
        messageCount = 8,
        preview = "NsdManager needs the multicast lock held for the whole browse window.",
    ),
    ChatSummary(
        id = "6",
        title = "Draft the release notes",
        projectPath = "/Users/carlo/dev/spettro-landing",
        updatedAt = ago(4_400),
        messageCount = 5,
        preview = "Three headline items: pairing, offline resume, the new config sheet.",
    ),
)

/** A longer task list, most of it still ahead. */
private val longPlan = listOf(
    AcpPlanEntry("Reproduce the resume crash", status = "completed"),
    AcpPlanEntry("Guard navigation on agentReady", status = "completed"),
    AcpPlanEntry("Re-emit transcript on reconnect", status = "completed"),
    AcpPlanEntry("Keep the draft while the link comes back", status = "in_progress"),
    AcpPlanEntry("Add a regression test", status = "pending"),
    AcpPlanEntry("Run the whole suite", status = "pending"),
    AcpPlanEntry("Write the release note", status = "pending"),
)

/** A pairing link from a second computer, with its LAN address. */
private const val pairingLink =
    "spettro-pair://pair?v=1&h=host-2&n=Studio%20iMac&k=mac&s=BwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSY" +
        "&a=192.168.1.30&p=51820"

/** A project's files, for the @-mention menu. */
private val mentionFixture = listOf(
    "README.md",
    "app/build.gradle.kts",
    "app/src/main/java/to/eyed/spettro/mobile/ui/screens/settings/SaveButton.kt",
    "app/src/main/java/to/eyed/spettro/mobile/ui/screens/settings/SettingsScreen.kt",
    "app/src/main/java/to/eyed/spettro/mobile/core/remote/SavedState.kt",
    "app/src/test/java/to/eyed/spettro/mobile/SaveButtonTest.kt",
)

// As the CLI sends an approval attached to the card it is drawing: no
// title, no kind of its own — the card supplies those (Prompts.describedBy).
private val permissionRequest = AcpPermissionRequest(
    sessionId = "s-1",
    title = "Run a command",
    toolKind = "execute",
    toolCallId = "call-3",
    texts = listOf(
        "```sh\n./gradlew :app:testDebugUnitTest --tests '*RemoteClientTest*'\n```",
        "non-whitelisted command requires approval",
    ),
    options = listOf(
        AcpPermissionRequest.Option("allow", "Allow", "allow_once"),
        AcpPermissionRequest.Option("always", "Always allow ./gradlew", "allow_always", isRecommended = true),
        AcpPermissionRequest.Option("reject", "Deny", "reject_once"),
    ),
)

private val editPermissionRequest = AcpPermissionRequest(
    sessionId = "s-1",
    title = "Edit RemoteClient.kt",
    toolKind = "edit",
    toolCallId = "call-4",
    texts = listOf("outside the project folder"),
    diffs = listOf(
        AcpToolContent.Diff(
            path = "/home/dev/spettro-android/app/src/main/java/core/remote/RemoteClient.kt",
            oldText = "private const val LAN_TIMEOUT_MS = 3_000L\nprivate const val BACKOFF_MAX_MS = 30_000L",
            newText = "private const val LAN_TIMEOUT_MS = 2_500L\nprivate const val BACKOFF_MAX_MS = 30_000L",
        ),
    ),
    options = listOf(
        AcpPermissionRequest.Option("allow", "Allow", "allow_once"),
        AcpPermissionRequest.Option("reject", "Deny", "reject_once"),
    ),
)

private val questionRequest = AcpQuestionRequest(
    sessionId = "s-1",
    context = "Setting up the persistence layer for the new sync feature.",
    questions = listOf(
        AcpQuestionItem(
            id = "q-0",
            header = "Database",
            question = "Which database should the sync service use?",
            options = listOf(
                AcpQuestionOption(
                    id = "opt-0",
                    label = "SQLite",
                    description = "Embedded, zero configuration, one file on disk.",
                    isRecommended = true,
                ),
                AcpQuestionOption(
                    id = "opt-1",
                    label = "PostgreSQL",
                    description = "A separate server, but shared between devices.",
                ),
            ),
            allowCustomInput = true,
        ),
        AcpQuestionItem(
            id = "q-1",
            header = "Cleanups",
            question = "Which cleanups should ride along?",
            options = listOf(
                AcpQuestionOption(id = "opt-0", label = "Delete the legacy QR parser"),
                AcpQuestionOption(id = "opt-1", label = "Inline the single-use helpers"),
                AcpQuestionOption(id = "opt-2", label = "Rename RemotePairing to PairingService"),
            ),
            multiSelect = true,
        ),
    ),
    transport = AcpQuestionRequest.Transport.ExtensionCall(2),
)
