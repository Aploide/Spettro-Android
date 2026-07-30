package to.eyed.spettro.mobile.coordinator.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.core.remote.RemoteApi
import to.eyed.spettro.mobile.core.remote.RemoteClient
import to.eyed.spettro.mobile.core.remote.RemoteNotification
import to.eyed.spettro.mobile.core.remote.RemoteProject
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.core.rpc.RpcException
import to.eyed.spettro.mobile.model.ChatSession
import to.eyed.spettro.mobile.model.ImageAttachment

/** A permission or question the host is waiting on, addressed by prompt id. */
data class PendingPermission(
    val promptID: String,
    val chatID: String?,
    val request: AcpPermissionRequest,
    /** Non-null when the permission transport actually carries a question form. */
    val questionForm: AcpQuestionRequest?,
)

data class PendingQuestion(
    val promptID: String,
    val chatID: String?,
    val request: AcpQuestionRequest,
)

/** A transient, dismissible message shown above the chat UI. */
data class Banner(val text: String, val isError: Boolean = false)

/**
 * The Protocol B coordinator: owns the mirror of the Mac host's state and
 * routes notifications into it. Port of the iOS MobileModel (+ the account
 * and provider stores, which live here because they share the agent relay).
 */
class MobileModel(
    val client: RemoteClient,
    private val api: RemoteApi,
    private val scope: CoroutineScope,
) {
    constructor(client: RemoteClient, scope: CoroutineScope) :
        this(client, RemoteApi(client::request), scope)

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

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

    /** Chats the list screen shows: non-archived, pinned first, newest first. */
    val visibleChats: List<ChatSummary>
        get() = _chats.value.filter { !it.isArchived }
            .sortedWith(compareByDescending<ChatSummary> { it.isPinned }.thenByDescending { it.updatedAt })

    val archivedChats: List<ChatSummary>
        get() = _chats.value.filter { it.isArchived }.sortedByDescending { it.updatedAt }

    init {
        scope.launch {
            client.state.collect { state ->
                when (state) {
                    is RemoteState.Connected -> {
                        _agentReady.value = state.agentReady
                        refreshEverything()
                    }
                    else -> {
                        // The socket owns pending prompts: a prompt can't
                        // outlive the connection that delivered it.
                        _pendingPermissions.value = emptyList()
                        _pendingQuestions.value = emptyList()
                        loginPollJob?.cancel()
                    }
                }
            }
        }
        scope.launch {
            client.notifications.collect(::handle)
        }
    }

    fun dismissBanner() {
        _banner.value = null
    }

    // MARK: Notification routing

    private fun handle(notification: RemoteNotification) {
        when (notification) {
            is RemoteNotification.Hello -> Unit
            is RemoteNotification.ChatUpdate -> {
                val session = _openChat.value ?: return
                if (session.chatId != notification.chatID) return
                AcpParser.parseSessionUpdate(notification.update)?.let(session::apply)
            }
            is RemoteNotification.ChatUser -> {
                // Another client submitted a prompt; the host already excludes
                // us, so anything arriving here is genuinely someone else's.
                val session = _openChat.value ?: return
                if (session.chatId != notification.chatID) return
                session.appendUserMessage(
                    notification.text,
                    notification.attachments.map { ImageAttachment(base64Data = it.base64, mimeType = it.mimeType) },
                )
                session.beginRun()
            }
            is RemoteNotification.ChatState -> {
                upsertChat(notification.chat)
                val session = _openChat.value ?: return
                if (session.chatId != notification.chat.id) return
                session.setTitle(notification.chat.title)
                session.setPinned(notification.chat.isPinned)
                session.setArchived(notification.chat.isArchived)
                if (notification.chat.isBusy && !session.isBusy.value) session.beginRun()
                if (notification.stopReason != null) session.endRun(notification.stopReason)
                if (!notification.chat.isBusy && notification.stopReason == null) session.setBusy(false)
                notification.notice?.let { session.appendNotice(it.text, it.isError) }
            }
            is RemoteNotification.ChatRemoved -> {
                _chats.value = _chats.value.filterNot { it.id == notification.chatID }
                if (_openChat.value?.chatId == notification.chatID) {
                    _openChat.value = null
                    _banner.value = Banner("This chat was deleted on the Mac")
                }
            }
            is RemoteNotification.HostState -> {
                _agentReady.value = notification.agentReady
                notification.message?.let { _banner.value = Banner(it) }
            }
            is RemoteNotification.PermissionAsk -> {
                val request = AcpParser.parsePermissionRequest(notification.request) ?: return
                val form = AcpParser.parseQuestionFromPermission(request)
                val pending = PendingPermission(notification.promptID, notification.chatID, request, form)
                _pendingPermissions.value =
                    _pendingPermissions.value.filterNot { it.promptID == pending.promptID } + pending
            }
            is RemoteNotification.PermissionResolved -> {
                _pendingPermissions.value =
                    _pendingPermissions.value.filterNot { it.promptID == notification.promptID }
            }
            is RemoteNotification.QuestionAsk -> {
                val request = AcpParser.parseQuestionRequest(notification.request) ?: return
                val pending = PendingQuestion(notification.promptID, notification.chatID, request)
                _pendingQuestions.value =
                    _pendingQuestions.value.filterNot { it.promptID == pending.promptID } + pending
            }
            is RemoteNotification.QuestionResolved -> {
                _pendingQuestions.value =
                    _pendingQuestions.value.filterNot { it.promptID == notification.promptID }
            }
            is RemoteNotification.AgentNotification -> {
                if (notification.method == AcpExtensionMethod.ACCOUNT_UPDATE) {
                    _account.value = AcpAccountStatus.parse(notification.params)
                }
            }
        }
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
                .onFailure { note("Couldn't load chats: ${it.message}", isError = true) }
            runCatching { _projects.value = api.projectsList() }
            runCatching { _account.value = AcpAccountStatus.parse(api.agentCall(AcpExtensionMethod.ACCOUNT_STATUS)) }
            _isRefreshing.value = false
        }
    }

    suspend fun openChat(chatID: String): Boolean {
        return try {
            val result = api.chatsOpen(chatID)
            val stored = AcpParser.parseStoredSession(result.chat)
            val session = ChatSession(chatId = chatID)
            session.restoreFrom(stored)
            (result.configOptions as? JsonArray)?.let {
                session.applyConfigUpdate(AcpParser.parseConfigOptionsStored(it))
            }
            (result.commands as? JsonArray)?.let { session.setCommands(AcpParser.parseCommands(it)) }
            (result.plan as? JsonArray)?.let { session.setPlan(AcpParser.parsePlan(it)) }
            session.setUsage(AcpParser.parseUsage(result.usage))
            if (result.isBusy) session.beginRun()
            _openChat.value = session
            true
        } catch (e: Exception) {
            note("Couldn't open chat: ${e.message}", isError = true)
            false
        }
    }

    fun closeChat() {
        val id = _openChat.value?.chatId
        _openChat.value = null
        if (id != null) scope.launch { runCatching { api.chatsClose(id) } }
    }

    suspend fun newChat(projectPath: String): String? = try {
        val summary = api.chatsNew(projectPath)
        upsertChat(summary)
        summary.id
    } catch (e: Exception) {
        note("Couldn't create chat: ${e.message}", isError = true)
        null
    }

    fun deleteChat(chatID: String) {
        _chats.value = _chats.value.filterNot { it.id == chatID }
        if (_openChat.value?.chatId == chatID) _openChat.value = null
        scope.launch {
            runCatching { api.chatsDelete(chatID) }
                .onFailure { note("Couldn't delete chat: ${it.message}", isError = true) }
        }
    }

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

    fun sendPrompt(text: String, attachments: List<ImageAttachment> = emptyList()) {
        val session = _openChat.value ?: return
        // Optimistic: we draw our own message; the host will not echo it back.
        session.appendUserMessage(text, attachments)
        session.beginRun()
        val blocks = buildList {
            if (text.isNotBlank()) add(textBlock(text))
            attachments.forEach { add(imageBlock(it)) }
        }
        scope.launch {
            runCatching { api.prompt(session.chatId, blocks) }
                .onFailure {
                    session.endRun()
                    session.appendNotice("Send failed: ${it.message}", isError = true)
                }
        }
    }

    fun cancelRun() {
        val session = _openChat.value ?: return
        scope.launch { runCatching { api.cancel(session.chatId) } }
    }

    fun setConfigValue(configID: String, value: String) {
        val session = _openChat.value ?: return
        session.applyLocalConfigValue(configID, value)
        scope.launch {
            runCatching { api.config(session.chatId, configID, stringValue = value) }
                .onFailure { note("Couldn't change setting: ${it.message}", isError = true) }
        }
    }

    fun setConfigValue(configID: String, value: Boolean) {
        val session = _openChat.value ?: return
        session.applyLocalConfigValue(configID, value)
        scope.launch {
            runCatching { api.config(session.chatId, configID, boolValue = value) }
                .onFailure { note("Couldn't change setting: ${it.message}", isError = true) }
        }
    }

    // MARK: Prompts (permissions & questions)

    fun replyToPermission(promptID: String, selectedOptionID: String?) {
        _pendingPermissions.value = _pendingPermissions.value.filterNot { it.promptID == promptID }
        scope.launch { runCatching { api.permissionReply(promptID, selectedOptionID) } }
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
                note(relayErrorText(e, "Sign-in failed"), isError = true)
            } catch (e: Exception) {
                _login.value = null
                note("Sign-in failed: ${e.message}", isError = true)
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
                .onFailure { note("Sign-out failed: ${it.message}", isError = true) }
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
                    note("Update the spettro CLI on your Mac to manage providers from here")
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
                .onFailure { note("Couldn't connect provider: ${it.message}", isError = true) }
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
            }.onFailure { note("Couldn't disconnect: ${it.message}", isError = true) }
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
            }.onFailure { note("Probe failed: ${it.message}", isError = true) }
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
            }.onFailure { note("Couldn't add server: ${it.message}", isError = true) }
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
            }.onFailure { note("Couldn't remove server: ${it.message}", isError = true) }
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
            }.onFailure { note("Couldn't update favorite: ${it.message}", isError = true) }
        }
    }

    // MARK: Helpers

    private fun note(text: String, isError: Boolean = false) {
        _banner.value = Banner(text, isError)
    }

    private fun relayErrorText(e: RpcException, prefix: String): String =
        if (e.code == RpcException.METHOD_NOT_FOUND) "Update the spettro CLI on your Mac" else "$prefix: ${e.message}"

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
