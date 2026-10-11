package to.eyed.spettro.mobile.model

/**
 * The question card's footer rules, from the desktop's QuestionCard.tsx:
 * whether leaving would throw answers away, and the hint that some questions
 * will go unanswered.
 */
object QuestionForm {
    /** One question's working answer. */
    data class Draft(
        val selected: List<String> = emptyList(),
        val custom: String = "",
        val note: String = "",
    ) {
        /** Answered: something picked, or the user's own words. */
        val answered: Boolean get() = selected.isNotEmpty() || custom.isNotBlank()

        /** Anything the user would lose by leaving — a note included. */
        val touched: Boolean get() = answered || note.isNotBlank()
    }

    /** Skip all asks "Discard your answers?" first when this is true. */
    fun dirty(drafts: Collection<Draft>): Boolean = drafts.any { it.touched }

    /**
     * "2 questions will be sent as skipped", on the last question of a form
     * of several once something is answered and something isn't; else null.
     */
    fun skippedHint(isLast: Boolean, drafts: Collection<Draft>): String? {
        val unanswered = drafts.count { !it.answered }
        if (!isLast || drafts.size < 2 || unanswered == 0 || drafts.none { it.answered }) return null
        val noun = if (unanswered == 1) "1 question" else "$unanswered questions"
        return "$noun will be sent as skipped"
    }
}
