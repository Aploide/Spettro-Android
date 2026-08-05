package to.eyed.spettro.mobile.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.eyed.spettro.mobile.coordinator.AppContainer
import to.eyed.spettro.mobile.coordinator.remote.MobileModel
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.RemotePairing
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.model.ChatSession
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.ImageProcessing
import to.eyed.spettro.mobile.ui.components.AppIconImage
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.screens.chat.ChatConfigSheet
import to.eyed.spettro.mobile.ui.screens.chat.ChatScreen
import to.eyed.spettro.mobile.ui.screens.home.ArchivedChatsSheet
import to.eyed.spettro.mobile.ui.screens.home.ChatListScreen
import to.eyed.spettro.mobile.ui.screens.home.DisconnectReason
import to.eyed.spettro.mobile.ui.screens.home.DisconnectedScreen
import to.eyed.spettro.mobile.ui.screens.home.PairingScreen
import to.eyed.spettro.mobile.ui.screens.home.ProjectPickerSheet
import to.eyed.spettro.mobile.ui.screens.settings.ProvidersScreen
import to.eyed.spettro.mobile.ui.screens.settings.SettingsScreen
import to.eyed.spettro.mobile.ui.screens.sheets.PermissionSheet
import to.eyed.spettro.mobile.ui.screens.sheets.QuestionSheet
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors

/** Top-level router: connection state first, then which screen. */
@Composable
fun AppRoot(container: AppContainer) {
    val client = container.client
    val model = container.model
    val scope = rememberCoroutineScope()
    val state by client.state.collectAsState()

    when (val s = state) {
        is RemoteState.Unpaired -> {
            var isPairing by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val pair: (String) -> Unit = pair@{ text ->
                // A second scan result while one attempt is in flight would
                // tear the first down mid-auth and strand the host's pairing
                // window — one attempt at a time.
                if (isPairing) return@pair
                val payload = RemotePairing.parse(text)
                if (payload == null) {
                    error = "That doesn't look like a Spettro pairing code."
                } else {
                    scope.launch {
                        isPairing = true
                        error = null
                        try {
                            client.pair(payload)
                        } catch (e: Exception) {
                            error = e.message ?: "Pairing failed"
                        } finally {
                            isPairing = false
                        }
                    }
                }
            }
            PairingScreen(
                isPairing = isPairing,
                errorText = error,
                onScanned = pair,
                onManualEntry = pair,
            )
        }

        is RemoteState.Connected -> RemoteNavigator(model, container, s)

        else -> {
            val credential by client.credential.collectAsState()
            val reason = when (val st = state) {
                is RemoteState.Searching, is RemoteState.Connecting -> DisconnectReason.Searching
                is RemoteState.Offline -> when (st.reason) {
                    OfflineReason.HostNotFound -> DisconnectReason.HostAsleep
                    OfflineReason.Rejected -> DisconnectReason.Revoked
                    OfflineReason.NotRecognised -> DisconnectReason.Revoked
                    OfflineReason.HostStopped -> DisconnectReason.SharingOff
                    OfflineReason.NoLocalNetwork -> DisconnectReason.NoLocalNetwork
                    OfflineReason.Transport -> DisconnectReason.Transport
                }
                else -> DisconnectReason.Searching
            }
            DisconnectedScreen(
                reason = reason,
                hostName = credential?.hostName,
                onRetry = { client.start() },
                onPairAgain = { client.forget() },
                onForget = { client.forget() },
            )
        }
    }
}

private enum class RemoteScreen { ChatList, Chat, Settings, Providers }

/** Sidebar width of the desktop app's split shell, adapted to dp. */
private val SidebarWidth = 320.dp

/**
 * Below these bounds the one-screen-at-a-time phone flow stays; at or above
 * them the desktop-style split shell takes over. The height floor keeps
 * landscape phones — wide but short — on the phone layout.
 */
private val ExpandedMinWidth = 780.dp
private val ExpandedMinHeight = 500.dp

