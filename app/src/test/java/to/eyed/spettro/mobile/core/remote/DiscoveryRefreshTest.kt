package to.eyed.spettro.mobile.core.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Browsing Bonjour afresh while the host is away, so a relaunched desktop is found in seconds. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryRefreshTest {

    // MARK: Schedule

    @Test
    fun twoSecondsThenEveryTenThenEveryThirtyAfterFiveMinutes() {
        val schedule = DiscoveryRefresh(startedAt = 1_000L)
        var now = 1_000L
        val waits = mutableListOf<Long>()
        while (now - 1_000L < 330_000L) {
            val wait = schedule.nextWait(now)
            waits += wait
            now += wait
        }
        assertEquals(2_000L, waits.first())
        // 2 s, then 10 s apart until 5 min have gone by…
        val fast = waits.drop(1).takeWhile { it == RemoteClient.NSD_REFRESH_EVERY_MS }
        assertEquals(30, fast.size) // 2 s + 30 × 10 s = 302 s
        // …and 30 s apart after that.
        assertTrue(waits.drop(1 + fast.size).all { it == RemoteClient.NSD_REFRESH_SLOW_MS })
        assertTrue(waits.size > 1 + fast.size)
    }

    // MARK: When

    private val host = HostInfo(hostID = "h", hostName = "endermite", hostKind = "app")

    private fun refreshes(
        state: RemoteState,
        paired: Boolean = true,
        inBackgroundGrace: Boolean = false,
        localNetworkAllowed: Boolean = true,
    ) = RemoteClient.refreshesDiscovery(state, paired, inBackgroundGrace, localNetworkAllowed)

    @Test
    fun aHostThatMayComeBackByItselfIsLookedFor() {
        assertTrue(refreshes(RemoteState.Searching))
        assertTrue(refreshes(RemoteState.Connecting))
        assertTrue(refreshes(RemoteState.Offline(OfflineReason.HostStopped)))
        assertTrue(refreshes(RemoteState.Offline(OfflineReason.HostNotFound)))
        assertTrue(refreshes(RemoteState.Offline(OfflineReason.Transport)))
        assertTrue(refreshes(RemoteState.Offline(OfflineReason.Rejected)))
    }

    @Test
    fun connectedDisconnectedOrAVerdictForTheUserIsNot() {
        assertFalse(refreshes(RemoteState.Connected(host, agentReady = true)))
        assertFalse(refreshes(RemoteState.Disconnected))
        assertFalse(refreshes(RemoteState.Unpaired))
        assertFalse(refreshes(RemoteState.Offline(OfflineReason.NotRecognised)))
        assertFalse(refreshes(RemoteState.Offline(OfflineReason.VersionMismatch)))
        assertFalse(refreshes(RemoteState.Offline(OfflineReason.NoLocalNetwork)))
    }

    @Test
    fun outOfSightUnpairedOrWithoutThePermissionNothingIsBrowsed() {
        val stopped = RemoteState.Offline(OfflineReason.HostStopped)
        assertFalse(refreshes(stopped, inBackgroundGrace = true))
        assertFalse(refreshes(stopped, paired = false))
        assertFalse(refreshes(stopped, localNetworkAllowed = false))
        assertFalse(refreshes(RemoteState.Searching, inBackgroundGrace = true))
    }

    // MARK: Loop

    private class Fake(val scope: TestScope) {
        var allowed = true
        val refreshedAt = mutableListOf<Long>()
        var starts = 0
        val loop = DiscoveryRefreshLoop(
            scope = scope.backgroundScope,
            clock = { scope.testScheduler.currentTime },
            shouldRefresh = { allowed },
            refresh = { refreshedAt += scope.testScheduler.currentTime },
            onStart = { starts++ },
        )
    }

    @Test
    fun theLoopRefreshesOnScheduleAndEnsureIsIdempotent() = runTest {
        val f = Fake(this)
        f.loop.ensure()
        f.loop.ensure() // a second goodbye, a failed retry: still one loop
        advanceTimeBy(32_001L)
        assertEquals(listOf(2_000L, 12_000L, 22_000L, 32_000L), f.refreshedAt)
        assertEquals(1, f.starts)
        assertTrue(f.loop.isRunning)
    }

    @Test
    fun connectingCancelsAndTheNextStretchStartsTheScheduleOver() = runTest {
        val f = Fake(this)
        f.loop.ensure()
        advanceTimeBy(12_001L)
        f.loop.cancel()
        assertFalse(f.loop.isRunning)
        advanceTimeBy(60_000L)
        assertEquals(listOf(2_000L, 12_000L), f.refreshedAt)
        f.loop.ensure()
        advanceTimeBy(2_001L)
        assertEquals(listOf(2_000L, 12_000L, 74_001L), f.refreshedAt)
        assertEquals(2, f.starts)
    }

    @Test
    fun theLoopEndsOnceItShouldNotRefreshAndDoesNotStartThen() = runTest {
        val f = Fake(this)
        f.loop.ensure()
        advanceTimeBy(2_001L)
        f.allowed = false // the background grace began, or Disconnect
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(listOf(2_000L), f.refreshedAt)
        assertFalse(f.loop.isRunning)
        f.loop.ensure()
        assertFalse(f.loop.isRunning)
        assertEquals(1, f.starts)
        f.allowed = true // back in sight
        f.loop.ensure()
        assertTrue(f.loop.isRunning)
    }
}
