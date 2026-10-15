package to.eyed.spettro.mobile.ui.screens.home

import to.eyed.spettro.mobile.core.remote.ChatSummary

/**
 * The list's ordering, as a pure function — a port of the desktop's
 * sessionGroups.ts: most recently active first, folders by their newest
 * chat and chats by their own last activity, with pinned chats lifted to
 * the top of their folder and archived chats set apart.
 */
internal object SessionGroups {
    data class Group(val path: String, val name: String, val chats: List<ChatSummary>)

    data class Groups(val groups: List<Group>, val archived: List<ChatSummary>)

    /** A folder's name: the last component of its path. */
    fun folderName(path: String): String =
        path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

    /**
     * A chat matches on its own title or on its folder's name, so searching
     * a folder surfaces every chat inside it — and, on the phone, on the
     * preview line it shows under the title.
     */
    fun matches(chat: ChatSummary, query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() ||
            chat.title.contains(q, ignoreCase = true) ||
            folderName(chat.projectPath).contains(q, ignoreCase = true) ||
            chat.preview.contains(q, ignoreCase = true)
    }

    fun group(all: List<ChatSummary>, query: String = ""): Groups {
        // Recency, not creation order: the chat just worked in is the one
        // come back to. sortedByDescending is stable, so ties keep the
        // host's order.
        val chats = all.sortedByDescending { parseIsoMillis(it.updatedAt) ?: 0L }
        val buckets = LinkedHashMap<String, MutableList<ChatSummary>>()
        for (chat in chats) {
            if (chat.isArchived || !matches(chat, query)) continue
            buckets.getOrPut(chat.projectPath) { mutableListOf() }.add(chat)
        }
        return Groups(
            groups = buckets.map { (path, group) ->
                Group(path, folderName(path).ifEmpty { "No project" }, pinnedFirst(group))
            },
            archived = pinnedFirst(chats.filter { it.isArchived && matches(it, query) }),
        )
    }

    /** Stable sort lifting pinned chats without disturbing the rest's order. */
    private fun pinnedFirst(chats: List<ChatSummary>): List<ChatSummary> = chats.sortedByDescending { it.isPinned }
}
