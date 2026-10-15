package to.eyed.spettro.mobile.coordinator.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long Undo is offered before a delete happens (desktop `UNDO_WINDOW_MS`). */
const val UNDO_WINDOW_MS = 8_000L

/**
 * Deleting a chat, with a way back — a port of the desktop's
 * state/pendingDeletes.ts. Delete asks first, and even then the chat isn't
 * deleted yet: it leaves the list at once, a toast offers Undo for
 * [UNDO_WINDOW_MS], and only when that runs out is the computer told.
 * Undo puts the row back where it was; nothing on the computer was touched.
 *
 * The timer is injected so a test can drive the clock: [setTimer] runs its
 * block after the delay and returns a way to cancel it.
 */
class DeleteScheduler(
    private val delayMs: Long = UNDO_WINDOW_MS,
    private val setTimer: (delayMs: Long, run: () -> Unit) -> (() -> Unit),
) {
    private class Entry(val cancel: () -> Unit, val commit: () -> Unit)

    private val entries = LinkedHashMap<String, Entry>()
    private val _pending = MutableStateFlow<Set<String>>(emptySet())

    /** The chats on their way out, which every list of chats leaves out. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    /** Hides [id] now and runs [commit] once the window passes. */
    fun schedule(id: String, commit: () -> Unit) {
        entries.remove(id)?.cancel?.invoke()
        entries[id] = Entry(setTimer(delayMs) { run(id) }, commit)
        changed()
    }

    /** Takes a scheduled delete back; false when it already ran (or never was). */
    fun undo(id: String): Boolean {
        val entry = entries.remove(id) ?: return false
        entry.cancel()
        changed()
        return true
    }

    /** Runs every scheduled delete now (the app is going away). */
    fun flush() {
        for (id in entries.keys.toList()) {
            entries[id]?.cancel?.invoke()
            run(id)
        }
    }

    /** Drops every scheduled delete without running it (another computer now). */
    fun cancelAll() {
        entries.values.forEach { it.cancel() }
        entries.clear()
        changed()
    }

    private fun run(id: String) {
        val entry = entries.remove(id) ?: return
        changed()
        entry.commit()
    }

    private fun changed() {
        _pending.value = entries.keys.toSet()
    }
}
