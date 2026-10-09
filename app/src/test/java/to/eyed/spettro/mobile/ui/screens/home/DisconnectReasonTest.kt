package to.eyed.spettro.mobile.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.RemoteAuthError
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
        assertEquals(DisconnectReason.Unreachable, offline(OfflineReason.TunnelUnreachable))
        assertEquals(DisconnectReason.SharingOff, offline(OfflineReason.HostStopped))
        assertEquals(DisconnectReason.NoLocalNetwork, offline(OfflineReason.NoLocalNetwork))
        assertEquals(DisconnectReason.Transport, offline(OfflineReason.Transport))
        assertEquals(
            OfflineReason.entries.size,
            OfflineReason.entries.map(::offline).toSet().size,
        )
    }

    @Test
    fun `looking and connecting both read as searching`() {
        assertEquals(DisconnectReason.Searching, DisconnectReason.from(RemoteState.Searching))
        assertEquals(DisconnectReason.Searching, DisconnectReason.from(RemoteState.Connecting))
    }
}
