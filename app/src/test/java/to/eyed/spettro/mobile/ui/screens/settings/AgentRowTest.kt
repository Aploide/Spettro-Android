package to.eyed.spettro.mobile.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings' Agent row, which must not stay a green "Running" through a drop. */
class AgentRowTest {

    @Test
    fun reconnectingSaysNothingAboutTheAgent() {
        // The agent flag is the one from before the drop.
        assertEquals(AgentRow.Unknown, agentRow(isConnected = false, agentReady = true))
        assertEquals(AgentRow.Unknown, agentRow(isConnected = false, agentReady = false))
    }

    @Test
    fun connectedShowsTheAgentsState() {
        assertEquals(AgentRow.Running, agentRow(isConnected = true, agentReady = true))
        assertEquals(AgentRow.Starting, agentRow(isConnected = true, agentReady = false))
    }
}
