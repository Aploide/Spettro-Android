package to.eyed.spettro.mobile.screenshots

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.eyed.spettro.mobile.BuildConfig
import to.eyed.spettro.mobile.core.acp.AcpAccountStatus
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpQuestionItem
import to.eyed.spettro.mobile.core.acp.AcpQuestionOption
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.ui.screens.chat.ChatConfigSheet
import to.eyed.spettro.mobile.ui.screens.chat.ChatPreviewData
import to.eyed.spettro.mobile.ui.screens.chat.ChatScreen
import to.eyed.spettro.mobile.ui.screens.home.ChatListScreen
import to.eyed.spettro.mobile.ui.screens.home.PairingScreen
import to.eyed.spettro.mobile.ui.screens.home.ProjectPickerSheet
import to.eyed.spettro.mobile.ui.screens.home.previewProjects
import to.eyed.spettro.mobile.ui.screens.settings.SettingsScreen
import to.eyed.spettro.mobile.ui.screens.sheets.PermissionSheet
import to.eyed.spettro.mobile.ui.screens.sheets.QuestionSheet
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Debug-only harness for the Play Store listing: renders exactly one screen,
 * fully populated from the preview fixtures, with no client and no network.
 *
 *     adb shell am start -n to.eyed.spettro.mobile/.screenshots.ScreenshotActivity \
 *         --es scene chat --es theme dark
 *
 * Dynamic color is switched off so every capture carries the Spettro palette
 * rather than the capture device's wallpaper. Driven by
 * `tools/playstore-screenshots.sh`.
 */
class ScreenshotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scene = intent.getStringExtra(EXTRA_SCENE) ?: SCENE_CHAT
        val dark = intent.getStringExtra(EXTRA_THEME) != "light"
        setContent {
            SpettroTheme(darkTheme = dark, dynamicColor = false) {
                Scene(scene)
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
        const val EXTRA_THEME = "theme"
        const val SCENE_CHAT = "chat"
    }
}

// MARK: - Scenes

@Composable
private fun Scene(scene: String) {
    when (scene) {
        "chat" -> Chat(items = ChatPreviewData.transcript, busy = false)
        "chat_busy" -> Chat(items = ChatPreviewData.streamingTranscript, busy = true)
        "list" -> ChatList()
        "config" -> {
            Chat(items = ChatPreviewData.transcript, busy = false)
            ChatConfigSheet(
                options = ChatPreviewData.configOptions,
                pendingValues = null,
                onSetString = { _, _ -> },
                onSetBool = { _, _ -> },
                onDismiss = {},
            )
        }
        "permission" -> {
            Chat(items = ChatPreviewData.transcript, busy = true)
            PermissionSheet(
                request = permissionRequest,
                chatTitle = "Fix the resume crash",
                onSelect = {},
                onDismiss = {},
            )
        }
        "question" -> {
            Chat(items = ChatPreviewData.transcript, busy = true)
            QuestionSheet(
                request = questionRequest,
                onSubmit = {},
                onDecline = {},
                onDismiss = {},
            )
        }
        "projects" -> {
            ChatList()
            ProjectPickerSheet(projects = previewProjects(), onPick = {}, onDismiss = {})
        }
        "pairing" -> PairingScreen(
            isPairing = false,
            errorText = null,
            onScanned = {},
            onManualEntry = {},
        )
        "settings" -> Settings()
        else -> Chat(items = ChatPreviewData.transcript, busy = false)
    }
}

@Composable
private fun Chat(items: List<to.eyed.spettro.mobile.model.TranscriptItem>, busy: Boolean) {
    var text by remember { mutableStateOf("") }
    ChatScreen(
        title = "Fix the resume crash",
        items = items,
        isBusy = busy,
        runStartedAt = if (busy) System.currentTimeMillis() - 74_000 else null,
        liveTokens = if (busy) 9_400L else null,
        plan = ChatPreviewData.plan,
        usage = ChatPreviewData.usage,
        modeName = "coding",
        modeColorName = "green",
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
        configSummary = ChatPreviewData.CONFIG_SUMMARY,
        onConfigTap = {},
        commands = ChatPreviewData.commands,
        onCommandPick = {},
        onBack = {},
        onTogglePinned = {},
        onToggleArchived = {},
        onDelete = {},
    )
}

@Composable
private fun ChatList() {
    ChatListScreen(
        chats = listChats(),
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
    )
}

@Composable
private fun Settings() {
    SettingsScreen(
        hostName = "Carlo's MacBook Pro",
        hostKind = "mac",
        connectionLabel = "Connected",
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

private val permissionRequest = AcpPermissionRequest(
    sessionId = "s-1",
    title = "Run a shell command",
    toolKind = "execute",
    rawInput = buildJsonObject {
        put("command", "./gradlew :app:testDebugUnitTest --tests '*RemoteClientTest*'")
    },
    options = listOf(
        AcpPermissionRequest.Option("allow", "Allow", "allow_once"),
        AcpPermissionRequest.Option("always", "Always Allow", "allow_always", isRecommended = true),
        AcpPermissionRequest.Option("reject", "Reject", "reject_once"),
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
