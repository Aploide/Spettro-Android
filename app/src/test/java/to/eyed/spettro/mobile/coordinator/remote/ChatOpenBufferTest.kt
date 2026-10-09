package to.eyed.spettro.mobile.coordinator.remote

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.core.remote.FrameMark
import to.eyed.spettro.mobile.core.remote.InboundNotification
import to.eyed.spettro.mobile.core.remote.RemoteNotification

class ChatOpenBufferTest {

    private fun update(chatID: String, frame: Long, connection: Long = 1) = InboundNotification(
        RemoteNotification.ChatUpdate(chatID, JsonObject(mapOf())),
        FrameMark(connection, frame),
    )

    private fun state(chatID: String, frame: Long) = InboundNotification(
        RemoteNotification.ChatState(ChatSummary(id = chatID), stopReason = "end_turn", notice = null),
        FrameMark(1, frame),
    )

    @Test
    fun `holds only the chat being opened`() {
        val buffer = ChatOpenBuffer()
        assertFalse(buffer.hold(update("c-1", 1)))

        buffer.begin("c-1")
        assertTrue(buffer.hold(update("c-1", 2)))
        assertTrue(buffer.hold(state("c-1", 3)))
        assertFalse(buffer.hold(update("c-2", 4)))
        val host = InboundNotification(RemoteNotification.HostState(true, false, null), FrameMark(1, 5))
        assertFalse(buffer.hold(host))
    }

    @Test
    fun `replays only what came after the reply, in order`() {
        val buffer = ChatOpenBuffer()
        buffer.begin("c-1")
        val before = update("c-1", 4)
        val afterA = update("c-1", 6)
        val afterB = state("c-1", 7)
        listOf(before, afterA, afterB).forEach { buffer.hold(it) }

        val replay = buffer.finish("c-1", reply = FrameMark(1, 5))
        // Frame 4 is already in the snapshot; replaying it would double a chunk.
        assertEquals(listOf(afterA.note, afterB.note), replay)
        assertNull(buffer.opening)
        // Once finished, the chat's updates go straight to the session.
        assertFalse(buffer.hold(update("c-1", 8)))
    }

    @Test
    fun `updates from an older connection are never replayed`() {
        val buffer = ChatOpenBuffer()
        buffer.begin("c-1")
        buffer.hold(update("c-1", 50, connection = 1))
        assertTrue(buffer.finish("c-1", reply = FrameMark(2, 3)).isEmpty())
    }

    @Test
    fun `without a mark nothing is replayed`() {
        val buffer = ChatOpenBuffer()
        buffer.begin("c-1")
        buffer.hold(update("c-1", 9))
        assertTrue(buffer.finish("c-1", reply = null).isEmpty())
    }

    @Test
    fun `a newer open supersedes an older one`() {
        val buffer = ChatOpenBuffer()
        buffer.begin("c-1")
        buffer.hold(update("c-1", 2))
        buffer.begin("c-2")
        buffer.hold(update("c-2", 3))

        assertTrue(buffer.finish("c-1", reply = FrameMark(1, 1)).isEmpty())
        assertEquals("c-2", buffer.opening)
        assertEquals(1, buffer.finish("c-2", reply = FrameMark(1, 1)).size)
    }

    @Test
    fun `chat ids match regardless of case`() {
        // The host lowercases ids for lookup; a summary may carry either.
        val buffer = ChatOpenBuffer()
        buffer.begin("ABC-1")
        assertTrue(buffer.hold(update("abc-1", 2)))
    }

    @Test
    fun `cancel drops everything`() {
        val buffer = ChatOpenBuffer()
        buffer.begin("c-1")
        buffer.hold(update("c-1", 2))
        buffer.cancel()
        assertNull(buffer.opening)
        assertTrue(buffer.finish("c-1", reply = FrameMark(1, 1)).isEmpty())
    }

    @Test
    fun `a failed open of one chat leaves the open of another alone`() {
        // Open A fails after the user went on to open B (two panes):
        // cancelling A must not take B's held updates with it.
        val buffer = ChatOpenBuffer()
        buffer.begin("a")
        buffer.begin("b")
        buffer.hold(update("b", 3))
        buffer.cancel("a")
        assertEquals("b", buffer.opening)
        assertEquals(1, buffer.finish("b", reply = FrameMark(1, 1)).size)

        buffer.begin("c")
        buffer.cancel("c")
        assertNull(buffer.opening)
    }
}
