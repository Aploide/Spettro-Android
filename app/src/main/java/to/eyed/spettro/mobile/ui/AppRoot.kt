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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.eyed.spettro.mobile.coordinator.AppContainer
import to.eyed.spettro.mobile.coordinator.AppMode
import to.eyed.spettro.mobile.coordinator.headless.HeadlessConnection
import to.eyed.spettro.mobile.coordinator.headless.acpAnswersToHeaderMap
import to.eyed.spettro.mobile.coordinator.headless.approvalToAcpPermission
import to.eyed.spettro.mobile.coordinator.headless.askUserToAcpQuestion
import to.eyed.spettro.mobile.coordinator.remote.MobileModel
import to.eyed.spettro.mobile.core.headless.HeadlessClient
import to.eyed.spettro.mobile.core.headless.HeadlessEndpoint
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.RemotePairing
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.model.ChatSession
import to.eyed.spettro.mobile.model.ConfigValue
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.ImageProcessing
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.screens.chat.ChatConfigSheet
import to.eyed.spettro.mobile.ui.screens.chat.ChatScreen
import to.eyed.spettro.mobile.ui.screens.home.ArchivedChatsSheet
import to.eyed.spettro.mobile.ui.screens.home.ChatListScreen
import to.eyed.spettro.mobile.ui.screens.home.CliConnectScreen
import to.eyed.spettro.mobile.ui.screens.home.DisconnectReason
import to.eyed.spettro.mobile.ui.screens.home.DisconnectedScreen
import to.eyed.spettro.mobile.ui.screens.home.PairingScreen
import to.eyed.spettro.mobile.ui.screens.home.ProjectPickerSheet
import to.eyed.spettro.mobile.ui.screens.settings.ProvidersScreen
import to.eyed.spettro.mobile.ui.screens.settings.SettingsScreen
import to.eyed.spettro.mobile.ui.screens.sheets.PermissionSheet
import to.eyed.spettro.mobile.ui.screens.sheets.QuestionSheet
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors

/** Top-level router: which backend, then which screen. */
@Composable
fun AppRoot(container: AppContainer) {
    val mode by container.prefs.mode.collectAsState(initial = AppMode.Remote)
    when (mode) {
        AppMode.Remote -> RemoteRoot(container)
        AppMode.Cli -> CliRoot(container)
    }
}

// MARK: - Protocol B (Spettro Remote)

@Composable
private fun RemoteRoot(container: AppContainer) {
    val client = container.client
    val model = container.model
    val scope = rememberCoroutineScope()
    val state by client.state.collectAsState()

    when (val s = state) {
        is RemoteState.Unpaired -> {
            var isPairing by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val pair: (String) -> Unit = { text ->
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
                onSwitchToCli = { scope.launch { container.prefs.setMode(AppMode.Cli) } },
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
                onSwitchToCli = { scope.launch { container.prefs.setMode(AppMode.Cli) } },
            )
        }
    }
}

