package to.eyed.spettro.mobile.coordinator.remote

import to.eyed.spettro.mobile.core.remote.FrameMark
import to.eyed.spettro.mobile.core.remote.InboundNotification
import to.eyed.spettro.mobile.core.remote.RemoteNotification

/**
 * Bridges the gap between asking for a chat's snapshot and showing it.
 *
 * The host emits each live update and applies it to its own copy in the
 * same tick, and builds the `chats/open` snapshot synchronously — so every
 * update that reached the phone *before* the reply is already in the
 * snapshot, and every one *after* it is not. Until the new session is
 * installed there is nowhere to apply the later ones, and dropping them
 * would tear a streaming answer in two. So the chat's updates are held from
 * the moment the open starts, and only those after the reply are replayed.
 *
 * Main-thread only, like the model that owns it.
 */
internal class ChatOpenBuffer {
    private var chatID: String? = null
    private val held = mutableListOf<InboundNotification>()

    /** The chat being opened right now, if any. */
    val opening: String? get() = chatID

    /** Starts holding [chatID]'s updates; supersedes any open still in flight. */
    fun begin(chatID: String) {
        this.chatID = chatID
        held.clear()
    }

    /** Keeps [item] when it belongs to the chat being opened; false otherwise. */
    fun hold(item: InboundNotification): Boolean {
        val target = chatID ?: return false
        val id = chatIDOf(item.note) ?: return false
        if (!id.equals(target, ignoreCase = true)) return false
        held.add(item)
        return true
    }

    /**
     * Ends the open of [chatID] and hands back what to replay, in arrival
     * order: the held updates that came after [reply]. Without a mark there
     * is no telling, and replaying a duplicate chunk is worse than a gap the
     * next update closes, so nothing is replayed. Empty when this open was
     * superseded by another.
     */
    fun finish(chatID: String, reply: FrameMark?): List<RemoteNotification> {
        if (this.chatID != chatID) return emptyList()
        val replay = if (reply == null) emptyList() else held.filter { it.mark.isAfter(reply) }.map { it.note }
        cancel()
        return replay
    }

    /** Abandons the open (it failed, or the connection went). */
    fun cancel() {
        chatID = null
        held.clear()
    }

    companion object {
        /** The chat a session-level notification is about; null for everything else. */
        fun chatIDOf(note: RemoteNotification): String? = when (note) {
            is RemoteNotification.ChatUpdate -> note.chatID
            is RemoteNotification.ChatUser -> note.chatID
            is RemoteNotification.ChatState -> note.chat.id
            else -> null
        }
    }
}
