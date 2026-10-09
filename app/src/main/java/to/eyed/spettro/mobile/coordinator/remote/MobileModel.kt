package to.eyed.spettro.mobile.coordinator.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.eyed.spettro.mobile.core.ErrorActionKind
import to.eyed.spettro.mobile.core.humanizeError
import to.eyed.spettro.mobile.core.acp.AcpAccountStatus
import to.eyed.spettro.mobile.core.acp.AcpExtensionMethod
import to.eyed.spettro.mobile.core.acp.AcpLocalProbeResult
import to.eyed.spettro.mobile.core.acp.AcpLoginStatus
import to.eyed.spettro.mobile.core.acp.AcpModelsList
import to.eyed.spettro.mobile.core.acp.AcpParser
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpProvidersList
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.core.remote.InboundNotification
import to.eyed.spettro.mobile.core.remote.RemoteApi
import to.eyed.spettro.mobile.core.remote.RemoteClient
import to.eyed.spettro.mobile.core.remote.RemoteFeature
import to.eyed.spettro.mobile.core.remote.RemoteNotification
import to.eyed.spettro.mobile.core.remote.RemoteProject
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.core.rpc.RpcException
import to.eyed.spettro.mobile.model.ChatMessage
import to.eyed.spettro.mobile.model.ChatSession
import to.eyed.spettro.mobile.model.ConfigValue
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.Prompts.describedBy

/** A permission or question the host is waiting on, addressed by prompt id. */
data class PendingPermission(
    val promptID: String,
    val chatID: String?,
    /**
     * The request as the prompt shows it: the title and kind it left to its
     * card filled in (see `Prompts.describedBy`).
     */
    val request: AcpPermissionRequest,
    /** Non-null when the permission transport actually carries a question form. */
    val questionForm: AcpQuestionRequest?,
    /** The open chat's card this request marked "pending", to put back once it is answered. */
    val card: ChatSession.ApprovalCard? = null,
    /** The request as it arrived, before its card filled anything in. */
    val received: AcpPermissionRequest = request,
)

data class PendingQuestion(
    val promptID: String,
    val chatID: String?,
    val request: AcpQuestionRequest,
)

/**
 * The prompts to show with the chat on screen ([openChatID], null for none),
 * oldest first: its own, and any that names no chat — or a chat the list
 * doesn't know ([knownChatIDs]), which would otherwise be shown nowhere. A
 * prompt for another chat waits there, as "Needs you".
 */
fun promptsShownWith(
    openChatID: String?,
    knownChatIDs: Collection<String>,
    permissions: List<PendingPermission>,
    questions: List<PendingQuestion>,
): Pair<List<PendingPermission>, List<PendingQuestion>> {
    val known = knownChatIDs.map { it.lowercase() }.toSet()
    fun here(chatID: String?) = chatID == null ||
        chatID.equals(openChatID, ignoreCase = true) ||
        chatID.lowercase() !in known
    return permissions.filter { here(it.chatID) } to questions.filter { here(it.chatID) }
}

/**
 * A transient, dismissible message shown above the chat UI, as the desktop's
 * toast: a short [text] title and, for errors, a [detail] sentence saying
 * what to do, at most one [action], and the [raw] error behind "Show
 * details".
 */
data class Banner(
    val text: String,
    val isError: Boolean = false,
    val detail: String? = null,
    val action: BannerAction? = null,
    val raw: String? = null,
) {
    /** The desktop's toast timings: 8s with something to do, 6s for an error, 4s for news. */
    val durationMs: Long
        get() = when {
            action != null -> 8_000
            isError -> 6_000
            else -> 4_000
        }
}

/**
 * A toast's one button. Either the model runs it ([run]: Undo, Try again),
 * or it names a place the UI opens ([opens]: Models & providers).
 */
class BannerAction(
    val label: String,
    val opens: ErrorActionKind? = null,
    val run: (() -> Unit)? = null,
)

/**
 * The Protocol B coordinator: owns the mirror of the PC host's state and
 * routes notifications into it. Port of the iOS MobileModel (+ the account
 * and provider stores, which live here because they share the agent relay).
 */
