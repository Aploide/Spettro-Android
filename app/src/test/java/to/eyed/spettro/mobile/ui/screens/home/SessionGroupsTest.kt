package to.eyed.spettro.mobile.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.ChatSummary

/** The desktop's sessionGroups.ts, ordered the same way. */
class SessionGroupsTest {

    private fun chat(
        id: String,
        path: String,
        minute: Int,
        pinned: Boolean = false,
        archived: Boolean = false,
        title: String = "chat $id",
    ) = ChatSummary(
        id = id,
        title = title,
        projectPath = path,
        updatedAt = "2026-10-10T10:%02d:00Z".format(minute),
        isPinned = pinned,
        isArchived = archived,
    )

    private val chats = listOf(
        chat("a1", "/w/acme", 10),
        chat("b1", "/w/beta", 30),
        chat("a2", "/w/acme", 40),
        chat("a3", "/w/acme", 5, pinned = true),
        chat("x", "/w/acme", 50, archived = true),
    )

    @Test
    fun foldersByTheirNewestChatChatsByRecencyPinnedFirst() {
        val g = SessionGroups.group(chats)
        assertEquals(listOf("/w/acme", "/w/beta"), g.groups.map { it.path })
        assertEquals(listOf("acme", "beta"), g.groups.map { it.name })
        assertEquals(listOf("a3", "a2", "a1"), g.groups[0].chats.map { it.id })
        assertEquals(listOf("x"), g.archived.map { it.id })
    }

    @Test
    fun searchMatchesTheTitleOrTheFolderName() {
        val titled = chats + chat("t", "/w/gamma", 1, title = "Fix the login")
        assertEquals(listOf("t"), SessionGroups.group(titled, "LOGIN").groups.flatMap { it.chats }.map { it.id })
        assertEquals(listOf("b1"), SessionGroups.group(chats, "bet").groups.flatMap { it.chats }.map { it.id })
        assertEquals(listOf("x"), SessionGroups.group(chats, "acme").archived.map { it.id })
    }

    @Test
    fun aChatWithNoFolderStillHasAHeading() {
        assertEquals("No project", SessionGroups.group(listOf(chat("n", "", 1))).groups[0].name)
        assertEquals("proj", SessionGroups.folderName("C:\\dev\\proj\\"))
    }
}
