package to.eyed.spettro.mobile.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The dot by the computer's name in the chat list's head. */
class HeaderDotTest {

    @Test
    fun reconnectingIsNeverGreen() {
        // The agent flag is stale while the socket is down: it says nothing.
        assertEquals(HeaderDot.Reconnecting, headerDot(reconnecting = true, agentReady = true))
        assertEquals(HeaderDot.Reconnecting, headerDot(reconnecting = true, agentReady = false))
    }

    @Test
    fun attachedWithTheAgentDownIsAmberAndStill() {
        assertEquals(HeaderDot.AgentDown, headerDot(reconnecting = false, agentReady = false))
    }

    @Test
    fun attachedWithTheAgentUpIsGreen() {
        assertEquals(HeaderDot.Connected, headerDot(reconnecting = false, agentReady = true))
    }
}