class MobileModel(
    val client: RemoteClient,
    private val api: RemoteApi,
    private val scope: CoroutineScope,
) {
    constructor(client: RemoteClient, scope: CoroutineScope) :
        this(client, RemoteApi(client), scope)

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())

    /** Deleted chats still inside their Undo window ([deleteChat]). */
    private val deletes = DeleteScheduler { delayMs, run ->
        val job = scope.launch {
            delay(delayMs)
            run()
        }
        return@DeleteScheduler { job.cancel() }
    }

    /** Every chat on the computer, less those on their way out (see [deleteChat]). */
    val chats: StateFlow<List<ChatSummary>> =
        combine(_chats, deletes.pending) { chats, leaving ->
            if (leaving.isEmpty()) chats else chats.filterNot { it.id in leaving }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _projects = MutableStateFlow<List<RemoteProject>>(emptyList())
    val projects: StateFlow<List<RemoteProject>> = _projects.asStateFlow()

    private val _openChat = MutableStateFlow<ChatSession?>(null)
    val openChat: StateFlow<ChatSession?> = _openChat.asStateFlow()

    private val _agentReady = MutableStateFlow(false)
    val agentReady: StateFlow<Boolean> = _agentReady.asStateFlow()

    private val _pendingPermissions = MutableStateFlow<List<PendingPermission>>(emptyList())
    val pendingPermissions: StateFlow<List<PendingPermission>> = _pendingPermissions.asStateFlow()

    private val _pendingQuestions = MutableStateFlow<List<PendingQuestion>>(emptyList())
    val pendingQuestions: StateFlow<List<PendingQuestion>> = _pendingQuestions.asStateFlow()

    /**
     * Prompts the user swiped away without answering. A swipe never answers
     * — that would deny an approval, or decline a question, nobody chose to —
     * so the prompt stays pending, out of the way, until the user asks for
     * it back (see [bringBackPrompts]).
     */
    private val _promptsSetAside = MutableStateFlow<Set<String>>(emptySet())
    val promptsSetAside: StateFlow<Set<String>> = _promptsSetAside.asStateFlow()

    /** The chats a prompt is waiting in: the list's "Needs you". */
    val chatsNeedingYou: StateFlow<Set<String>> =
        combine(_pendingPermissions, _pendingQuestions) { permissions, questions ->
            (permissions.mapNotNull { it.chatID } + questions.mapNotNull { it.chatID }).toSet()
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    // Account & subscription (via agent passthrough).
    private val _account = MutableStateFlow<AcpAccountStatus?>(null)
    val account: StateFlow<AcpAccountStatus?> = _account.asStateFlow()

    private val _login = MutableStateFlow<AcpLoginStatus?>(null)
    val login: StateFlow<AcpLoginStatus?> = _login.asStateFlow()

    // Providers & models (via agent passthrough). A failed refresh must never
    // read as "nothing configured": keep the last good data and flag the miss.
    private val _providers = MutableStateFlow<AcpProvidersList?>(null)
    val providers: StateFlow<AcpProvidersList?> = _providers.asStateFlow()

    private val _models = MutableStateFlow<AcpModelsList?>(null)
    val models: StateFlow<AcpModelsList?> = _models.asStateFlow()

    private val _providersLoading = MutableStateFlow(false)
    val providersLoading: StateFlow<Boolean> = _providersLoading.asStateFlow()

    private val _lastProvidersRefreshFailed = MutableStateFlow(false)
    val lastProvidersRefreshFailed: StateFlow<Boolean> = _lastProvidersRefreshFailed.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var loginPollJob: Job? = null

    /** Holds the updates of a chat whose snapshot is still on its way. */
    private val openBuffer = ChatOpenBuffer()

    /** Whether the last state seen was Connected — to tell a reconnect from an agent flip. */
    private var wasConnected = false

    /** The host the last connection was to; a different one starts from nothing. */
    private var lastHostID: String? = null

    /** Chats the list screen shows: non-archived, pinned first, newest first. */
    val visibleChats: List<ChatSummary>
        get() = chats.value.filter { !it.isArchived }
            .sortedWith(compareByDescending<ChatSummary> { it.isPinned }.thenByDescending { it.updatedAt })

    val archivedChats: List<ChatSummary>
        get() = chats.value.filter { it.isArchived }.sortedByDescending { it.updatedAt }

    init {
        scope.launch {
            client.state.collect { state ->
                when (state) {
                    is RemoteState.Connected -> {
                        if (lastHostID != null && lastHostID != state.host.hostID) forgetHostState()
                        lastHostID = state.host.hostID
                        _agentReady.value = state.agentReady
                        refreshEverything()
                        // Back after a drop: the open chat missed whatever
                        // streamed meanwhile, so fetch it again rather than
                        // carry on from a stale transcript.
                        if (!wasConnected) reopenChat()
                        wasConnected = true
                    }
                    else -> {
                        wasConnected = false
                        // The socket owns pending prompts: a prompt can't
                        // outlive the connection that delivered it. (Hosts
                        // with pendingReplay re-send the live ones on auth.)
                        _pendingPermissions.value = emptyList()
                        _pendingQuestions.value = emptyList()
                        _promptsSetAside.value = emptySet()
                        openBuffer.cancel()
                        loginPollJob?.cancel()
                    }
                }
            }
        }
        scope.launch {
            client.inbound.collect(::handle)
        }
    }

    /**
     * Paired with another computer: the last one's chats and projects mean
     * nothing here, and its open chat must not be "reopened" on this one.
     */
    private fun forgetHostState() {
        deletes.cancelAll()
        openBuffer.cancel()
        _openChat.value = null
        _chats.value = emptyList()
        _projects.value = emptyList()
        _providers.value = null
        _models.value = null
        _account.value = null
    }

    fun dismissBanner() {
        _banner.value = null
    }

    // MARK: Notification routing

    private fun handle(item: InboundNotification) {
        val notification = item.note
        // The list is the host's, open or not; only the session part waits.
        if (notification is RemoteNotification.ChatState) upsertChat(notification.chat)
        if (openBuffer.hold(item)) return
        when (notification) {
            is RemoteNotification.ChatUpdate,
            is RemoteNotification.ChatUser,
            is RemoteNotification.ChatState,
            -> {
                val session = _openChat.value ?: return
                applyToSession(session, notification)
            }
            else -> handleHostNotification(notification)
        }
    }

    /** Applies a chat-level notification to [session], when it is about that chat. */
    private fun applyToSession(session: ChatSession, notification: RemoteNotification) {
        when (notification) {
            is RemoteNotification.ChatUpdate -> {
                if (session.chatId != notification.chatID) return
                AcpParser.parseSessionUpdate(notification.update)?.let(session::apply)
            }
            is RemoteNotification.ChatUser -> {
                // Another client submitted a prompt; the host already excludes
                // us, so anything arriving here is genuinely someone else's.
                if (session.chatId != notification.chatID) return
                // chat/user carries no steering flag, but the host sends it
                // before the chat/state that marks a fresh turn busy: a
                // message that finds the chat already busy steers the
                // running turn, which keeps its clock.
                val steering = if (session.isBusy.value) ChatMessage.Steering.SENDING else null
                session.appendUserMessage(
                    notification.text,
                    notification.attachments.map { ImageAttachment(base64Data = it.base64, mimeType = it.mimeType) },
                    steering,
                )
                if (steering == null) session.beginRun()
            }
            is RemoteNotification.ChatState -> {
                if (session.chatId != notification.chat.id) return
                session.setTitle(notification.chat.title)
                session.setPinned(notification.chat.isPinned)
                session.setArchived(notification.chat.isArchived)
                if (notification.chat.isBusy && !session.isBusy.value) session.beginRun()
                if (notification.stopReason != null) session.endRun(notification.stopReason)
                if (!notification.chat.isBusy && notification.stopReason == null) session.setBusy(false)
                notification.notice?.takeIf { it.text.isNotEmpty() }
                    ?.let { session.appendNotice(it.text, it.isError) }
            }
            else -> Unit
        }
    }

    private fun handleHostNotification(notification: RemoteNotification) {
        when (notification) {
            is RemoteNotification.Hello,
            is RemoteNotification.ChatUpdate,
            is RemoteNotification.ChatUser,
            is RemoteNotification.ChatState,
            -> Unit
            is RemoteNotification.ChatRemoved -> {
                // Gone already: nothing left for a pending delete to do.
                deletes.undo(notification.chatID)
                _chats.value = _chats.value.filterNot { it.id == notification.chatID }
                if (_openChat.value?.chatId == notification.chatID) {
                    _openChat.value = null
                    _banner.value = Banner("This chat was deleted on your computer")
                }
            }
            is RemoteNotification.HostState -> {
                _agentReady.value = notification.agentReady
                notification.message?.let { _banner.value = Banner(it) }
            }
            is RemoteNotification.PermissionAsk -> {
                val received = AcpParser.parsePermissionRequest(notification.request) ?: return
                val form = AcpParser.parseQuestionFromPermission(received)
                // A replayed ask (pendingReplay) is one we may already hold.
                val known = _pendingPermissions.value.firstOrNull { it.promptID == notification.promptID }
                val session = sessionFor(notification.chatID, received.sessionId)
                val pending = adoptCard(
                    PendingPermission(
                        promptID = notification.promptID,
                        chatID = notification.chatID ?: session?.chatId,
                        request = received.describedBy(null),
                        questionForm = form,
                        card = known?.card,
                        received = received,
                    ),
                    session,
                )
                _pendingPermissions.value =
                    _pendingPermissions.value.filterNot { it.promptID == pending.promptID } + pending
            }
            is RemoteNotification.PermissionResolved -> {
                val pending = _pendingPermissions.value.firstOrNull { it.promptID == notification.promptID }
                _pendingPermissions.value =
                    _pendingPermissions.value.filterNot { it.promptID == notification.promptID }
                // Answered elsewhere, or withdrawn: whatever comes next for
                // the card says what happened to it.
                pending?.let { releaseCard(it, made = AcpToolStatus.IN_PROGRESS) }
                _promptsSetAside.value -= notification.promptID
            }
            is RemoteNotification.QuestionAsk -> {
                val request = questionFrom(notification.request) ?: return
                // A question over the permission transport names its chat
                // only through its session.
                val chatID = notification.chatID ?: sessionFor(null, request.sessionId)?.chatId
                val pending = PendingQuestion(notification.promptID, chatID, request)
                _pendingQuestions.value =
                    _pendingQuestions.value.filterNot { it.promptID == pending.promptID } + pending
            }
            is RemoteNotification.QuestionResolved -> {
                _pendingQuestions.value =
                    _pendingQuestions.value.filterNot { it.promptID == notification.promptID }
                _promptsSetAside.value -= notification.promptID
            }
            is RemoteNotification.AgentNotification -> {
                if (notification.method == AcpExtensionMethod.ACCOUNT_UPDATE) {
                    _account.value = AcpAccountStatus.parse(notification.params)
                }
            }
        }
    }

    /**
     * The question a `question/ask` carries: `_spettro/question/ask` params,
     * or — from an older CLI walking the form over the permission transport —
     * a `session/request_permission` with the form in its `_meta`. Either
     * way it is answered with `question/reply`; the host turns that into the
     * reply the transport needs.
     */
    private fun questionFrom(request: JsonObject): AcpQuestionRequest? {
        if (request["toolCall"] is JsonObject) {
            return AcpParser.parsePermissionRequest(request)?.let(AcpParser::parseQuestionFromPermission)
        }
        return AcpParser.parseQuestionRequest(request)
    }

    private fun upsertChat(chat: ChatSummary) {
        val existing = _chats.value
        _chats.value = if (existing.any { it.id == chat.id }) {
            existing.map { if (it.id == chat.id) chat else it }
        } else {
            existing + chat
        }
    }

    // MARK: Chats

    fun refreshEverything() {
        scope.launch {
            _isRefreshing.value = true
            runCatching { _chats.value = api.chatsList() }
                .onFailure { noteError("Couldn’t load your chats", it.message, retry = ::refreshEverything) }
            runCatching { _projects.value = api.projectsList() }
            runCatching { _account.value = AcpAccountStatus.parse(api.agentCall(AcpExtensionMethod.ACCOUNT_STATUS)) }
            _isRefreshing.value = false
        }
    }

    suspend fun openChat(chatID: String): Boolean = loadChat(chatID, isReopen = false)

    /** Fetches the open chat again after a reconnect and swaps the fresh session in. */
    private fun reopenChat() {
        val current = _openChat.value ?: return
        scope.launch { loadChat(current.chatId, isReopen = true) }
    }

    private suspend fun loadChat(chatID: String, isReopen: Boolean): Boolean {
        openBuffer.begin(chatID)
        return try {
            val (result, mark) = api.chatsOpenMarked(chatID)
            // A long transcript is a lot of JSON; parsing it on Main janks
            // the open animation.
            val stored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                AcpParser.parseStoredSession(result.chat)
            }
            // Another chat was opened while this one loaded: that one wins.
            if (openBuffer.opening != chatID) return false
            // A reload that lands after the user left the chat has nothing to refresh.
            if (isReopen && _openChat.value?.chatId != chatID) {
                openBuffer.cancel()
                return false
            }
            val session = ChatSession(chatId = chatID)
            session.restoreFrom(stored, isBusy = result.isBusy)
            (result.configOptions as? JsonArray)?.let {
                session.applyConfigUpdate(AcpParser.parseConfigOptionsStored(it))
            }
            (result.commands as? JsonArray)?.let { session.setCommands(AcpParser.parseCommands(it)) }
            (result.plan as? JsonArray)?.let { session.setPlan(AcpParser.parsePlan(it)) }
            session.setUsage(AcpParser.parseUsage(result.usage))
            if (result.isBusy) session.beginRun()
            // What streamed in after the snapshot was taken, before the
            // session is visible — so it never shows a half-applied state.
            openBuffer.finish(chatID, mark).forEach { applyToSession(session, it) }
            // Prompts this chat asked while it wasn't open.
            _pendingPermissions.value = _pendingPermissions.value.map {
                if (it.chatID.equals(chatID, ignoreCase = true)) adoptCard(it.copy(card = null), session) else it
            }
            _openChat.value = session
            true
        } catch (e: Exception) {
            // Only this open's buffer: another chat may be opening by now.
            openBuffer.cancel(chatID)
            if (isReopen) {
                if (e is RpcException && e.code == NO_SUCH_CHAT && _openChat.value?.chatId == chatID) {
                    _openChat.value = null
                    note("This chat is no longer on your computer")
                }
                // Otherwise keep the transcript on screen; the next
                // reconnect tries again.
            } else {
                noteError("Couldn’t open the chat", e.message)
            }
            false
        }
    }

    fun closeChat() {
        val id = _openChat.value?.chatId
        openBuffer.cancel()
        _openChat.value = null
        if (id != null) scope.launch { runCatching { api.chatsClose(id) } }
    }

    suspend fun newChat(projectPath: String): String? = try {
        val summary = api.chatsNew(projectPath)
        upsertChat(summary)
        summary.id
    } catch (e: Exception) {
        noteError("Couldn’t start a chat", e.message)
        null
    }

    /**
     * Deletes a chat the desktop's way (the UI has already asked): it leaves
     * the list at once and a toast offers Undo for [UNDO_WINDOW_MS]; only
     * then is the computer told. Undo puts it back — and back on screen, if
     * it was on screen. A delete the computer refuses brings the row back.
     */
    fun deleteChat(chatID: String) {
        val title = _chats.value.firstOrNull { it.id == chatID }?.title?.ifBlank { null } ?: "Untitled chat"
        val wasOpen = _openChat.value?.chatId == chatID
        if (wasOpen) closeChat()
        val undo = BannerAction("Undo", run = {
            if (deletes.undo(chatID) && wasOpen) scope.launch { openChat(chatID) }
        })
        deletes.schedule(chatID) {
            // Once it has really happened, its Undo goes with it.
            if (_banner.value?.action === undo) _banner.value = null
            scope.launch {
                runCatching { api.chatsDelete(chatID) }
                    .onSuccess { _chats.value = _chats.value.filterNot { it.id == chatID } }
                    .onFailure { noteError("Couldn’t delete the chat", it.message) }
            }
        }
        _banner.value = Banner("Deleted “$title”", action = undo)
    }

    /** Carries out every delete still in its Undo window: the app is going away. */
    fun flushDeletes() = deletes.flush()

    fun setPinned(chatID: String, pinned: Boolean) = flagChat(chatID, isPinned = pinned)

    fun setArchived(chatID: String, archived: Boolean) = flagChat(chatID, isArchived = archived)

    private fun flagChat(chatID: String, isPinned: Boolean? = null, isArchived: Boolean? = null) {
        _chats.value = _chats.value.map {
            if (it.id != chatID) it
            else it.copy(isPinned = isPinned ?: it.isPinned, isArchived = isArchived ?: it.isArchived)
        }
        _openChat.value?.takeIf { it.chatId == chatID }?.let { session ->
            isPinned?.let(session::setPinned)
            isArchived?.let(session::setArchived)
        }
        scope.launch { runCatching { api.chatsFlag(chatID, isPinned, isArchived) } }
    }

    // MARK: Prompting

    /**
     * Sends a prompt to the open chat. Returns false — leaving the draft
     * where it is — when there is no chat or no connection to send it on.
     *
     * While a turn runs, the message steers it: the host hands it to the
     * running agent, which reads it at its next step. It is drawn as
     * "sending" until the CLI's acknowledgement moves it along, and the
     * turn keeps its clock.
     */
    fun sendPrompt(text: String, attachments: List<ImageAttachment> = emptyList()): Boolean {
        val session = _openChat.value ?: return false
        if (client.state.value !is RemoteState.Connected) return false
        // Optimistic: we draw our own message; the host will not echo it back.
        val steering = if (session.isBusy.value) ChatMessage.Steering.SENDING else null
        val message = session.appendUserMessage(text, attachments, steering)
        if (steering == null) session.beginRun()
        val blocks = buildList {
            if (text.isNotBlank()) add(textBlock(text))
            attachments.forEach { add(imageBlock(it)) }
        }
        scope.launch {
            runCatching { api.prompt(session.chatId, blocks) }
                .onFailure {
                    // A steer that didn't go is a plain message; the turn
                    // it meant to steer carries on.
                    if (steering != null) session.setSteering(message.id, null) else session.endRun()
                    session.appendNotice("Send failed: ${it.message}", isError = true)
                }
        }
        return true
    }

    fun cancelRun() {
        val session = _openChat.value ?: return
        scope.launch { runCatching { api.cancel(session.chatId) } }
    }

    fun setConfigValue(configID: String, value: String) {
        val session = _openChat.value ?: return
        scope.launch { applyConfig(session, configID, ConfigValue.Str(value)) }
    }

    fun setConfigValue(configID: String, value: Boolean) {
        val session = _openChat.value ?: return
        scope.launch { applyConfig(session, configID, ConfigValue.Bool(value)) }
    }

    /**
     * Sets one option, showing it at once. A host with
     * [RemoteFeature.CONFIG_RESULT] answers when the set is done: success
     * carries the options as they now are (other options can move with
     * it), and a refusal means it already rolled back, so the phone does
     * too. An older host answers before trying, so the optimistic value is
     * all there is. Returns whether the host took it — sequenced changes
     * (thinking, then Ultra) wait on this.
     */
    suspend fun applyConfig(session: ChatSession, configID: String, value: ConfigValue): Boolean {
        val connected = client.state.value as? RemoteState.Connected
        val awaitsResult = connected?.has(RemoteFeature.CONFIG_RESULT) == true
        val before = session.configOptions.value
        session.applyLocalConfigValue(configID, value)
        return try {
            val result = api.config(
                session.chatId,
                configID,
                stringValue = (value as? ConfigValue.Str)?.value,
                boolValue = (value as? ConfigValue.Bool)?.value,
            )
            if (awaitsResult) {
                (result.configOptions as? JsonArray)?.let {
                    session.applyConfigUpdate(AcpParser.parseConfigOptionsStored(it))
                }
            }
            true
        } catch (e: RpcException) {
            if (awaitsResult && e.code != RpcException.CONNECTION_CLOSED) {
                session.applyConfigUpdate(before)
                // With notices the same sentence lands in the transcript.
                if (connected?.has(RemoteFeature.NOTICES) != true) {
                    note(e.message ?: "Couldn't change that setting.", isError = true)
                }
            } else {
                noteError("Couldn’t change that setting", e.message)
            }
            false
        } catch (e: Exception) {
            noteError("Couldn’t change that setting", e.message)
            false
        }
    }

    // MARK: Prompts (permissions & questions)

    /**
     * Answers a permission; a null option dismisses it, which the CLI reads
     * as a denial. A reject marks the card as the user's own answer, so its
     * failure reads as that rather than as something going wrong.
     */
    fun replyToPermission(promptID: String, selectedOptionID: String?) {
        val pending = _pendingPermissions.value.firstOrNull { it.promptID == promptID }
        _pendingPermissions.value = _pendingPermissions.value.filterNot { it.promptID == promptID }
        if (pending != null) {
            val option = pending.request.options.firstOrNull { it.optionId == selectedOptionID }
            val allowed = option != null && !option.isReject
            val card = pending.card
            if (card != null && option?.isReject == true) {
                sessionFor(pending.chatID, pending.request.sessionId)?.markDenied(card.toolCallId)
            }
            releaseCard(pending, made = if (allowed) AcpToolStatus.IN_PROGRESS else AcpToolStatus.FAILED)
        }
        _promptsSetAside.value -= promptID
        scope.launch { runCatching { api.permissionReply(promptID, selectedOptionID) } }
    }

    /** Puts a prompt out of the way without answering it (a swipe). */
    fun setPromptAside(promptID: String) {
        val pending = _pendingPermissions.value.any { it.promptID == promptID } ||
            _pendingQuestions.value.any { it.promptID == promptID }
        if (pending) _promptsSetAside.value += promptID
    }

    /** Shows again the prompts set aside that [promptsShownWith] gives [openChatID]. */
    fun bringBackPrompts(openChatID: String?) {
        val (permissions, questions) = promptsShownWith(
            openChatID, _chats.value.map { it.id }, _pendingPermissions.value, _pendingQuestions.value,
        )
        _promptsSetAside.value -= (permissions.map { it.promptID } + questions.map { it.promptID }).toSet()
    }

    /**
     * [pending] with what its card in [session] gives it. The host relays
     * the raw request, without the title, kind and card the desktop gives
     * it, so they come from the open chat's card here, as they do there —
     * and the card is marked as waiting on the user. Unchanged when
     * [session] isn't the chat that asked, or the card is already adopted.
     */
    private fun adoptCard(pending: PendingPermission, session: ChatSession?): PendingPermission {
        if (session == null || pending.card != null) return pending
        val wireId = pending.received.toolCallId
        val request = pending.received.describedBy(wireId?.let(session::toolById))
        val card = if (pending.questionForm == null) session.adoptPermission(request) else null
        return pending.copy(request = request, card = card)
    }

    /** Puts back the "pending" [pending] put on its card in the open chat. */
    private fun releaseCard(pending: PendingPermission, made: AcpToolStatus) {
        val card = pending.card ?: return
        sessionFor(pending.chatID, pending.request.sessionId)?.releaseApprovalCard(card, made)
    }

    /**
     * The open chat, when it is the one [chatID] names — or, for a prompt
     * that names no chat, the one whose ACP session is [sessionId].
     */
    private fun sessionFor(chatID: String?, sessionId: String?): ChatSession? {
        val session = _openChat.value ?: return null
        return when {
            chatID != null -> session.takeIf { it.chatId.equals(chatID, ignoreCase = true) }
            !sessionId.isNullOrEmpty() -> session.takeIf { it.acpSessionId.value == sessionId }
            else -> null
        }
    }

    /** Answers a question form that arrived over the permission transport. */
    fun replyToPermissionQuestion(pending: PendingPermission, answers: List<AcpQuestionAnswer>?) {
        val optionID = when {
            answers.isNullOrEmpty() -> null
            else -> {
                val answer = answers.first()
                answer.optionIds.firstOrNull()
                    ?: (pending.questionForm?.transport as? AcpQuestionRequest.Transport.Permission)?.customOptionId
            }
        }
        replyToPermission(pending.promptID, optionID)
    }

    fun replyToQuestion(promptID: String, answers: List<AcpQuestionAnswer>?) {
        _pendingQuestions.value = _pendingQuestions.value.filterNot { it.promptID == promptID }
        _promptsSetAside.value -= promptID
        val payload: JsonElement? = answers?.let { list -> buildJsonArray { list.forEach { add(it.toJson()) } } }
        scope.launch { runCatching { api.questionReply(promptID, payload) } }
    }

    // MARK: Account (device-flow sign-in over the agent relay)

    fun signIn() {
        loginPollJob?.cancel()
        loginPollJob = scope.launch {
            try {
                _login.value = AcpLoginStatus.parse(api.agentCall(AcpExtensionMethod.ACCOUNT_LOGIN_START))
                while (true) {
                    val status = _login.value?.state
                    if (status != AcpLoginStatus.State.STARTING && status != AcpLoginStatus.State.PENDING) break
                    delay(2000)
                    _login.value = AcpLoginStatus.parse(api.agentCall(AcpExtensionMethod.ACCOUNT_LOGIN_POLL))
                }
                if (_login.value?.state == AcpLoginStatus.State.COMPLETE) {
                    _account.value = AcpAccountStatus.parse(api.agentCall(AcpExtensionMethod.ACCOUNT_STATUS))
                    _login.value = null
                }
            } catch (e: RpcException) {
                _login.value = null
                if (e.code == RpcException.METHOD_NOT_FOUND) {
                    note("Update Spettro on your computer to sign in from here", isError = true)
                } else {
                    noteError("Couldn’t sign in", e.message)
                }
            } catch (e: Exception) {
                _login.value = null
                noteError("Couldn’t sign in", e.message)
            }
        }
    }

    fun cancelLogin() {
        loginPollJob?.cancel()
        loginPollJob = null
        _login.value = null
        scope.launch { runCatching { api.agentCall(AcpExtensionMethod.ACCOUNT_LOGIN_CANCEL) } }
    }

    fun signOut() {
        scope.launch {
            runCatching { _account.value = AcpAccountStatus.parse(api.agentCall(AcpExtensionMethod.ACCOUNT_LOGOUT)) }
                .onFailure { noteError("Couldn’t sign out", it.message) }
        }
    }

    // MARK: Providers & models

    fun refreshProviders() {
        scope.launch {
            _providersLoading.value = true
            try {
                _providers.value = AcpProvidersList.parse(api.agentCall(AcpExtensionMethod.PROVIDERS_LIST))
                _models.value = AcpModelsList.parse(api.agentCall(AcpExtensionMethod.MODELS_LIST))
                _lastProvidersRefreshFailed.value = false
            } catch (e: RpcException) {
                _lastProvidersRefreshFailed.value = true
                if (e.code == RpcException.METHOD_NOT_FOUND) {
                    note("Update Spettro on your computer to manage providers from here")
                }
            } catch (_: Exception) {
                _lastProvidersRefreshFailed.value = true
            } finally {
                _providersLoading.value = false
            }
        }
    }

    fun connectProvider(providerId: String, apiKey: String, activate: Boolean) {
        scope.launch {
            runCatching {
                api.agentCall(
                    AcpExtensionMethod.PROVIDERS_CONNECT,
                    buildJsonObject {
                        put("providerId", providerId)
                        put("apiKey", apiKey)
                        put("activate", activate)
                    },
                )
            }.onSuccess { refreshProviders() }
                .onFailure { noteError("Couldn’t connect the provider", it.message) }
        }
    }

    fun disconnectProvider(providerId: String) {
        scope.launch {
            runCatching {
                _providers.value = AcpProvidersList.parse(
                    api.agentCall(
                        AcpExtensionMethod.PROVIDERS_DISCONNECT,
                        buildJsonObject { put("providerId", providerId) },
                    ),
                )
            }.onFailure { noteError("Couldn’t disconnect the provider", it.message) }
        }
    }

    private val _probeResult = MutableStateFlow<AcpLocalProbeResult?>(null)
    val probeResult: StateFlow<AcpLocalProbeResult?> = _probeResult.asStateFlow()

    fun probeLocal(endpoint: String, apiKey: String?) {
        scope.launch {
            _probeResult.value = null
            runCatching {
                _probeResult.value = AcpLocalProbeResult.parse(
                    api.agentCall(
                        AcpExtensionMethod.LOCAL_PROBE,
                        buildJsonObject {
                            put("endpoint", endpoint)
                            if (apiKey != null) put("apiKey", apiKey)
                        },
                    ),
                )
            }.onFailure { noteError("Couldn’t check that server", it.message) }
        }
    }

    fun addLocal(endpoint: String, apiKey: String?) {
        scope.launch {
            runCatching {
                _providers.value = AcpProvidersList.parse(
                    api.agentCall(
                        AcpExtensionMethod.LOCAL_ADD,
                        buildJsonObject {
                            put("endpoint", endpoint)
                            if (apiKey != null) put("apiKey", apiKey)
                        },
                    ),
                )
                _probeResult.value = null
            }.onFailure { noteError("Couldn’t add the server", it.message) }
        }
    }

    fun removeLocal(endpoint: String) {
        scope.launch {
            runCatching {
                _providers.value = AcpProvidersList.parse(
                    api.agentCall(
                        AcpExtensionMethod.LOCAL_REMOVE,
                        buildJsonObject { put("endpoint", endpoint) },
                    ),
                )
            }.onFailure { noteError("Couldn’t remove the server", it.message) }
        }
    }

    fun toggleFavorite(provider: String, model: String, favorite: Boolean) {
        scope.launch {
            runCatching {
                _models.value = AcpModelsList.parse(
                    api.agentCall(
                        AcpExtensionMethod.MODELS_FAVORITE,
                        buildJsonObject {
                            put("provider", provider)
                            put("model", model)
                            put("favorite", favorite)
                        },
                    ),
                )
            }.onFailure { noteError("Couldn’t update your favorites", it.message) }
        }
    }

    // MARK: Helpers

    private fun note(text: String, isError: Boolean = false) {
        _banner.value = Banner(text, isError)
    }

    /**
     * A failure, said the desktop's way (core/Humanize): a recognised error
     * becomes its plain title, what to do and the one action that does it;
     * anything else keeps [what] failed as the title, with the message as
     * the detail. The raw text goes behind "Show details" whenever the
     * toast hasn't already printed all of it. [retry] is what Try again
     * runs; without one, a Try again action isn't offered.
     */
    private fun noteError(what: String, message: String?, retry: (() -> Unit)? = null) {
        val human = humanizeError(message)
        val action = human.action?.let { action ->
            when (action.kind) {
                ErrorActionKind.Retry -> retry?.let { BannerAction(action.label, run = it) }
                ErrorActionKind.Connect, ErrorActionKind.Models -> BannerAction(action.label, opens = action.kind)
                // Restarting, reinstalling or updating the engine happens on
                // the computer, not from here.
                else -> null
            }
        }
        val raw = human.raw.takeIf { it.isNotBlank() && (human.known || '\n' in it || it.length > human.detail.length + 1) }
        _banner.value = Banner(
            text = if (human.known) human.title else what,
            isError = true,
            detail = human.detail,
            action = action,
            raw = raw,
        )
    }

    private companion object {
        /** `chats/open` for a chat the host doesn't have (deleted meanwhile). */
        const val NO_SUCH_CHAT = -32001
    }

    private fun textBlock(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun imageBlock(attachment: ImageAttachment): JsonObject = buildJsonObject {
        put("type", "image")
        put("data", attachment.base64Data)
        put("mimeType", attachment.mimeType)
    }
}
