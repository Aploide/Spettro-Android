package to.eyed.spettro.mobile.model

/**
 * The two messages the transcript's actions hang off — a port of the
 * desktop's transcriptAnchors: the newest user message (Edit & resend), and,
 * once the turn it ended is over, the error that ended it (Try again). An
 * error from an earlier turn has been answered by everything since, so it
 * offers nothing; nor does an error that ended no turn (a settings change
 * the agent refused), since resending the prompt would not fix it.
 */
data class TranscriptAnchors(val lastUserMessageId: String?, val retryNoticeId: String?) {
    companion object {
        fun of(items: List<TranscriptItem>, busy: Boolean): TranscriptAnchors {
            var lastUser: String? = null
            var retry: String? = null
            for (i in items.indices.reversed()) {
                val message = (items[i] as? TranscriptItem.Message)?.message ?: continue
                if (message.role == ChatMessage.Role.User) {
                    lastUser = message.id
                    break
                }
                val role = message.role
                // A host without snapshotV2 can't say which error ended a
                // turn; the error the transcript ends on did.
                if (retry == null && role is ChatMessage.Role.Notice && role.isError &&
                    (message.endsTurn || i == items.lastIndex)
                ) {
                    retry = message.id
                }
            }
            if (busy || lastUser == null) retry = null
            return TranscriptAnchors(lastUser, retry)
        }
    }
}