private enum class RemoteScreen { ChatList, Chat, Settings, Providers }

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

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            RemoteScreen.ChatList -> {
                val visible = chats.filter { !it.isArchived }
                    .sortedWith(compareByDescending<to.eyed.spettro.mobile.core.remote.ChatSummary> { it.isPinned }.thenByDescending { it.updatedAt })
                ChatListScreen(
                    chats = visible,
                    archivedCount = chats.count { it.isArchived },
                    agentReady = agentReady,
                    hostName = connected.host.hostName,
                    isRefreshing = isRefreshing,
                    onRefresh = { model.refreshEverything() },
                    onOpenChat = { id -> scope.launch { if (model.openChat(id)) screen = RemoteScreen.Chat } },
                    onNewChat = { showProjectPicker = true },
                    onPin = model::setPinned,
                    onArchive = model::setArchived,
                    onDelete = model::deleteChat,
                    onOpenSettings = { screen = RemoteScreen.Settings },
                    onOpenArchived = { showArchived = true },
                )
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
                            scope.launch { if (model.openChat(id)) screen = RemoteScreen.Chat }
                        },
                        onUnarchive = { model.setArchived(it, false) },
                        onDelete = model::deleteChat,
                        onDismiss = { showArchived = false },
                    )
                }
            }

            RemoteScreen.Chat -> {
                val session = openChat
                if (session != null) {
                    RemoteChatHost(model, session, onBack = {
                        model.closeChat()
                        screen = RemoteScreen.ChatList
                    })
                }
            }

            RemoteScreen.Settings -> {
                BackHandler { screen = RemoteScreen.ChatList }
                val account by model.account.collectAsState()
                val login by model.login.collectAsState()
                val diagnostics by model.client.diagnostics.collectAsState()
                val context = LocalContext.current
                SettingsScreen(
                    hostName = connected.host.hostName,
                    hostKind = connected.host.hostKind,
                    connectionLabel = "Connected",
                    agentReady = agentReady,
                    account = account,
                    login = login,
                    diagnostics = diagnostics,
                    appVersion = appVersion(context),
                    onDisconnect = { model.client.stop() },
                    onForget = { model.client.forget() },
                    onSignIn = model::signIn,
                    onSignOut = model::signOut,
                    onCancelLogin = model::cancelLogin,
                    onOpenProviders = {
                        model.refreshProviders()
                        screen = RemoteScreen.Providers
                    },
                    onBack = { screen = RemoteScreen.ChatList },
                )
            }

            RemoteScreen.Providers -> {
                BackHandler { screen = RemoteScreen.Settings }
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
                    onBack = { screen = RemoteScreen.Settings },
                )
            }
        }

        RemotePromptOverlays(model)
        BannerOverlay(model)
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
private fun RemoteChatHost(model: MobileModel, session: ChatSession, onBack: () -> Unit) {
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

// MARK: - Protocol A (direct CLI)

@Composable
private fun CliRoot(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var connection by remember { mutableStateOf<HeadlessConnection?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved by container.headlessStore.endpoint.collectAsState()

    val active = connection
    if (active == null) {
        CliConnectScreen(
            defaultHost = saved?.let { "${it.host}:${it.port}" } ?: "",
            isConnecting = isConnecting,
            errorText = error,
            onConnect = { endpoint, tokenOrPaste ->
                scope.launch {
                    isConnecting = true
                    error = null
                    try {
                        val paste = HeadlessEndpoint.parseTokenPaste(tokenOrPaste)
                        val token = paste.token
                            ?: throw IllegalArgumentException("Paste the SPETTRO_TOKEN= line or the 32-character token")
                        var target = endpoint.trim()
                        if (target.isEmpty()) throw IllegalArgumentException("Enter the computer's address")
                        if (!target.contains(":") && paste.port != null) target = "$target:${paste.port}"
                        val baseUrl = HeadlessEndpoint.parse(target)
                            ?: throw IllegalArgumentException("That address doesn't look right")
                        val client = HeadlessClient(baseUrl, token)
                        client.probe() // verifies reachability and the token
                        val conn = HeadlessConnection(container.scope, client)
                        // Remember the endpoint (not the token — it changes every run).
                        val hostPort = baseUrl.removePrefix("http://").removeSuffix("/")
                        container.headlessStore.save(
                            host = hostPort.substringBeforeLast(':'),
                            port = hostPort.substringAfterLast(':').toIntOrNull() ?: 7878,
                        )
                        connection = conn
                    } catch (e: Exception) {
                        error = e.message ?: "Couldn't connect"
                    } finally {
                        isConnecting = false
                    }
                }
            },
            onBack = { scope.launch { container.prefs.setMode(AppMode.Remote) } },
        )
    } else {
        CliChatHost(active, onClose = {
            active.stop()
            connection = null
        })
    }
}

@Composable
private fun CliChatHost(connection: HeadlessConnection, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()

    val session = connection.session
    val items by session.items.collectAsState()
    val isBusy by connection.isBusy.collectAsState()
    val runStartedAt by session.runStartedAt.collectAsState()
    val tokensUsed by connection.tokensUsed.collectAsState()
    val mode by connection.mode.collectAsState()
    val plan by session.plan.collectAsState()
    val usage by session.usage.collectAsState()

    var composerText by rememberSaveable { mutableStateOf("") }

    ChatScreen(
        title = mode?.let { "spettro · $it" } ?: "spettro",
        items = items,
        isBusy = isBusy,
        runStartedAt = runStartedAt,
        liveTokens = tokensUsed.takeIf { isBusy && it > 0 },
        plan = plan,
        usage = usage,
        modeName = mode,
        modeColorName = mode,
        isPinned = false,
        isArchived = false,
        composerText = composerText,
        onComposerTextChange = { composerText = it },
        attachments = emptyList(),
        onRemoveAttachment = {},
        onAddImages = {}, // The CLI plane is text-only.
        onSend = {
            val text = composerText.trim()
            if (text.isNotEmpty()) {
                composerText = ""
                scope.launch { connection.send(text) }
            }
        },
        onStop = { scope.launch { connection.interrupt() } },
        enabled = true,
        configSummary = null,
        onConfigTap = {},
        commands = emptyList(),
        onCommandPick = { composerText = "/${it.name} " },
        onBack = onClose,
        onTogglePinned = {},
        onToggleArchived = {},
        onDelete = onClose,
    )

    CliPromptOverlays(connection)
}

@Composable
private fun CliPromptOverlays(connection: HeadlessConnection) {
    val scope = rememberCoroutineScope()
    val approval by connection.pendingApproval.collectAsState()
    val askUser by connection.pendingQuestion.collectAsState()

    val currentApproval = approval
    if (currentApproval != null) {
        PermissionSheet(
            request = approvalToAcpPermission(currentApproval),
            chatTitle = null,
            onSelect = { optionId ->
                scope.launch { connection.approve(optionId ?: "deny") }
            },
            onDismiss = {},
        )
        return
    }

    val currentQuestion = askUser
    if (currentQuestion != null) {
        val form = remember(currentQuestion.questionId) { askUserToAcpQuestion(currentQuestion) }
        QuestionSheet(
            request = form,
            onSubmit = { answers ->
                scope.launch { connection.answerQuestion(acpAnswersToHeaderMap(currentQuestion, answers)) }
            },
            onDecline = { scope.launch { connection.answerQuestion(emptyMap()) } },
            onDismiss = { scope.launch { connection.answerQuestion(emptyMap()) } },
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

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (_: Exception) {
    "1.0"
}
