package to.eyed.spettro.mobile.core.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a goodbye was: its reason when the host gives one, otherwise read from its message. */
class GoodbyeReasonTest {

    private val off = "Remote access was turned off."

    @Test
    fun aReasonIsTakenAtItsWord() {
        assertEquals(Goodbye.Quit, RemoteClient.goodbyeOf("quit", null))
        assertEquals(Goodbye.Restart, RemoteClient.goodbyeOf("restart", null))
        assertEquals(Goodbye.SharingOff, RemoteClient.goodbyeOf("sharing-off", off))
        // Even where the message would have said otherwise.
        assertEquals(Goodbye.SharingOff, RemoteClient.goodbyeOf("sharing-off", null))
        assertEquals(Goodbye.Quit, RemoteClient.goodbyeOf("quit", off))
    }

    @Test
    fun anOlderHostIsReadFromItsMessage() {
        assertEquals(Goodbye.Quit, RemoteClient.goodbyeOf(null, null))
        assertEquals(Goodbye.Quit, RemoteClient.goodbyeOf(null, " "))
        assertEquals(Goodbye.SharingOff, RemoteClient.goodbyeOf(null, off))
    }

    @Test
    fun aReasonThisPhoneDoesNotKnowFallsBackToTheMessage() {
        assertEquals(Goodbye.Quit, RemoteClient.goodbyeOf("sleep", null))
        assertEquals(Goodbye.SharingOff, RemoteClient.goodbyeOf("sleep", off))
    }

    @Test
    fun anOlderDesktopsTwoGoodbyesOnQuitReadAsAQuit() {
        // No reason: the first goodbye (no message) says it, and the
        // sharing-off one right behind it changes nothing.
        val first = RemoteClient.goodbyeAfter(Goodbye.SharingOff, alreadyStopped = false, reason = null, message = null)
        assertEquals(Goodbye.Quit, first)
        assertEquals(Goodbye.Quit, RemoteClient.goodbyeAfter(first, alreadyStopped = true, reason = null, message = off))
        // Sharing switched off on an older desktop: one goodbye, with a message.
        assertEquals(Goodbye.SharingOff, RemoteClient.goodbyeAfter(Goodbye.Quit, alreadyStopped = false, reason = null, message = off))
    }

    @Test
    fun anExplicitReasonAlwaysCounts() {
        assertEquals(Goodbye.Restart, RemoteClient.goodbyeAfter(Goodbye.SharingOff, alreadyStopped = false, reason = "restart", message = null))
        // Even after a goodbye already stands.
        assertEquals(Goodbye.SharingOff, RemoteClient.goodbyeAfter(Goodbye.Quit, alreadyStopped = true, reason = "sharing-off", message = off))
    }

    @Test
    fun theWireNamesMatchTheDesktop() {
        assertEquals("quit", GoodbyeReason.QUIT)
        assertEquals("restart", GoodbyeReason.RESTART)
        assertEquals("sharing-off", GoodbyeReason.SHARING_OFF)
        assertEquals("goodbyeReason", RemoteFeature.GOODBYE_REASON)
    }
}
