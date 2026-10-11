package to.eyed.spettro.mobile.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a host that turned sharing off is checked on until it's back. */
class GoodbyePollTest {

    private fun cred(lastAddress: String? = null, remoteHost: String? = null) = RemoteCredential(
        hostID = "h",
        hostName = "endermite",
        hostKind = "app",
        deviceKey = ByteArray(32),
        pairedAt = "2026-10-11T00:00:00Z",
        lastAddress = lastAddress,
        lastPort = lastAddress?.let { 7879 },
        remoteHost = remoteHost,
    )

    @Test
    fun aLanAddressIsCheckedTooForNetworksThatFilterBonjour() {
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(lastAddress = "192.168.1.21"), announcedRemoteHost = ""))
    }

    @Test
    fun aTunnelIsChecked() {
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(remoteHost = "spettro.example.com"), announcedRemoteHost = null))
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(), announcedRemoteHost = "spettro.example.com"))
    }

    @Test
    fun nowhereToCheckWaitsForBonjour() {
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(cred(), announcedRemoteHost = ""))
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(null, announcedRemoteHost = null))
    }

    @Test
    fun theLanIsCheckedQuicklyWithABackoffBetweenFullChecks() {
        val checks = GoodbyeChecks(now = 0L)
        var now = 0L
        val waits = mutableListOf<Long>()
        val full = mutableListOf<Boolean>()
        repeat(8) {
            val wait = checks.nextWait(now, lanToCheck = true)
            waits += wait
            now += wait
            full += checks.takeCheck(now)
        }
        // 3, 6, 12, then 15 s apart, cut short so every route is still checked at 45 s.
        assertEquals(listOf(3_000L, 6_000L, 12_000L, 15_000L, 9_000L, 15_000L, 15_000L, 15_000L), waits)
        assertEquals(listOf(false, false, false, false, true, false, false, true), full)
    }

    @Test
    fun withNoLanRouteOnlyTheSlowCheckRuns() {
        val checks = GoodbyeChecks(now = 1_000L)
        assertEquals(RemoteClient.HOST_STOPPED_POLL_MS, checks.nextWait(1_000L, lanToCheck = false))
        assertFalse(checks.takeCheck(30_000L))
        assertTrue(checks.takeCheck(1_000L + RemoteClient.HOST_STOPPED_POLL_MS))
    }

    @Test
    fun comingBackToTheForegroundChecksEveryRouteAtOnce() {
        val checks = GoodbyeChecks(now = 5_000L, fullFirst = true)
        assertEquals(0L, checks.nextWait(5_000L, lanToCheck = true))
        assertTrue(checks.takeCheck(5_000L))
        assertFalse(checks.takeCheck(8_000L))
    }

    @Test
    fun aRefusedHandshakeDuringTheHoldIsSpacedLikeAnyRefusal() {
        // A bad proof or a closed pairing window is an answer: the host is
        // back, so the goodbye no longer holds the loop on its quick checks…
        assertNull(RemoteClient.holdAfterHandshakeFailure(OfflineReason.HostStopped, RemoteAuthError.BAD_PROOF))
        assertNull(RemoteClient.holdAfterHandshakeFailure(OfflineReason.HostStopped, RemoteAuthError.PAIRING_EXPIRED))
        // …while anything that isn't a verdict leaves the goodbye standing.
        assertEquals(OfflineReason.HostStopped, RemoteClient.holdAfterHandshakeFailure(OfflineReason.HostStopped, -32603))
        assertNull(RemoteClient.holdAfterHandshakeFailure(null, RemoteAuthError.BAD_PROOF))
        // The second refusal in a row waits 90 s, well clear of the host's
        // five failures in five minutes.
        assertNull(RemoteClient.refusalSpacingMs(1))
        assertEquals(RemoteClient.REFUSED_RETRY_MS, RemoteClient.refusalSpacingMs(2))
        assertEquals(RemoteClient.REFUSED_RETRY_MS, RemoteClient.refusalSpacingMs(5))
    }
}
