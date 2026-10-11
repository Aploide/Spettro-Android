package to.eyed.spettro.mobile.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.RemoteAuthError
import to.eyed.spettro.mobile.core.remote.RemoteClient
import to.eyed.spettro.mobile.core.remote.RemoteState

class DisconnectReasonTest {

    private fun offline(reason: OfflineReason) = DisconnectReason.from(RemoteState.Offline(reason))

    @Test
    fun `a passing refusal is not a removal`() {
        // A stale proof, a closed pairing window or a rate limit must never
        // put "Pair again" — which forgets the pairing — in front of the user.
        assertEquals(DisconnectReason.TurnedAway, offline(OfflineReason.Rejected))
        assertEquals(DisconnectReason.VersionMismatch, offline(OfflineReason.VersionMismatch))
        assertEquals(DisconnectReason.Revoked, offline(OfflineReason.NotRecognised))
    }

    @Test
    fun `only revocation codes count as a lost pairing`() {
        assertTrue(RemoteAuthError.isRevocation(RemoteAuthError.NOT_PAIRED))
        assertTrue(RemoteAuthError.isRevocation(RemoteAuthError.REVOKED))
        assertFalse(RemoteAuthError.isRevocation(RemoteAuthError.BAD_PROOF))
        assertFalse(RemoteAuthError.isRevocation(RemoteAuthError.PAIRING_EXPIRED))
        assertFalse(RemoteAuthError.isRevocation(RemoteAuthError.VERSION_MISMATCH))
        // Version mismatch is still permanent — retrying can't fix it.
        assertTrue(RemoteAuthError.isPermanent(RemoteAuthError.VERSION_MISMATCH))
        assertFalse(RemoteAuthError.isPermanent(RemoteAuthError.BAD_PROOF))
    }

    @Test
    fun `every offline reason has its own screen`() {
        assertEquals(DisconnectReason.HostAsleep, offline(OfflineReason.HostNotFound))
        assertEquals(DisconnectReason.SharingOff, offline(OfflineReason.HostStopped))
        assertEquals(DisconnectReason.NoLocalNetwork, offline(OfflineReason.NoLocalNetwork))
        assertEquals(DisconnectReason.Transport, offline(OfflineReason.Transport))
        assertEquals(
            OfflineReason.entries.size,
            OfflineReason.entries.map(::offline).toSet().size,
        )
    }

    @Test
    fun `a deliberate disconnect is not a search`() {
        // Nothing retries after Settings › Disconnect, so "Looking for…" and
        // its spinner would wait forever with no button to press.
        assertEquals(DisconnectReason.Disconnected, DisconnectReason.from(RemoteState.Disconnected))
    }

    @Test
    fun `looking and connecting both read as searching`() {
        assertEquals(DisconnectReason.Searching, DisconnectReason.from(RemoteState.Searching))
        assertEquals(DisconnectReason.Searching, DisconnectReason.from(RemoteState.Connecting))
    }

    @Test
    fun `off Wi-Fi is said only where finding the computer is the problem`() {
        // The phone's real state with Wi-Fi off and no SIM: nothing on the
        // LAN can be found, and "make sure it's awake" sends the user to the
        // wrong device.
        assertTrue(offNetworkHint(DisconnectReason.Searching, hasRemoteHost = false, onLocalNetwork = false))
        assertTrue(offNetworkHint(DisconnectReason.HostAsleep, hasRemoteHost = false, onLocalNetwork = false))
        // On Wi-Fi, or with a tunnel to go through, it isn't the advice.
        assertFalse(offNetworkHint(DisconnectReason.Searching, hasRemoteHost = false, onLocalNetwork = true))
        assertFalse(offNetworkHint(DisconnectReason.Searching, hasRemoteHost = true, onLocalNetwork = false))
        // Paired over USB (adb reverse to 127.0.0.1): no network is needed,
        // and the client keeps trying that route, so Wi-Fi isn't the fix.
        assertFalse(
            offNetworkHint(
                DisconnectReason.Searching,
                hasRemoteHost = false,
                onLocalNetwork = false,
                lastAddressIsLoopback = true,
            ),
        )
        assertFalse(
            offNetworkHint(
                DisconnectReason.HostAsleep,
                hasRemoteHost = false,
                onLocalNetwork = false,
                lastAddressIsLoopback = true,
            ),
        )
        // Other stories keep their own words.
        assertFalse(offNetworkHint(DisconnectReason.Disconnected, hasRemoteHost = false, onLocalNetwork = false))
        assertFalse(offNetworkHint(DisconnectReason.SharingOff, hasRemoteHost = false, onLocalNetwork = false))
        assertFalse(offNetworkHint(DisconnectReason.Revoked, hasRemoteHost = false, onLocalNetwork = false))
    }

    @Test
    fun `a quitting desktop is not sharing switched off`() {
        // Quitting says goodbye with no message, and sharing comes back on
        // with the next launch: "turn it back on in Settings" would be wrong.
        assertTrue(RemoteClient.isQuitGoodbye(null))
        assertTrue(RemoteClient.isQuitGoodbye(""))
        assertFalse(RemoteClient.isQuitGoodbye("Remote access was turned off."))
        val stopped = RemoteState.Offline(OfflineReason.HostStopped)
        assertEquals(DisconnectReason.HostQuit, DisconnectReason.from(stopped, hostQuit = true))
        assertEquals(DisconnectReason.SharingOff, DisconnectReason.from(stopped, hostQuit = false))
        // Only a goodbye is read that way.
        assertEquals(DisconnectReason.HostAsleep, DisconnectReason.from(RemoteState.Offline(OfflineReason.HostNotFound), hostQuit = true))
    }
}