@Composable
private fun RemoteNavigator(model: MobileModel, container: AppContainer, connected: RemoteState.Connected) {
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(RemoteScreen.ChatList) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    val chats by model.chats.collectAsState()
    val projects by model.projects.collectAsState()
    val agentReady by model.agentReady.collectAsState()
    val isRefreshing by model.isRefreshing.collectAsState()
    val openChat by model.openChat.collectAsState()

    // If the open chat disappears (deleted on the host), fall back to the list.
    LaunchedEffect(openChat) {
        if (openChat == null && screen == RemoteScreen.Chat) screen = RemoteScreen.ChatList
    }

    val visibleChats = chats.filter { !it.isArchived }
        .sortedWith(compareByDescending<to.eyed.spettro.mobile.core.remote.ChatSummary> { it.isPinned }.thenByDescending { it.updatedAt })
    val openChatById: (String) -> Unit = { id ->
        scope.launch { if (model.openChat(id)) screen = RemoteScreen.Chat }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= ExpandedMinWidth && maxHeight >= ExpandedMinHeight

        // Leaving the split shell (fold closed, window shrunk) with a chat
        // still open: land on that chat, not the list it was selected from.
        LaunchedEffect(isExpanded, openChat) {
            if (!isExpanded && openChat != null && screen == RemoteScreen.ChatList) {
                screen = RemoteScreen.Chat
            }
        }

        if (isExpanded) {
            val colors = LocalSpettroColors.current
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(SidebarWidth).fillMaxHeight()) {
                    ChatListScreen(
                        chats = visibleChats,
                        archivedCount = chats.count { it.isArchived },
                        agentReady = agentReady,
                        hostName = connected.host.hostName,
                        isRefreshing = isRefreshing,
                        onRefresh = { model.refreshEverything() },
                        onOpenChat = openChatById,
                        onNewChat = { showProjectPicker = true },
                        onPin = model::setPinned,
                        onArchive = model::setArchived,
                        onDelete = model::deleteChat,
                        onOpenSettings = { screen = RemoteScreen.Settings },
                        onOpenArchived = { showArchived = true },
                        selectedChatId = openChat?.chatId,
                    )
                }
                VerticalDivider(color = colors.hairline)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val session = openChat
                    if (session != null) {
                        // Keyed so per-chat UI state (scroll position, composer
                        // draft) resets when a different chat opens.
                        key(session.chatId) {
                            RemoteChatHost(
                                model,
                                session,
                                showBack = false,
                                onBack = { model.closeChat() },
                            )
                        }
                    } else {
                        DetailPlaceholder(onNewChat = { showProjectPicker = true })
                    }
                }
            }
            // Settings and Providers present as centered panels over the
            // split, the way the desktop app shows its modals.
            if (screen == RemoteScreen.Settings || screen == RemoteScreen.Providers) {
                DesktopModal(onDismiss = { screen = RemoteScreen.ChatList }) {
                    if (screen == RemoteScreen.Settings) {
                        BackHandler { screen = RemoteScreen.ChatList }
                        SettingsHost(
                            model = model,
                            connected = connected,
                            agentReady = agentReady,
                            onOpenProviders = { screen = RemoteScreen.Providers },
                            onBack = { screen = RemoteScreen.ChatList },
                        )
                    } else {
                        BackHandler { screen = RemoteScreen.Settings }
                        ProvidersHost(model = model, onBack = { screen = RemoteScreen.Settings })
                    }
                }
            }
        } else {
            when (screen) {
                RemoteScreen.ChatList -> {
                    ChatListScreen(
                        chats = visibleChats,
                        archivedCount = chats.count { it.isArchived },
                        agentReady = agentReady,
                        hostName = connected.host.hostName,
                        isRefreshing = isRefreshing,
                        onRefresh = { model.refreshEverything() },
                        onOpenChat = openChatById,
                        onNewChat = { showProjectPicker = true },
                        onPin = model::setPinned,
                        onArchive = model::setArchived,
                        onDelete = model::deleteChat,
                        onOpenSettings = { screen = RemoteScreen.Settings },
                        onOpenArchived = { showArchived = true },
                    )
                }

                RemoteScreen.Chat -> {
                    val session = openChat
                    if (session != null) {
                        // Keyed so per-chat UI state (scroll position, composer
                        // draft) resets when a different chat opens.
                        key(session.chatId) {
                            RemoteChatHost(model, session, onBack = {
                                model.closeChat()
                                screen = RemoteScreen.ChatList
                            })
                        }
                    }
                }

                RemoteScreen.Settings -> {
                    BackHandler { screen = RemoteScreen.ChatList }
                    SettingsHost(
                        model = model,
                        connected = connected,
                        agentReady = agentReady,
                        onOpenProviders = { screen = RemoteScreen.Providers },
                        onBack = { screen = RemoteScreen.ChatList },
                    )
                }

                RemoteScreen.Providers -> {
                    BackHandler { screen = RemoteScreen.Settings }
                    ProvidersHost(model = model, onBack = { screen = RemoteScreen.Settings })
                }
            }
        }

        if (showProjectPicker) {
            ProjectPickerSheet(
                projects = projects,
                onPick = { path ->
                    showProjectPicker = false
                    scope.launch {
                        val id = model.newChat(path)
                        if (id != null && model.openChat(id)) screen = RemoteScreen.Chat
                    }
                },
                onDismiss = { showProjectPicker = false },
            )
        }
        if (showArchived) {
            ArchivedChatsSheet(
                chats = chats.filter { it.isArchived }.sortedByDescending { it.updatedAt },
                onOpen = { id ->
                    showArchived = false
                    openChatById(id)
                },
                onUnarchive = { model.setArchived(it, false) },
                onDelete = model::deleteChat,
                onDismiss = { showArchived = false },
            )
        }

        RemotePromptOverlays(model)
        BannerOverlay(model)
    }
}

