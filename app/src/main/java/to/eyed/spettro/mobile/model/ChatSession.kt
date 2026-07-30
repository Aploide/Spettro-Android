package to.eyed.spettro.mobile.model

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import to.eyed.spettro.mobile.core.acp.AcpCommand
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.core.acp.AcpSessionUpdate
import to.eyed.spettro.mobile.core.acp.AcpToolCallEvent
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.core.acp.AcpUsage
import to.eyed.spettro.mobile.core.acp.StoredItemData
import to.eyed.spettro.mobile.core.acp.StoredSessionData
import to.eyed.spettro.mobile.core.acp.encodedJsonString

/** A single select or boolean config value, for local display state. */
sealed class ConfigValue {
    data class Str(val value: String) : ConfigValue()
    data class Bool(val value: Boolean) : ConfigValue()
}

/**
 * One conversation with the agent. Owns the transcript and the live session
 * config. A plain Kotlin state holder (not an Android ViewModel): all
 * mutations are main-thread confined by convention, state is exposed as
 * [StateFlow]s for Compose. Ported from ChatSession.swift.
 */
class ChatSession(
    val chatId: String = UUID.randomUUID().toString(),
    projectPath: String = "",
    title: String? = null,
    val createdAt: String = nowIso(),
) {
    var projectPath: String = projectPath
        private set

    val projectName: String
        get() = projectPath.trimEnd('/').substringAfterLast('/')

    /** The ACP session id, assigned once a live session exists. */
    private val _acpSessionId = MutableStateFlow<String?>(null)
    val acpSessionId: StateFlow<String?> = _acpSessionId.asStateFlow()

    private val _title = MutableStateFlow(title ?: this.projectName)
    val title: StateFlow<String> = _title.asStateFlow()

    private val _items = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val items: StateFlow<List<TranscriptItem>> = _items.asStateFlow()

    private val _configOptions = MutableStateFlow<List<AcpConfigOption>>(emptyList())
    val configOptions: StateFlow<List<AcpConfigOption>> = _configOptions.asStateFlow()

    private val _commands = MutableStateFlow<List<AcpCommand>>(emptyList())
    val commands: StateFlow<List<AcpCommand>> = _commands.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    private val _isArchived = MutableStateFlow(false)
    val isArchived: StateFlow<Boolean> = _isArchived.asStateFlow()

    /** Live context-window occupancy, streamed by the agent during a turn. */
    private val _usage = MutableStateFlow<AcpUsage?>(null)
    val usage: StateFlow<AcpUsage?> = _usage.asStateFlow()

    /** The agent's current plan (task list), when it publishes one. */
    private val _plan = MutableStateFlow<List<AcpPlanEntry>>(emptyList())
    val plan: StateFlow<List<AcpPlanEntry>> = _plan.asStateFlow()

    /** Epoch millis when the in-flight turn started; null when idle. */
    private val _runStartedAt = MutableStateFlow<Long?>(null)
    val runStartedAt: StateFlow<Long?> = _runStartedAt.asStateFlow()

    /** Cumulative token count at the moment the turn started. */
    private var tokensAtRunStart: Int = 0

    /** True until the first prompt is sent, so the UI can show a welcome state. */
    private var isEmptyFlag = true

    /**
     * True for a chat that has never received a prompt (and never attached a
     * live session) — not a conversation, so it isn't persisted.
     */
    val isPristine: Boolean get() = isEmptyFlag && _acpSessionId.value == null

    /**
     * Chunks of a re-delivered previous answer already suppressed (see
     * [appendAssistant]); reset whenever a genuinely new message starts.
     */
    private var replayTail = ""

    /**
     * Changes the user made while this chat had no live ACP session yet; the
     * coordinator replays them onto the session as soon as one attaches.
     */
    val pendingConfigChanges: MutableMap<String, ConfigValue> = mutableMapOf()

    /** Tokens streamed during the in-flight turn. */
    val liveRunTokens: Int
        get() {
            if (_runStartedAt.value == null) return 0
            val total = _usage.value?.tokensUsed ?: return 0
            return maxOf(0, total - tokensAtRunStart)
        }

    // MARK: Lifecycle

    fun setAcpSessionId(id: String?) {
        _acpSessionId.value = id
    }

    fun setPinned(pinned: Boolean) {
        _isPinned.value = pinned
    }

    fun setArchived(archived: Boolean) {
        _isArchived.value = archived
    }

    fun setBusy(busy: Boolean) {
        _isBusy.value = busy
    }

    fun setTitle(title: String) {
        _title.value = title
    }

    /** Marks the start of a prompt turn for the live ticker. */
    fun beginRun() {
        tokensAtRunStart = _usage.value?.tokensUsed ?: 0
        _runStartedAt.value = System.currentTimeMillis()
        _isBusy.value = true
    }

    /** Marks the end of a prompt turn and closes any streaming bubbles. */
    fun endRun(stopReason: String? = null) {
        endStreaming()
        _runStartedAt.value = null
        _isBusy.value = false
    }

    /**
     * Rebuilds this session from a decoded snapshot. The result is "cold": it
     * carries the display transcript but is not wired to a live ACP session.
     */
    fun restoreFrom(stored: StoredSessionData) {
        projectPath = stored.projectPath
        _acpSessionId.value = stored.acpSessionId
        _title.value = stored.title
        _isPinned.value = stored.isPinned
        _isArchived.value = stored.isArchived
        _items.value = stored.items.map(::transcriptItem)
        isEmptyFlag = _items.value.isEmpty()
        _configOptions.value = stored.configOptions ?: emptyList()
        replayTail = ""
    }

    private fun transcriptItem(stored: StoredItemData): TranscriptItem = when (stored) {
        is StoredItemData.Message -> {
            val role = when (stored.role) {
                "assistant" -> ChatMessage.Role.Assistant
                "reasoning" -> ChatMessage.Role.Reasoning
                "notice" -> ChatMessage.Role.Notice(stored.noticeIsError)
                else -> ChatMessage.Role.User
            }
            TranscriptItem.Message(
                ChatMessage(
                    id = stored.id,
                    role = role,
                    text = stored.text,
                    attachments = stored.attachments.map {
                        ImageAttachment(id = it.id, base64Data = it.dataBase64, mimeType = it.mimeType)
                    },
                    timestamp = stored.timestamp,
                )
            )
        }
        is StoredItemData.Tool -> TranscriptItem.Tool(
            ToolCallItem(
                id = stored.id,
                title = stored.title,
                kind = stored.kind,
                status = AcpToolStatus.from(stored.status),
                output = stored.output,
                diffs = stored.diffs.map {
                    ToolCallItem.ToolDiff(it.path, it.oldText, it.newText)
                },
                locations = stored.locations,
                argsJSON = stored.argsJSON,
                timestamp = stored.timestamp,
            )
        )
    }

    // MARK: Config state

    fun option(id: String): AcpConfigOption? = _configOptions.value.firstOrNull { it.id == id }

    /** Replaces the option set; an empty update is ignored (agent quirk). */
    fun applyConfigUpdate(options: List<AcpConfigOption>) {
        if (options.isNotEmpty()) _configOptions.value = options
    }

    /**
     * Updates the displayed value of one option without a round-trip, so the
     * UI reflects the user's choice instantly (the agent is synced separately).
     */
    fun applyLocalConfigValue(configID: String, value: ConfigValue) {
        val options = _configOptions.value.toMutableList()
        val index = options.indexOfFirst { it.id == configID }
        if (index < 0) return
        val option = options[index]
        val kind = option.kind
        val newKind = when {
            kind is AcpConfigOption.Kind.Select && value is ConfigValue.Str ->
                AcpConfigOption.Kind.Select(current = value.value, groups = kind.groups)
            kind is AcpConfigOption.Kind.Bool && value is ConfigValue.Bool ->
                AcpConfigOption.Kind.Bool(current = value.value)
            else -> return
        }
        options[index] = option.copy(kind = newKind)
        _configOptions.value = options
    }

    fun applyLocalConfigValue(configID: String, value: String) =
        applyLocalConfigValue(configID, ConfigValue.Str(value))

    fun applyLocalConfigValue(configID: String, value: Boolean) =
        applyLocalConfigValue(configID, ConfigValue.Bool(value))

    /** The current value of every displayed option, for reconciliation. */
    val displayedConfigValues: Map<String, ConfigValue>
        get() {
            val values = mutableMapOf<String, ConfigValue>()
            for (option in _configOptions.value) {
                when (val kind = option.kind) {
                    is AcpConfigOption.Kind.Select ->
                        if (kind.current.isNotEmpty()) values[option.id] = ConfigValue.Str(kind.current)
                    is AcpConfigOption.Kind.Bool ->
                        values[option.id] = ConfigValue.Bool(kind.current)
                }
            }
            return values
        }

    // MARK: Streaming update dispatch

    /** Routes one decoded session update into the right mutation. */
    fun apply(update: AcpSessionUpdate) {
        when (update) {
            is AcpSessionUpdate.MessageChunk -> appendAssistant(update.text)
            is AcpSessionUpdate.ThoughtChunk -> appendReasoning(update.text)
            is AcpSessionUpdate.ToolCall -> applyToolEvent(update.event, isStart = true)
            is AcpSessionUpdate.ToolCallUpdate -> applyToolEvent(update.event, isStart = false)
            is AcpSessionUpdate.CommandsUpdate -> _commands.value = update.commands
            is AcpSessionUpdate.ConfigUpdate -> applyConfigUpdate(update.options)
            is AcpSessionUpdate.Plan -> _plan.value = update.entries
            is AcpSessionUpdate.Usage -> _usage.value = update.usage
        }
    }

    fun setCommands(commands: List<AcpCommand>) {
        _commands.value = commands
    }

    fun setPlan(entries: List<AcpPlanEntry>) {
        _plan.value = entries
    }

    fun setUsage(usage: AcpUsage?) {
        _usage.value = usage
    }

    // MARK: Transcript mutation

    fun appendUserMessage(text: String, attachments: List<ImageAttachment> = emptyList()) {
        _items.value = _items.value +
            TranscriptItem.Message(ChatMessage(role = ChatMessage.Role.User, text = text, attachments = attachments))
        isEmptyFlag = false
        if (_title.value == projectName || _title.value.isEmpty()) {
            val titleSource = if (text.isEmpty()) "Image" else text
            _title.value = derivedTitle(titleSource)
        }
    }

    fun appendNotice(text: String, isError: Boolean) {
        _items.value = _items.value +
            TranscriptItem.Message(ChatMessage(role = ChatMessage.Role.Notice(isError), text = text))
    }

    /** Appends to (or starts) the current streaming reasoning bubble. */
    fun appendReasoning(delta: String) {
        val list = _items.value.toMutableList()
        val last = list.lastOrNull()
        if (last is TranscriptItem.Message &&
            last.message.role == ChatMessage.Role.Reasoning &&
            last.message.isStreaming
        ) {
            list[list.size - 1] = TranscriptItem.Message(
                last.message.copy(text = last.message.text + delta)
            )
            _items.value = list
        } else {
            list.add(
                TranscriptItem.Message(
                    ChatMessage(role = ChatMessage.Role.Reasoning, text = delta, isStreaming = true)
                )
            )
            _items.value = list
        }
    }

    /**
     * Appends to (or starts) the current streaming assistant answer bubble.
     * Guards against duplicate delivery: the CLI re-sends the turn's final
     * answer in situations like a degraded resume, and blindly appending
     * every chunk duplicates the last message across relaunches.
     */
    fun appendAssistant(delta: String) {
        endReasoningStream()
        val list = _items.value.toMutableList()
        val last = list.lastOrNull()
        if (last is TranscriptItem.Message && last.message.role == ChatMessage.Role.Assistant) {
            val m = last.message
            if (m.isStreaming) {
                list[list.size - 1] = TranscriptItem.Message(m.copy(text = m.text + delta))
                _items.value = list
                return
            }
            // A finished bubble with identical text is a re-delivery — drop it.
            if (m.text == delta) return
            // A finished bubble that is a strict prefix of the new chunk is a
            // re-send of a longer final answer — replace instead of duplicating.
            if (delta.startsWith(m.text)) {
                list[list.size - 1] = TranscriptItem.Message(m.copy(text = delta))
                _items.value = list
                return
            }
            // A re-delivery of the previous turn's answer split across several
            // chunks: suppress chunks while they keep re-stating the tail of
            // the last finished bubble. `replayTail` accumulates what was
            // suppressed, so the whole replay is dropped chunk by chunk
            // instead of becoming a duplicated bubble.
            val candidate = replayTail + delta
            if (m.text.endsWith(candidate)) {
                replayTail = candidate
                return
            }
        }
        replayTail = ""
        list.add(
            TranscriptItem.Message(
                ChatMessage(role = ChatMessage.Role.Assistant, text = delta, isStreaming = true)
            )
        )
        _items.value = list
    }

    /** Merges a `tool_call` / `tool_call_update` event into the transcript. */
    fun applyToolEvent(event: AcpToolCallEvent, isStart: Boolean) {
        val combinedOutput = event.content.joinToString("\n") { it.plainText }
        val diffs = event.content.mapNotNull {
            (it as? AcpToolContent.Diff)?.let { d ->
                ToolCallItem.ToolDiff(d.path, d.oldText, d.newText)
            }
        }

        val list = _items.value.toMutableList()
        val index = list.indexOfFirst { it is TranscriptItem.Tool && it.tool.id == event.toolCallId }
        if (index >= 0) {
            var existing = (list[index] as TranscriptItem.Tool).tool
            event.title?.let { existing = existing.copy(title = it) }
            event.kind?.let { existing = existing.copy(kind = it) }
            event.status?.let { existing = existing.copy(status = it) }
            if (combinedOutput.isNotEmpty()) existing = existing.copy(output = combinedOutput)
            if (diffs.isNotEmpty()) existing = existing.copy(diffs = diffs)
            if (event.locations.isNotEmpty()) existing = existing.copy(locations = event.locations)
            event.rawInput.encodedJsonString()?.let { existing = existing.copy(argsJSON = it) }
            list[index] = TranscriptItem.Tool(existing)
        } else {
            list.add(
                TranscriptItem.Tool(
                    ToolCallItem(
                        id = event.toolCallId,
                        title = event.title ?: "Tool call",
                        kind = event.kind,
                        status = event.status
                            ?: (if (isStart) AcpToolStatus.IN_PROGRESS else AcpToolStatus.COMPLETED),
                        output = combinedOutput,
                        diffs = diffs,
                        locations = event.locations,
                        argsJSON = event.rawInput.encodedJsonString(),
                    )
                )
            )
        }
        _items.value = list
    }

    /** Marks the current streamed bubbles as finished at turn's end. */
    fun endStreaming() {
        endReasoningStream()
        val list = _items.value.toMutableList()
        val last = list.lastOrNull()
        if (last is TranscriptItem.Message && last.message.isStreaming) {
            list[list.size - 1] = TranscriptItem.Message(last.message.copy(isStreaming = false))
            _items.value = list
        }
    }

    private fun endReasoningStream() {
        var changed = false
        val list = _items.value.toMutableList()
        for (i in list.indices) {
            val item = list[i]
            if (item is TranscriptItem.Message &&
                item.message.role == ChatMessage.Role.Reasoning &&
                item.message.isStreaming
            ) {
                list[i] = TranscriptItem.Message(item.message.copy(isStreaming = false))
                changed = true
            }
        }
        if (changed) _items.value = list
    }

    companion object {
        /** The chat title derived from the first prompt: first line, 48 chars. */
        fun derivedTitle(from: String): String {
            val trimmed = from.trim()
            val firstLine = trimmed.lineSequence().firstOrNull() ?: trimmed
            return firstLine.take(48)
        }
    }
}
