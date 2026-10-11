package to.eyed.spettro.mobile.model

import to.eyed.spettro.mobile.core.acp.AcpToolStatus

/**
 * What a running turn is doing, in the words of the desktop's run ticker
 * (RunTicker.tsx `runPhase`): waiting on the user, thinking, working, or —
 * every tool settled and the model on its own again — writing the answer.
 *
 * That last one matters more than it looks: the CLI does not stream its
 * answer, so after the last tool call there is a stretch where nothing on
 * screen moves, and "Writing the answer…" is the difference between a turn
 * that looks busy and one that looks stuck. The waiting phases say plainly
 * that the turn is held up on whoever holds the phone.
 */
object RunPhase {
    const val AWAITING_APPROVAL = "Waiting for your approval…"
    const val AWAITING_ANSWER = "Waiting for your answer…"
    const val WORKING = "Working…"
    const val THINKING = "Thinking…"
    const val WRITING = "Writing the answer…"

    /**
     * The phase of the turn at the tail of [items].
     *
     * @param awaitingApproval a permission prompt for this chat is pending.
     * @param awaitingAnswer a question for this chat is pending.
     */
    fun of(items: List<TranscriptItem>, awaitingApproval: Boolean = false, awaitingAnswer: Boolean = false): String {
        if (awaitingApproval) return AWAITING_APPROVAL
        if (awaitingAnswer) return AWAITING_ANSWER
        // Only this turn counts: the tail after the newest user message that
        // started one (a steer sent mid-turn doesn't start anything).
        val start = items.indexOfLast { item ->
            item is TranscriptItem.Message &&
                item.message.role == ChatMessage.Role.User &&
                item.message.steering == null
        } + 1
        val turn = items.subList(start, items.size)
        val running = turn.any { item ->
            item is TranscriptItem.Tool &&
                (item.tool.status == AcpToolStatus.IN_PROGRESS || item.tool.status == AcpToolStatus.PENDING)
        }
        if (running) return WORKING
        val last = turn.lastOrNull { !(it is TranscriptItem.Message && it.message.role == ChatMessage.Role.User) }
            ?: return WORKING
        return when {
            last is TranscriptItem.Message && last.message.role == ChatMessage.Role.Reasoning && last.message.isStreaming ->
                THINKING
            last is TranscriptItem.Tool -> WRITING
            last is TranscriptItem.Message && last.message.role !is ChatMessage.Role.Notice -> WRITING
            else -> WORKING
        }
    }

    /**
     * The ticker's elapsed time, the desktop's way: "42s", "3m 7s", then
     * "1h 12m". Rounded to the nearest second, as the desktop rounds.
     */
    fun elapsed(elapsedMillis: Long): String {
        val seconds = Math.round(elapsedMillis / 1000.0).coerceAtLeast(0)
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}m ${seconds % 60}s"
        return "${minutes / 60}h ${minutes % 60}m"
    }
}
