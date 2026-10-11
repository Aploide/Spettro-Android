package to.eyed.spettro.mobile.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Forget also takes this phone off the computer's paired devices. */
class UnpairTest {

    private val host = HostInfo(hostID = "h", hostName = "endermite", hostKind = "app")

    private fun connected(vararg features: String) =
        RemoteState.Connected(host = host, agentReady = true, features = features.toSet())

    @Test
    fun aConnectedHostThatServesUnpairIsTold() {
        assertTrue(RemoteClient.unpairsOnHost(connected(RemoteFeature.UNPAIR, RemoteFeature.RENAME)))
    }

    @Test
    fun anOlderHostIsNotAskedForAMethodItLacks() {
        assertFalse(RemoteClient.unpairsOnHost(connected()))
        assertFalse(RemoteClient.unpairsOnHost(connected(RemoteFeature.RENAME, RemoteFeature.SNAPSHOT_V2)))
    }

    @Test
    fun notConnectedForgetsHereOnly() {
        assertFalse(RemoteClient.unpairsOnHost(RemoteState.Searching))
        assertFalse(RemoteClient.unpairsOnHost(RemoteState.Connecting))
        assertFalse(RemoteClient.unpairsOnHost(RemoteState.Disconnected))
        assertFalse(RemoteClient.unpairsOnHost(RemoteState.Unpaired))
        for (reason in OfflineReason.entries) {
            assertFalse(RemoteClient.unpairsOnHost(RemoteState.Offline(reason)))
        }
    }

    @Test
    fun theMethodAndFlagMatchTheDesktop() {
        assertEquals("_spettro/remote/devices/unpair", RemoteMethod.DEVICES_UNPAIR)
        assertEquals("unpair", RemoteFeature.UNPAIR)
    }
}