/** Collects settings state and renders the stateless SettingsScreen. */
@Composable
private fun SettingsHost(
    model: MobileModel,
    connected: RemoteState.Connected,
    agentReady: Boolean,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
) {
    val account by model.account.collectAsState()
    val login by model.login.collectAsState()
    val diagnostics by model.client.diagnostics.collectAsState()
    SettingsScreen(
        hostName = connected.host.hostName,
        hostKind = connected.host.hostKind,
        connectionLabel = "Connected",
        agentReady = agentReady,
        account = account,
        login = login,
        diagnostics = diagnostics,
        appVersion = to.eyed.spettro.mobile.BuildConfig.VERSION_NAME,
        onDisconnect = { model.client.stop() },
        onForget = { model.client.forget() },
        onSignIn = model::signIn,
        onSignOut = model::signOut,
        onCancelLogin = model::cancelLogin,
        onOpenProviders = {
            model.refreshProviders()
            onOpenProviders()
        },
        onBack = onBack,
    )
}

/** Collects provider state and renders the stateless ProvidersScreen. */
@Composable
private fun ProvidersHost(model: MobileModel, onBack: () -> Unit) {
    val providers by model.providers.collectAsState()
    val models by model.models.collectAsState()
    val loading by model.providersLoading.collectAsState()
    val refreshFailed by model.lastProvidersRefreshFailed.collectAsState()
    val probe by model.probeResult.collectAsState()
    ProvidersScreen(
        providers = providers,
        models = models,
        isLoading = loading,
        lastRefreshFailed = refreshFailed,
        probeResult = probe?.let { "${it.name}: ${it.models.size} models" },
        probeSucceeded = probe != null,
        onConnectProvider = model::connectProvider,
        onDisconnectProvider = model::disconnectProvider,
        onProbeLocal = model::probeLocal,
        onAddLocal = model::addLocal,
        onRemoveLocal = model::removeLocal,
        onToggleFavorite = model::toggleFavorite,
        onBack = onBack,
    )
}

/**
 * The detail pane before any chat is selected — the split-shell analog of
 * the desktop app's welcome pane. Internal so the debug screenshot harness
 * can render the same pane.
 */
