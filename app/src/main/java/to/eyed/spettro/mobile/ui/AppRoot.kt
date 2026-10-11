package to.eyed.spettro.mobile.ui

import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import to.eyed.spettro.mobile.core.ErrorActionKind
import androidx.compose.ui.platform.LocalDensity
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.ConfirmDialog
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.SpettroToast
import to.eyed.spettro.mobile.ui.components.ToastTone
import to.eyed.spettro.mobile.ui.theme.Elevation
import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.theme.floatingShadow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.eyed.spettro.mobile.coordinator.AppContainer
import to.eyed.spettro.mobile.coordinator.remote.MobileModel
import to.eyed.spettro.mobile.coordinator.remote.promptRoutes
import to.eyed.spettro.mobile.ui.screens.chat.DockPrompt
import to.eyed.spettro.mobile.ui.screens.chat.CHAT_PLACEHOLDER
import to.eyed.spettro.mobile.ui.screens.chat.RECONNECTING_PLACEHOLDER
import to.eyed.spettro.mobile.ui.screens.chat.STARTING_PLACEHOLDER
import to.eyed.spettro.mobile.model.CommandCopy
import to.eyed.spettro.mobile.model.Mentions
import to.eyed.spettro.mobile.ui.screens.chat.DockReply
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.PairPayload
import to.eyed.spettro.mobile.core.remote.RemotePairing
import to.eyed.spettro.mobile.core.remote.RemoteRoute
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.core.remote.RemoteFeature
import to.eyed.spettro.mobile.model.ChatSession
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.ImageProcessing
import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.components.rememberLocalNetworkPermission
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingControl
import to.eyed.spettro.mobile.ui.screens.chat.ChatConfigSheet
import to.eyed.spettro.mobile.ui.screens.chat.ChatScreen
import to.eyed.spettro.mobile.ui.screens.chat.ConfigSummary
import to.eyed.spettro.mobile.ui.screens.chat.NewSessionScreen
import to.eyed.spettro.mobile.ui.screens.chat.workflowGate
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
import to.eyed.spettro.mobile.ui.theme.ThemeStore

/**
 * How long the app stays on its screens through a dropped connection before
 * giving way to the disconnected screen. Counted only while the app is in
 * the foreground: a reconnect after coming back usually takes a second.
 */
private const val RECONNECT_GRACE_MS = 20_000L

/**
 * Drops worth riding out with the UI mounted — the loop is retrying them on
 * its own. A first refusal counts ([refusals] in a row): it's retried
 * at once, and usually gets through.
 */
internal fun isTransientDrop(state: RemoteState, refusals: Int = 0): Boolean = when (state) {
    RemoteState.Searching, RemoteState.Connecting -> true
    is RemoteState.Offline -> when (state.reason) {
        OfflineReason.HostNotFound, OfflineReason.TunnelUnreachable, OfflineReason.Transport -> true
        OfflineReason.Rejected -> refusals <= 1
        // Each of these needs the user to see why, and usually to act.
        else -> false
    }
    else -> false
}

