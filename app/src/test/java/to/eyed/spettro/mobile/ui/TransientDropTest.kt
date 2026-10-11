package to.eyed.spettro.mobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.RemoteState

class TransientDropTest {

    private fun offline(reason: OfflineReason, refusals: Int = 0) =
        isTransientDrop(RemoteState.Offline(reason), refusals)

    @Test
    fun `looking, connecting and losing the route are ridden out`() {
        assertTrue(isTransientDrop(RemoteState.Searching))
        assertTrue(isTransientDrop(RemoteState.Connecting))
        assertTrue(offline(OfflineReason.HostNotFound))
        assertTrue(offline(OfflineReason.TunnelUnreachable))
        assertTrue(offline(OfflineReason.Transport))
    }

    @Test
    fun `one refusal is ridden out, a run of them is shown`() {
        assertTrue(offline(OfflineReason.Rejected, refusals = 1))
        assertFalse(offline(OfflineReason.Rejected, refusals = 2))
    }

    @Test
    fun `what needs the user is shown at once`() {
        assertFalse(offline(OfflineReason.NotRecognised))
        assertFalse(offline(OfflineReason.VersionMismatch))
        assertFalse(offline(OfflineReason.HostStopped))
        assertFalse(offline(OfflineReason.NoLocalNetwork))
        assertFalse(isTransientDrop(RemoteState.Unpaired))
        assertFalse(isTransientDrop(RemoteState.Disconnected))
    }
}