@Composable
internal fun DetailPlaceholder(onNewChat: () -> Unit) {
    val colors = LocalSpettroColors.current
    Box(
        modifier = Modifier.fillMaxSize().background(colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            AppIconImage(size = 56.dp)
            Text(
                "Spettro",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Select a chat, or start a new one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Dimens.spacingSm))
            Button(
                onClick = onNewChat,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(Dimens.radiusLg),
            ) {
                Text("New Chat", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Desktop-style centered panel over a scrim — how Settings and Providers
 * present in the split shell, mirroring the PC app's fixed modal panels.
 * Tapping the scrim dismisses; taps on the panel are consumed. Internal so
 * the debug screenshot harness can present Settings the same way.
 */
@Composable
internal fun DesktopModal(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalSpettroColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(Dimens.spacingXl)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(Dimens.radiusLg),
            color = colors.canvas,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            content()
        }
    }
}

/** Permission and question sheets, broadcast-style: first answer wins. */
@Composable
private fun RemotePromptOverlays(model: MobileModel) {
    val permissions by model.pendingPermissions.collectAsState()
    val questions by model.pendingQuestions.collectAsState()

    val permission = permissions.firstOrNull()
    if (permission != null) {
        val form = permission.questionForm
        if (form != null) {
            QuestionSheet(
                request = form,
                onSubmit = { model.replyToPermissionQuestion(permission, it) },
                onDecline = { model.replyToPermission(permission.promptID, null) },
                onDismiss = { model.replyToPermission(permission.promptID, null) },
            )
        } else {
            PermissionSheet(
                request = permission.request,
                chatTitle = null,
                onSelect = { model.replyToPermission(permission.promptID, it) },
                onDismiss = {},
            )
        }
        return
    }

    val question = questions.firstOrNull()
    if (question != null) {
        QuestionSheet(
            request = question.request,
            onSubmit = { model.replyToQuestion(question.promptID, it) },
            onDecline = { model.replyToQuestion(question.promptID, null) },
            onDismiss = { model.replyToQuestion(question.promptID, null) },
        )
    }
}

@Composable
private fun BannerOverlay(model: MobileModel) {
    val banner by model.banner.collectAsState()
    val colors = LocalSpettroColors.current
    AnimatedVisibility(
        visible = banner != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.statusBarsPadding(),
    ) {
        val current = banner ?: return@AnimatedVisibility
        LaunchedEffect(current) {
            kotlinx.coroutines.delay(4000)
            model.dismissBanner()
        }
        SpettroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { model.dismissBanner() },
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.isError) colors.diffRemoved else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Binds the stateless ChatScreen to a live remote ChatSession. */
@Composable
private fun RemoteChatHost(
    model: MobileModel,
    session: ChatSession,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val title by session.title.collectAsState()
    val items by session.items.collectAsState()
    val isBusy by session.isBusy.collectAsState()
    val runStartedAt by session.runStartedAt.collectAsState()
    val plan by session.plan.collectAsState()
    val usage by session.usage.collectAsState()
    val configOptions by session.configOptions.collectAsState()
    val commands by session.commands.collectAsState()
    val isPinned by session.isPinned.collectAsState()
    val isArchived by session.isArchived.collectAsState()

    var composerText by rememberSaveable(session.chatId) { mutableStateOf("") }
    var attachments by remember(session.chatId) { mutableStateOf(listOf<ImageAttachment>()) }
    var showConfig by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> loadAttachment(context, uri) }
                }
                attachments = (attachments + loaded).take(4)
            }
        }
    }

    val modeOption = configOptions.firstOrNull { it.id == "mode" }
    val summary = configSummary(configOptions)

    ChatScreen(
        title = title,
        items = items,
        isBusy = isBusy,
        runStartedAt = runStartedAt,
        liveTokens = session.liveRunTokens.toLong().takeIf { isBusy },
        plan = plan,
        usage = usage,
        modeName = modeOption?.currentLabel,
        modeColorName = (modeOption?.kind as? to.eyed.spettro.mobile.core.acp.AcpConfigOption.Kind.Select)?.current,
        isPinned = isPinned,
        isArchived = isArchived,
        composerText = composerText,
        onComposerTextChange = { composerText = it },
        attachments = attachments,
        onRemoveAttachment = { a -> attachments = attachments.filterNot { it.id == a.id } },
        onAddImages = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSend = {
            val text = composerText.trim()
            if (text.isNotEmpty() || attachments.isNotEmpty()) {
                model.sendPrompt(text, attachments)
                composerText = ""
                attachments = emptyList()
            }
        },
        onStop = model::cancelRun,
        enabled = true,
        configSummary = summary,
        onConfigTap = { showConfig = true },
        commands = commands,
        onCommandPick = { composerText = "/${it.name} " },
        onBack = onBack,
        showBack = showBack,
        onTogglePinned = { model.setPinned(session.chatId, !isPinned) },
        onToggleArchived = { model.setArchived(session.chatId, !isArchived) },
        onDelete = {
            model.deleteChat(session.chatId)
            onBack()
        },
    )

    if (showConfig) {
        ChatConfigSheet(
            options = configOptions,
            pendingValues = session.displayedConfigValues,
            onSetString = model::setConfigValue,
            onSetBool = model::setConfigValue,
            onDismiss = { showConfig = false },
        )
    }
}

// MARK: - Helpers

private fun configSummary(options: List<to.eyed.spettro.mobile.core.acp.AcpConfigOption>): String? {
    val parts = listOf("mode", "model", "permission").mapNotNull { id ->
        options.firstOrNull { it.id == id }?.currentLabel?.takeIf { it.isNotBlank() }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun loadAttachment(context: Context, uri: android.net.Uri): ImageAttachment? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        ImageProcessing.attachmentFrom(stream.readBytes())
    }
} catch (_: Exception) {
    null
}

