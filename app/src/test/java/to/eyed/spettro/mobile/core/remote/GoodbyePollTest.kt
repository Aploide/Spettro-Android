package to.eyed.spettro.mobile.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a host that turned sharing off is checked on until it's back. */
class GoodbyePollTest {

    private fun cred(lastAddress: String? = null) = RemoteCredential(
        hostID = "h",
        hostName = "endermite",
        hostKind = "app",
        deviceKey = ByteArray(32),
        pairedAt = "2026-10-11T00:00:00Z",
        lastAddress = lastAddress,
        lastPort = lastAddress?.let { 7879 },
    )

    @Test
    fun aLanAddressIsCheckedTooForNetworksThatFilterBonjour() {
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(lastAddress = "192.168.1.21")))
        // A USB port forward too.
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(lastAddress = "127.0.0.1")))
    }

    @Test
    fun nowhereToCheckWaitsForBonjour() {
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(cred()))
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(cred(lastAddress = " ")))
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(null))
    }

    /** Runs [checks] from [from] until [until]: each wait, and whether the check after it was full. */
    private fun run(checks: GoodbyeChecks, from: Long, until: Long): List<Pair<Long, Boolean>> {
        var now = from
        val out = mutableListOf<Pair<Long, Boolean>>()
        while (now < until) {
            val wait = checks.nextWait(now, lanToCheck = true)
            now += wait
            out += wait to checks.takeCheck(now)
        }
        return out
    }

    @Test
    fun theLanIsCheckedEveryTwoSecondsForTwoMinutesThenWithABackoff() {
        val checks = GoodbyeChecks(now = 0L)
        val steps = run(checks, from = 0L, until = 150_000L)
        var at = 0L
        val fullAt = mutableListOf<Long>()
        val waitsAfterFast = mutableListOf<Long>()
        for ((wait, full) in steps) {
            // Never more than 2 s apart while fast, whatever falls due.
            if (at < RemoteClient.GOODBYE_LAN_FAST_FOR_MS) assertTrue(wait <= 2_000L) else waitsAfterFast += wait
            at += wait
            if (full) fullAt += at
        }
        // A relaunched desktop is found within 2 s: the first check is at 2 s.
        assertEquals(2_000L, steps.first().first)
        // Every route is still checked every 45 s.
        assertEquals(listOf(45_000L, 90_000L, 135_000L), fullAt)
        // After two minutes: 3, 6, then 12 s apart (cut short for the 135 s full check), and so on.
        assertEquals(listOf(3_000L, 6_000L, 6_000L), waitsAfterFast.take(3))
    }

    @Test
    fun afterTheFastStretchTheBackoffCapsAtFifteenSeconds() {
        val checks = GoodbyeChecks(now = 0L)
        val steps = run(checks, from = 0L, until = 400_000L)
        var at = 0L
        val later = steps.filter { (wait, _) -> at += wait; at > 200_000L }.map { it.first }
        assertTrue(later.all { it <= RemoteClient.GOODBYE_LAN_MAX_MS })
        assertTrue(later.contains(RemoteClient.GOODBYE_LAN_MAX_MS))
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