/** Top-level router: connection state first, then which screen. */
@Composable
fun AppRoot(container: AppContainer) {
    val client = container.client
    val model = container.model
    val scope = rememberCoroutineScope()
    val state by client.state.collectAsState()
    val refusals by client.refusals.collectAsState()

    // The last good connection, kept so a transient drop doesn't unmount the
    // chat — and with it the draft, the picked images and the scroll
    // position — for what is usually a few seconds of reconnecting.
    var lastConnected by remember { mutableStateOf<RemoteState.Connected?>(null) }
    var graceOver by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inForeground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val dropped = isTransientDrop(state, refusals)

    LaunchedEffect(state, dropped) {
        when (val s = state) {
            is RemoteState.Connected -> {
                lastConnected = s
                graceOver = false
            }
            RemoteState.Unpaired -> lastConnected = null
            // Once the disconnected screen has had to say why, stay on it
            // until the next real connection: the loop's own retries
            // (Connecting, then the same refusal) must not remount the chat
            // for a second each time, and the grace timer restarting with
            // every flip would never run out.
            is RemoteState.Offline -> if (!dropped) graceOver = true
            else -> Unit
        }
    }
    LaunchedEffect(dropped, inForeground, lastConnected != null) {
        if (dropped && inForeground && lastConnected != null) {
            kotlinx.coroutines.delay(RECONNECT_GRACE_MS)
            graceOver = true
        }
    }

    val pendingLink by container.pendingPairingLink.collectAsState()
    if (state !is RemoteState.Unpaired) {
        pendingLink?.let { link ->
            PairingLinkPrompt(
                link = link,
                state = state,
                pairedHostID = client.credential.value?.hostID,
                pairedHostName = client.credential.value?.hostName,
                // Straight to pairing, not forget-then-pair: pair() only
                // replaces the stored pairing once the new one is made, and
                // carries on with the old one if it isn't.
                onPair = client::pair,
                onDismiss = { container.pendingPairingLink.value = null },
            )
        }
    }

    // One call site for the navigator, whether live or riding out a drop, so
    // going from one to the other recomposes it instead of remounting it.
    val navigatorConnection = (state as? RemoteState.Connected)
        ?: lastConnected?.takeIf { dropped && !graceOver }
    if (navigatorConnection != null) {
        RemoteNavigator(
            model = model,
            container = container,
            connected = navigatorConnection,
            reconnecting = state !is RemoteState.Connected,
            onDisconnect = {
                scope.launch {
                    // A delete still in its Undo window goes now, while
                    // there is a socket to send it on.
                    model.flushDeletesAndWait()
                    // Deliberate: go straight to the disconnected screen.
                    lastConnected = null
                    client.stop()
                }
            },
        )
        return
    }

    when (val s = state) {
        is RemoteState.Unpaired -> {
            var isPairing by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            // A scanned code waiting on the local-network question.
            var awaitingPermission by remember { mutableStateOf<PairPayload?>(null) }
            val startPairing: (PairPayload) -> Unit = { payload ->
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
            val localNetwork = rememberLocalNetworkPermission { granted ->
                if (granted) client.start()
                // Pair either way: without the permission a code with an
                // internet address can still go over the tunnel, and the
                // client says what's missing when nothing works.
                awaitingPermission?.let(startPairing)
                awaitingPermission = null
            }
            val pair: (String) -> Unit = pair@{ text ->
                // A second scan result while one attempt is in flight would
                // tear the first down mid-auth and strand the host's pairing
                // window — one attempt at a time.
                if (isPairing || awaitingPermission != null) return@pair
                val payload = RemotePairing.parse(text)
                if (payload == null) {
                    error = "That doesn't look like a Spettro pairing code."
                } else if (!localNetwork.isGranted && (payload.address != null || payload.remoteHost == null)) {
                    awaitingPermission = payload
                    localNetwork.request()
                } else {
                    startPairing(payload)
                }
            }
            // A pairing link opened from another app pairs as if scanned —
            // once the attempt in hand (or its local-network question) is
            // over, rather than being taken and dropped meanwhile.
            LaunchedEffect(pendingLink, isPairing, awaitingPermission) {
                val link = pendingLink ?: return@LaunchedEffect
                if (isPairing || awaitingPermission != null) return@LaunchedEffect
                container.pendingPairingLink.value = null
                pair(link)
            }
            PairingScreen(
                isPairing = isPairing,
                errorText = error,
                onScanned = pair,
                onManualEntry = pair,
            )
        }

        else -> {
            val credential by client.credential.collectAsState()
            val reason = DisconnectReason.from(state)
            val localNetwork = rememberLocalNetworkPermission { granted ->
                // start() begins browsing now that it may, and retries at once.
                if (granted) client.start()
            }
            DisconnectedScreen(
                reason = reason,
                hostName = credential?.hostName,
                onRetry = { client.start() },
                onPairAgain = { client.forget() },
                onForget = { client.forget() },
                hasRemoteHost = credential?.remoteHost != null,
                onAllowLocalNetwork = localNetwork::request,
                localNetworkMissing = !localNetwork.isGranted,
                refusedRepeatedly = refusals > 1,
            )
        }
    }
}

private enum class RemoteScreen { ChatList, Chat, NewSession, Settings, Providers }

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
private fun RemoteNavigator(
    model: MobileModel,
    container: AppContainer,
    connected: RemoteState.Connected,
    reconnecting: Boolean,
    onDisconnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(RemoteScreen.ChatList) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    // The folder the new-session view will start its chat in: the one last
    // chosen, else the host's most recently active.
    var chosenPath by rememberSaveable { mutableStateOf<String?>(null) }

    val chats by model.chats.collectAsState()
    val projects by model.projects.collectAsState()
    val agentReady by model.agentReady.collectAsState()
    val isRefreshing by model.isRefreshing.collectAsState()
    val openChat by model.openChat.collectAsState()
    val needsYou by model.chatsNeedingYou.collectAsState()

    // If the open chat disappears (deleted on the host), fall back to the list.
    LaunchedEffect(openChat) {
        if (openChat == null && screen == RemoteScreen.Chat) screen = RemoteScreen.ChatList
    }

    val visibleChats = chats.filter { !it.isArchived }
        .sortedWith(compareByDescending<to.eyed.spettro.mobile.core.remote.ChatSummary> { it.isPinned }.thenByDescending { it.updatedAt })
    val openChatById: (String) -> Unit = { id ->
        scope.launch { if (model.openChat(id)) screen = RemoteScreen.Chat }
    }
    val newSessionPath = chosenPath?.takeIf { path -> projects.any { it.path == path } }
        ?: projects.firstOrNull()?.path
    // The new-session view's @-mention menu lists the chosen folder.
    val newSessionFiles: (suspend () -> List<String>)? =
        newSessionPath?.takeIf { model.canMentionFiles }?.let { path -> suspend { model.projectFiles(path) } }
    val newSessionFolder = newSessionPath?.let { path ->
        projects.firstOrNull { it.path == path }?.name?.ifBlank { null }
            ?: path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
    }
    /**
     * Sends the new-session view's first message: only now is the chat
     * created, in the chosen folder, and handed the message. False — the
     * draft stays — when there is no folder yet (the picker opens) or the
     * chat couldn't be made.
     */
    val startChat: suspend (String, List<ImageAttachment>, List<String>) -> Boolean = start@{ text, images, mentions ->
        val path = newSessionPath
        if (path == null) {
            showProjectPicker = true
            return@start false
        }
        val id = model.newChat(path) ?: return@start false
        if (!model.openChat(id)) return@start false
        screen = RemoteScreen.Chat
        model.sendPrompt(text, images, mentions)
    }

    // Where Providers goes back to: Settings, or the screen an error card
    // or toast opened it from.
    var providersBack by rememberSaveable { mutableStateOf(RemoteScreen.Settings) }
    val openProviders = {
        model.refreshProviders()
        providersBack = if (screen == RemoteScreen.Settings) RemoteScreen.Settings else screen
        screen = RemoteScreen.Providers
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
                        // The empty pane is the new-session view.
                        onNewChat = { model.closeChat() },
                        onPin = model::setPinned,
                        onArchive = model::setArchived,
                        onDelete = model::deleteChat,
                        onOpenSettings = { screen = RemoteScreen.Settings },
                        onOpenArchived = { showArchived = true },
                        selectedChatId = openChat?.chatId,
                        needsYou = needsYou,
                        inSidebar = true,
                        onRename = model::renameChat.takeIf { connected.has(RemoteFeature.RENAME) },
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
                                onOpenProviders = openProviders,
                                reconnecting = reconnecting,
                                agentReady = agentReady,
                            )
                        }
                    } else {
                        NewSessionHost(
                            folderName = newSessionFolder,
                            folderPath = newSessionPath,
                            mentionFiles = newSessionFiles,
                            onChooseFolder = { showProjectPicker = true },
                            onStart = startChat,
                        )
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
                            themeStore = container.themeStore,
                            connected = connected,
                            reconnecting = reconnecting,
                            agentReady = agentReady,
                            onDisconnect = onDisconnect,
                            onOpenProviders = {
                            providersBack = RemoteScreen.Settings
                            screen = RemoteScreen.Providers
                        },
                            onBack = { screen = RemoteScreen.ChatList },
                        )
                    } else {
                        BackHandler { screen = providersBack }
                        ProvidersHost(model = model, onBack = { screen = providersBack })
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
                        onNewChat = { screen = RemoteScreen.NewSession },
                        onPin = model::setPinned,
                        onArchive = model::setArchived,
                        onDelete = model::deleteChat,
                        onOpenSettings = { screen = RemoteScreen.Settings },
                        onOpenArchived = { showArchived = true },
                        needsYou = needsYou,
                        onRename = model::renameChat.takeIf { connected.has(RemoteFeature.RENAME) },
                    )
                }

                RemoteScreen.Chat -> {
                    val session = openChat
                    if (session != null) {
                        // Keyed so per-chat UI state (scroll position, composer
                        // draft) resets when a different chat opens.
                        key(session.chatId) {
                            RemoteChatHost(
                                model,
                                session,
                                onBack = {
                                    model.closeChat()
                                    screen = RemoteScreen.ChatList
                                },
                                onOpenProviders = openProviders,
                                reconnecting = reconnecting,
                                agentReady = agentReady,
                            )
                        }
                    }
                }

                RemoteScreen.NewSession -> {
                    BackHandler { screen = RemoteScreen.ChatList }
                    NewSessionHost(
                        folderName = newSessionFolder,
                        folderPath = newSessionPath,
                        mentionFiles = newSessionFiles,
                        onChooseFolder = { showProjectPicker = true },
                        onStart = startChat,
                        onBack = { screen = RemoteScreen.ChatList },
                    )
                }

                RemoteScreen.Settings -> {
                    BackHandler { screen = RemoteScreen.ChatList }
                    SettingsHost(
                        model = model,
                        themeStore = container.themeStore,
                        connected = connected,
                        reconnecting = reconnecting,
                        agentReady = agentReady,
                        onDisconnect = onDisconnect,
                        onOpenProviders = {
                            providersBack = RemoteScreen.Settings
                            screen = RemoteScreen.Providers
                        },
                        onBack = { screen = RemoteScreen.ChatList },
                    )
                }

                RemoteScreen.Providers -> {
                    BackHandler { screen = providersBack }
                    ProvidersHost(model = model, onBack = { screen = providersBack })
                }
            }
        }

        if (showProjectPicker) {
            ProjectPickerSheet(
                projects = projects,
                selectedPath = newSessionPath,
                onPick = { path ->
                    showProjectPicker = false
                    chosenPath = path
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
        BannerOverlay(model, onOpenProviders = openProviders)
        ReconnectingPill(visible = reconnecting)
    }
}

/**
 * Says the link dropped and is coming back, without taking the screen away:
 * the transcript and the draft stay put, and sending waits for the socket.
 */
@Composable
private fun ReconnectingPill(visible: Boolean) {
    val colors = LocalSpettroColors.current
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.padding(top = 64.dp),
        ) {
            val shape = RoundedCornerShape(50)
            Surface(
                modifier = Modifier.floatingShadow(Elevation.Medium, shape),
                shape = shape,
                color = colors.surfaceRaised,
                border = BorderStroke(1.dp, colors.hairline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = colors.warning, pulsing = true)
                    Text(
                        "Reconnecting…",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

/**
 * A pairing link arrived while this phone is already paired. Pairing again
 * replaces the current computer, so that is asked first — except for a
 * link from the computer it's already connected to, which changes nothing.
 * For that computer, the question waits until it's known whether the
 * pairing still works (a cold start is "looking" for a moment first).
 */
@Composable
private fun PairingLinkPrompt(
    link: String,
    state: RemoteState,
    pairedHostID: String?,
    pairedHostName: String?,
    onPair: suspend (PairPayload) -> Unit,
    onDismiss: () -> Unit,
) {
    val payload = remember(link) { RemotePairing.parse(link) }
    if (payload == null) {
        ConfirmDialog(
            title = "That link didn’t work",
            message = "It doesn’t look like a Spettro pairing link. Copy it again on your computer.",
            confirmLabel = "OK",
            onConfirm = onDismiss,
            onDismiss = onDismiss,
            destructive = false,
            dismissLabel = null,
        )
        return
    }
    val scope = rememberCoroutineScope()
    var shown by remember(link) { mutableStateOf(false) }
    var pairing by remember(link) { mutableStateOf(false) }
    var error by remember(link) { mutableStateOf<String?>(null) }
    val replacing = payload.hostID != pairedHostID
    if (!replacing && !shown) {
        when (state) {
            is RemoteState.Connected -> {
                LaunchedEffect(link) { onDismiss() }
                return
            }
            RemoteState.Searching, RemoteState.Connecting -> return
            else -> Unit
        }
    }
    SideEffect { shown = true }
    ConfirmDialog(
        title = "Pair with “${payload.hostName}”?",
        message = when {
            pairing -> "Pairing with “${payload.hostName}”…"
            error != null -> error!!
            replacing ->
                "This phone is paired with “${pairedHostName ?: "another computer"}”. Pairing with " +
                    "“${payload.hostName}” replaces it."
            else -> "This phone will pair with it again, replacing its old pairing."
        },
        confirmLabel = if (error != null) "Try again" else "Pair",
        onConfirm = {
            scope.launch {
                pairing = true
                error = null
                try {
                    onPair(payload)
                    onDismiss()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    error = e.message ?: "Pairing didn’t finish. Copy a new link and try again."
                } finally {
                    pairing = false
                }
            }
        },
        // Not while it runs: the old pairing is already let go of.
        onDismiss = { if (!pairing) onDismiss() },
        destructive = false,
        icon = null,
        enabled = !pairing,
    )
}

/** Collects settings state and renders the stateless SettingsScreen. */
@Composable
private fun SettingsHost(
    model: MobileModel,
    themeStore: ThemeStore,
    connected: RemoteState.Connected,
    reconnecting: Boolean,
    agentReady: Boolean,
    onDisconnect: () -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
) {
    val account by model.account.collectAsState()
    val login by model.login.collectAsState()
    val diagnostics by model.client.diagnostics.collectAsState()
    val credential by model.client.credential.collectAsState()
    val theme by themeStore.choice.collectAsState()
    SettingsScreen(
        hostName = connected.host.hostName,
        hostKind = connected.host.hostKind,
        connectionLabel = when {
            reconnecting -> "Reconnecting…"
            connected.via == RemoteRoute.Internet -> "Connected over the internet"
            else -> "Connected via Wi-Fi"
        },
        isConnected = !reconnecting,
        remoteHost = credential?.remoteHost,
        agentReady = agentReady,
        account = account,
        login = login,
        diagnostics = diagnostics,
        appVersion = to.eyed.spettro.mobile.BuildConfig.VERSION_NAME,
        onDisconnect = onDisconnect,
        onForget = model::forgetComputer,
        onSignIn = model::signIn,
        onSignOut = model::signOut,
        onCancelLogin = model::cancelLogin,
        onOpenProviders = {
            model.refreshProviders()
            onOpenProviders()
        },
        onBack = onBack,
        appearance = theme.appearance,
        accent = theme.accent,
        onAppearanceChange = themeStore::setAppearance,
        onAccentChange = themeStore::setAccent,
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
 * The new-session view with its own draft and photo picker: what "+" opens
 * on a phone, and the split shell's pane while no chat is open. [onStart]
 * creates the chat and sends; the draft is cleared only once it went.
 */
@Composable
private fun NewSessionHost(
    folderName: String?,
    folderPath: String? = null,
    mentionFiles: (suspend () -> List<String>)? = null,
    onChooseFolder: () -> Unit,
    onStart: suspend (String, List<ImageAttachment>, List<String>) -> Boolean,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<ImageAttachment>()) }
    var mentions by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sending by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { uris.mapNotNull { loadAttachment(context, it) } }
                attachments = (attachments + loaded).take(4)
            }
        }
    }
    NewSessionScreen(
        folderName = folderName,
        onChooseFolder = onChooseFolder,
        composerText = text,
        onComposerTextChange = { text = it },
        attachments = attachments,
        onRemoveAttachment = { a -> attachments = attachments.filterNot { it.id == a.id } },
        onAddImages = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onSend = {
            val message = text.trim()
            if ((message.isNotEmpty() || attachments.isNotEmpty()) && !sending) {
                scope.launch {
                    sending = true
                    try {
                        if (onStart(message, attachments, Mentions.live(message, mentions))) {
                            text = ""
                            attachments = emptyList()
                            mentions = emptyList()
                        }
                    } finally {
                        sending = false
                    }
                }
            }
        },
        enabled = true,
        sending = sending,
        onBack = onBack,
        folderPath = folderPath,
        mentionFiles = mentionFiles,
        onMention = { mentions = mentions + it },
    )
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
    // The desktop's modal entrance: the scrim fades in over 140ms while the
    // panel rises 14dp into place over 160ms, both quiet ease-outs.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, SpettroMotion.modal()) }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(Dimens.radiusMd)
        Surface(
            modifier = Modifier
                .padding(Dimens.spacingXl)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .fillMaxHeight()
                .graphicsLayer { translationY = with(density) { 14.dp.toPx() } * (1f - shown.value) }
                .floatingShadow(Elevation.Large, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = shape,
            color = colors.canvas,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            // The panel floats clear of the system bars, so the screens in it
            // must not pad for them again.
            Box(Modifier.consumeWindowInsets(WindowInsets.systemBars)) {
                content()
            }
        }
    }
}

/**
 * The sheets for prompts no open chat can dock, first answer wins (any
 * paired device, or the computer, may answer): one that names no chat, or a
 * chat the list doesn't know. The open chat's own prompts sit in its dock,
 * above the composer; another chat's prompt waits in the list as "Needs
 * you" until that chat is opened. Swiping a sheet away answers nothing: the
 * prompt stays pending, set aside, and the chat offers it again
 * ([PromptWaitingBar]).
 */
@Composable
private fun RemotePromptOverlays(model: MobileModel) {
    val permissions by model.pendingPermissions.collectAsState()
    val questions by model.pendingQuestions.collectAsState()
    val chats by model.chats.collectAsState()
    val openChat by model.openChat.collectAsState()
    val setAside by model.promptsSetAside.collectAsState()
    // The open chat's own prompts are in its dock; the rest get a sheet.
    val routes = promptRoutes(openChat?.chatId, chats.map { it.id }, permissions, questions)
    val shownPermissions = routes.sheetPermissions
    val shownQuestions = routes.sheetQuestions
    val chatTitle = { chatID: String? ->
        chats.firstOrNull { it.id.equals(chatID, ignoreCase = true) }?.title
            ?.takeIf { chatID?.equals(openChat?.chatId, ignoreCase = true) != true }
    }

    val permission = shownPermissions.firstOrNull { it.promptID !in setAside }
    if (permission != null) {
        // A sheet per prompt: the next one is a fresh sheet, not this one
        // with its contents swapped under the user's thumb.
        key(permission.promptID) {
            val form = permission.questionForm
            if (form != null) {
                QuestionSheet(
                    request = form,
                    onSubmit = { model.replyToPermissionQuestion(permission, it) },
                    onDecline = { model.replyToPermission(permission.promptID, null) },
                    onDismiss = { model.setPromptAside(permission.promptID) },
                )
            } else {
                PermissionSheet(
                    request = permission.request,
                    chatTitle = chatTitle(permission.chatID),
                    onSelect = { model.replyToPermission(permission.promptID, it) },
                    onDismiss = { model.setPromptAside(permission.promptID) },
                )
            }
        }
        return
    }

    val question = shownQuestions.firstOrNull { it.promptID !in setAside }
    if (question != null) {
        key(question.promptID) {
            QuestionSheet(
                request = question.request,
                onSubmit = { model.replyToQuestion(question.promptID, it) },
                onDecline = { model.replyToQuestion(question.promptID, null) },
                onDismiss = { model.setPromptAside(question.promptID) },
            )
        }
    }
}

/**
 * The model's notes, as the desktop's toast: top centre, raised, with an
 * icon by tone, the note's one action, and the raw error behind "Show
 * details". News stays up 4s, an error 6s, a toast with an action 8s
 * ([Banner.durationMs]); touching it holds it, the way hovering does on the
 * desktop, and a tap on it dismisses unless it has details to read.
 */
@Composable
private fun BannerOverlay(model: MobileModel, onOpenProviders: () -> Unit) {
    val banner by model.banner.collectAsState()
    Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = banner != null,
            enter = slideInVertically(SpettroMotion.toast()) { -it / 4 } + fadeIn(SpettroMotion.toast()),
            exit = fadeOut(SpettroMotion.quick()),
        ) {
            val current = banner ?: return@AnimatedVisibility
            var held by remember(current) { mutableStateOf(false) }
            LaunchedEffect(current, held) {
                if (held) return@LaunchedEffect
                kotlinx.coroutines.delay(current.durationMs)
                model.dismissBanner()
            }
            val action = current.action
            val runAction: (() -> Unit)? = action?.let {
                it.run ?: when (it.opens) {
                    ErrorActionKind.Connect, ErrorActionKind.Models -> onOpenProviders
                    else -> null
                }
            }
            SpettroToast(
                title = current.text,
                detail = current.detail,
                tone = if (current.isError) ToastTone.Error else ToastTone.Info,
                icon = if (current.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                actionLabel = action?.label,
                onAction = runAction?.let { run ->
                    {
                        model.dismissBanner()
                        run()
                    }
                },
                onClose = { model.dismissBanner() },
                details = current.raw,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(current) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            held = true
                        }
                    }
                    // With details to read, only the close button dismisses.
                    .clickable(enabled = current.raw == null) { model.dismissBanner() },
            )
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
    onOpenProviders: () -> Unit = {},
    reconnecting: Boolean = false,
    agentReady: Boolean = true,
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
    val permissions by model.pendingPermissions.collectAsState()
    val questions by model.pendingQuestions.collectAsState()
    val chats by model.chats.collectAsState()
    val setAside by model.promptsSetAside.collectAsState()
    val needsYou by model.chatsNeedingYou.collectAsState()
    val routes = promptRoutes(session.chatId, chats.map { it.id }, permissions, questions)
    // Opening the chat a prompt waits in is asking to see it.
    LaunchedEffect(session.chatId) { model.bringBackPrompts(session.chatId) }
    // Only a sheet can be swiped aside; the dock's prompts stay in view.
    val waitingFor = when {
        routes.sheetPermissions.any { it.promptID in setAside } -> "Spettro is waiting for your approval"
        routes.sheetQuestions.any { it.promptID in setAside } -> "Spettro is waiting for your answer"
        else -> null
    }
    val dockPrompts = routes.dockPermissions.map { DockPrompt.Permission(it.promptID, it.request, it.questionForm) } +
        routes.dockQuestions.map { DockPrompt.Question(it.promptID, it.request) }

    var composerText by rememberSaveable(session.chatId) { mutableStateOf("") }
    // The files picked from the @-mention menu; only those still written in
    // the text when it goes are sent.
    var mentions by rememberSaveable(session.chatId) { mutableStateOf(listOf<String>()) }
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
    val summary = ConfigSummary.of(configOptions)
    // Thinking and Ultra are one dial, the thinking chip's slider; whether
    // the model can think at all comes from the models catalog.
    val catalog by model.models.collectAsState()
    // Whether a model is connected, once the providers list is known: a
    // "connect a model" error is fixed when one is.
    val providersList by model.providers.collectAsState()
    LaunchedEffect(session.chatId) { model.loadModelsIfNeeded() }
    val thinking = if (Thinking.state(configOptions) != null) {
        ThinkingControl(
            options = configOptions,
            reasons = Thinking.modelReasons(configOptions, catalog?.models.orEmpty()),
            onCommit = model::commitThinking,
            onSwitchToRestricted = { model.setConfigValue(Thinking.PERMISSION_ID, Thinking.RESTRICTED) },
            onTurnOffUltra = { model.setConfigValue(Thinking.ULTRA_ID, false) },
        )
    } else {
        null
    }

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
            // While reconnecting the send waits: the draft stays in the field.
            if ((text.isNotEmpty() || attachments.isNotEmpty()) &&
                model.sendPrompt(text, attachments, Mentions.live(text, mentions))
            ) {
                composerText = ""
                attachments = emptyList()
                mentions = emptyList()
            }
        },
        onStop = model::cancelRun,
        enabled = true,
        configSummary = summary,
        onConfigTap = { showConfig = true },
        commands = commands,
        // A command that takes arguments waits in the field ("/mode "); any
        // other runs as it is, as the desktop's palette does.
        onCommandPick = { command ->
            if (CommandCopy.takesArguments(command)) {
                composerText = "/${command.name} "
            } else {
                model.sendPrompt("/${command.name}")
            }
        },
        onBack = onBack,
        showBack = showBack,
        onTogglePinned = { model.setPinned(session.chatId, !isPinned) },
        onToggleArchived = { model.setArchived(session.chatId, !isArchived) },
        onRename = if (model.canRename) ({ model.renameChat(session.chatId, it) }) else null,
        otherChatNeedsYou = needsYou.any { !it.equals(session.chatId, ignoreCase = true) },
        waitingFor = waitingFor,
        onReviewPrompt = { model.bringBackPrompts(session.chatId) },
        workflowGate = workflowGate(configOptions),
        onSwitchPermission = { model.setConfigValue("permission", "restricted") },
        thinking = thinking,
        awaitingApproval = routes.dockPermissions.any { it.questionForm == null },
        awaitingAnswer = routes.dockQuestions.isNotEmpty() || routes.dockPermissions.any { it.questionForm != null },
        projectPath = session.projectPath.ifEmpty { null },
        awaitingToolIds = routes.dockPermissions.mapNotNull { it.card?.toolCallId }.toSet(),
        prompts = dockPrompts,
        onPromptReply = { reply ->
            when (reply) {
                is DockReply.Option -> model.replyToPermission(reply.promptId, reply.optionId)
                is DockReply.Answers -> {
                    val form = routes.dockPermissions.firstOrNull { it.promptID == reply.promptId }
                    if (form != null) {
                        model.replyToPermissionQuestion(form, reply.answers)
                    } else {
                        model.replyToQuestion(reply.promptId, reply.answers)
                    }
                }
            }
        },
        // "What should Spettro do instead?" goes as the next message, or as
        // guidance to the turn still running.
        onDeniedFeedback = { model.sendPrompt(it) },
        // Edit & resend fills the composer; it deletes nothing.
        onEditMessage = { text, mentioned ->
            composerText = text
            mentions = mentioned
        },
        // The field never locks: while the link comes back or Spettro starts
        // on the computer, the draft stays editable and only Send waits.
        sendReady = !reconnecting && agentReady,
        composerPlaceholder = when {
            reconnecting -> RECONNECTING_PLACEHOLDER
            !agentReady -> STARTING_PLACEHOLDER
            else -> CHAT_PLACEHOLDER
        },
        mentionFiles = if (model.canMentionFiles && session.projectPath.isNotEmpty()) {
            { model.projectFiles(session.projectPath) }
        } else {
            null
        },
        onMention = { path -> mentions = mentions + path },
        onRetry = { model.retryLast() },
        onOpenProviders = onOpenProviders,
        noModel = providersList?.isEmpty,
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

private fun loadAttachment(context: Context, uri: android.net.Uri): ImageAttachment? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        ImageProcessing.attachmentFrom(stream.readBytes())
    }
} catch (_: Exception) {
    null
}

