package to.eyed.spettro.mobile.core.remote

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.rpc.RpcException

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

    // MARK: Order

    /** Records what Forget did, in order; a socket that closes once asked. */
    private class Fake {
        val steps = mutableListOf<String>()
        var closeHandler: (() -> Unit)? = { steps += "reconnect" }

        fun detach() {
            steps += "detach"
            closeHandler = null
        }

        fun forget() {
            steps += "forget"
        }

        /** The host answers, then closes the socket. */
        fun hostAnswersAndCloses() {
            steps += "request"
            closeHandler?.invoke()
        }
    }

    @Test
    fun theHostsCloseAfterItsAnswerStartsNoReconnect() = runTest {
        val fake = Fake()
        val removed = RemoteClient.unpairThenForget(
            tellHost = true,
            detachClose = fake::detach,
            request = { fake.hostAnswersAndCloses() },
            forget = fake::forget,
            told = { fake.steps += "told $it" },
        )
        assertEquals(true, removed)
        assertEquals(listOf("detach", "request", "told true", "forget"), fake.steps)
    }

    @Test
    fun anErrorStillForgetsHere() = runTest {
        val fake = Fake()
        val removed = RemoteClient.unpairThenForget(
            tellHost = true,
            detachClose = fake::detach,
            request = {
                fake.steps += "request"
                throw RpcException(-32601, "method not supported")
            },
            forget = fake::forget,
        )
        assertEquals(false, removed)
        assertEquals(listOf("detach", "request", "forget"), fake.steps)
    }

    @Test
    fun aHostThatNeverAnswersIsWaitedForThreeSecondsThenForgotten() = runTest {
        val fake = Fake()
        val removed = RemoteClient.unpairThenForget(
            tellHost = true,
            detachClose = fake::detach,
            request = {
                fake.steps += "request"
                awaitCancellation()
            },
            forget = fake::forget,
        )
        assertEquals(false, removed)
        assertEquals(RemoteClient.UNPAIR_TIMEOUT_MS, currentTime)
        assertEquals(listOf("detach", "request", "forget"), fake.steps)
    }

    @Test
    fun aHostWithoutUnpairIsNeverAsked() = runTest {
        val fake = Fake()
        val removed = RemoteClient.unpairThenForget(
            tellHost = false,
            detachClose = fake::detach,
            request = { fake.steps += "request" },
            forget = fake::forget,
        )
        assertNull(removed)
        assertEquals(listOf("forget"), fake.steps)
    }
}
