package to.eyed.spettro.mobile.model

import to.eyed.spettro.mobile.core.acp.AcpPlanEntry

/**
 * The agent's task list as the composer's todo tab draws it — a port of the
 * desktop's TodoList.tsx logic.
 *
 * spettro publishes its todo graph as ACP `plan` updates, the whole list each
 * time. ACP plans have no "blocked" status, so the CLI folds it into the text
 * as a " (blocked)" suffix; it is split off here and drawn as a quiet tag.
 */
object Todos {
    private const val BLOCKED_SUFFIX = " (blocked)"

    enum class Status { PENDING, IN_PROGRESS, COMPLETED }

    data class Entry(val text: String, val status: Status, val blocked: Boolean)

    /** A plan entry as the list draws it. Unknown statuses read as pending. */
    fun entry(plan: AcpPlanEntry): Entry {
        val blocked = plan.content.endsWith(BLOCKED_SUFFIX)
        val text = if (blocked) plan.content.dropLast(BLOCKED_SUFFIX.length) else plan.content
        val status = when (plan.status) {
            "in_progress" -> Status.IN_PROGRESS
            "completed" -> Status.COMPLETED
            else -> Status.PENDING
        }
        return Entry(text, status, blocked)
    }

    /** "3 of 7 tasks done", "All 7 tasks done", "1 task done". */
    fun summary(entries: List<Entry>): String {
        val done = entries.count { it.status == Status.COMPLETED }
        val total = entries.size
        val noun = if (total == 1) "task" else "tasks"
        if (done == total) return if (total == 1) "1 task done" else "All $total tasks done"
        return "$done of $total $noun done"
    }

    /**
     * What the folded tab names: the task under way ("Now: …"), else the
     * next one that isn't blocked ("Next: …"); null when there is neither.
     */
    fun current(entries: List<Entry>): String? {
        entries.firstOrNull { it.status == Status.IN_PROGRESS }?.let { return "Now: ${it.text}" }
        entries.firstOrNull { it.status == Status.PENDING && !it.blocked }?.let { return "Next: ${it.text}" }
        return null
    }
}
