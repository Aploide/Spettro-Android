package to.eyed.spettro.mobile.core.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

private fun environment(onLocalNetwork: Boolean = true, allowed: Boolean = true) = object : NetworkEnvironment {
    override val onLocalNetwork = onLocalNetwork
    override val localNetworkAllowed = allowed
}

private val bonjour = DiscoveredHost(hostID = "H", name = "Mac", kind = "app", host = "192.168.1.30", port = 7879)

class EndpointTest {

    @Test
    fun `LAN endpoints are plain ws, IPv6 bracketed, scope dropped`() {
        assertEquals("ws://192.168.1.2:7879/", Endpoint.Lan("192.168.1.2", 7879).url)
        assertEquals("ws://[fe80::1]:7879/", Endpoint.Lan("fe80::1%wlan0", 7879).url)
    }
}

class EndpointPlanTest {

    private val secret = Base64Url.encode(ByteArray(32) { 1 })

    @Test
    fun `the link names what actually carries the connection`() {
        val lan = Endpoint.Lan("192.168.1.30", 7879)
        assertEquals(RemoteLink.WiFi, EndpointPlan.linkOf(lan, LocalTransport.WiFi))
        assertEquals(RemoteLink.Ethernet, EndpointPlan.linkOf(lan, LocalTransport.Ethernet))
        assertEquals(RemoteLink.LocalNetwork, EndpointPlan.linkOf(lan, null))
        // A loopback address is a USB port forward, whatever network the phone is on.
        assertEquals(RemoteLink.Usb, EndpointPlan.linkOf(Endpoint.Lan("127.0.0.1", 7879), LocalTransport.WiFi))
        assertEquals(RemoteLink.Usb, EndpointPlan.linkOf(Endpoint.Lan("::1", 7879), null))
    }

    @Test
    fun `orders Bonjour, then the last address`() {
        val plan = EndpointPlan.candidates(bonjour, "192.168.1.20", 7879, environment())
        assertEquals(
            listOf(
                Endpoint.Lan("192.168.1.30", 7879),
                Endpoint.Lan("192.168.1.20", 7879),
            ),
            plan,
        )
    }

    @Test
    fun `the last address is skipped off Wi-Fi and Ethernet`() {
        val plan = EndpointPlan.candidates(null, "192.168.1.20", 7879, environment(onLocalNetwork = false))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a loopback last address is tried off Wi-Fi too`() {
        // A USB port forward (adb reverse) reaches the computer with no
        // network at all; 127.0.0.1 can't be someone else's machine.
        val plan = EndpointPlan.candidates(null, "127.0.0.1", 7879, environment(onLocalNetwork = false))
        assertEquals(listOf(Endpoint.Lan("127.0.0.1", 7879)), plan)
        assertTrue(EndpointPlan.isLoopback("::1"))
        assertTrue(EndpointPlan.isLoopback("localhost"))
        assertTrue(EndpointPlan.isLoopback("127.1.2.3"))
        assertFalse(EndpointPlan.isLoopback("192.168.1.20"))
        assertFalse(EndpointPlan.isLoopback("1270.0.0.1"))
    }

    @Test
    fun `without the local-network permission nothing is left to try`() {
        assertTrue(EndpointPlan.candidates(bonjour, "192.168.1.20", 7879, environment(allowed = false)).isEmpty())
        assertTrue(EndpointPlan.candidates(null, "127.0.0.1", 7879, environment(allowed = false)).isEmpty())
    }

    @Test
    fun `no address and no Bonjour is nowhere to go`() {
        assertTrue(EndpointPlan.candidates(null, null, null, environment()).isEmpty())
    }

    @Test
    fun `every route is a plain ws address on the LAN`() {
        val plans = listOf(
            EndpointPlan.candidates(bonjour, "192.168.1.20", 7879, environment()),
            EndpointPlan.candidates(null, "::1", 7879, environment(onLocalNetwork = false)),
            EndpointPlan.pairingCandidates(
                RemotePairing.parse("h=H&s=$secret&a=192.168.1.20&p=7879&r=spettro.example.com")!!,
                bonjour,
                environment(),
            ),
        ).flatten()
        assertTrue(plans.isNotEmpty())
        for (endpoint in plans) {
            assertTrue(endpoint.url, endpoint is Endpoint.Lan && endpoint.url.startsWith("ws://"))
        }
    }

    @Test
    fun `the permission is the reason only when nothing could be tried`() {
        assertEquals(
            OfflineReason.NoLocalNetwork,
            EndpointPlan.offlineReason(hostDiscovered = false, environment(allowed = false)),
        )
        // A stale Bonjour sighting doesn't count while browsing is refused.
        assertEquals(
            OfflineReason.NoLocalNetwork,
            EndpointPlan.offlineReason(hostDiscovered = true, environment(allowed = false)),
        )
    }

    @Test
    fun `seen on Bonjour is a transport fault, not seen is not here`() {
        assertEquals(OfflineReason.Transport, EndpointPlan.offlineReason(hostDiscovered = true, environment()))
        assertEquals(OfflineReason.HostNotFound, EndpointPlan.offlineReason(hostDiscovered = false, environment()))
        // Off Wi-Fi too: there is no other route to blame.
        assertEquals(
            OfflineReason.HostNotFound,
            EndpointPlan.offlineReason(hostDiscovered = false, environment(onLocalNetwork = false)),
        )
    }

    @Test
    fun `duplicates collapse`() {
        val plan = EndpointPlan.candidates(bonjour, "192.168.1.30", 7879, environment())
        assertEquals(listOf(Endpoint.Lan("192.168.1.30", 7879)), plan)
    }

    @Test
    fun `pairing tries the code's hint, then Bonjour`() {
        val payload = RemotePairing.parse("h=H&s=$secret&a=192.168.1.20&p=7879")!!
        assertEquals(
            listOf(
                Endpoint.Lan("192.168.1.20", 7879),
                Endpoint.Lan("192.168.1.30", 7879),
            ),
            EndpointPlan.pairingCandidates(payload, bonjour, environment()),
        )
        assertTrue(EndpointPlan.pairingCandidates(payload, bonjour, environment(allowed = false)).isEmpty())
    }

    @Test
    fun `an internet hostname in an older code adds no route`() {
        val withR = RemotePairing.parse("h=H&s=$secret&a=192.168.1.20&p=7879&r=spettro.example.com")!!
        val without = RemotePairing.parse("h=H&s=$secret&a=192.168.1.20&p=7879")!!
        assertEquals(
            EndpointPlan.pairingCandidates(without, bonjour, environment()),
            EndpointPlan.pairingCandidates(withR, bonjour, environment()),
        )
        // Without the permission such a code has nowhere to go, rather than the internet.
        assertTrue(EndpointPlan.pairingCandidates(withR, bonjour, environment(allowed = false)).isEmpty())
        val rOnly = RemotePairing.parse("h=H&s=$secret&r=spettro.example.com")!!
        assertTrue(EndpointPlan.pairingCandidates(rOnly, null, environment()).isEmpty())
    }
}

class RaceStaggeredTest {

    @Test
    fun `the first success wins and a late success is discarded`() = runBlocking {
        val discarded = Collections.synchronizedList(mutableListOf<String>())
        val winner = raceStaggered(
            candidates = listOf("slow", "fast"),
            stagger = 20,
            attempt = { name ->
                if (name == "slow") delay(300) else delay(10)
                name
            },
            discard = { discarded.add(it) },
        )
        assertEquals("fast", winner)
        // The slow one was cancelled mid-attempt, so there was nothing to discard.
        assertTrue(discarded.isEmpty())
    }

    @Test
    fun `a failure starts the next candidate without waiting out the head start`() = runBlocking {
        val started = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val t0 = System.nanoTime()
        val winner = raceStaggered(
            candidates = listOf("broken", "good"),
            stagger = 5_000,
            attempt = { name ->
                started.add(name to (System.nanoTime() - t0) / 1_000_000)
                if (name == "broken") throw IllegalStateException("refused")
                name
            },
            discard = {},
        )
        assertEquals("good", winner)
        assertTrue("the second didn't wait for the head start", started.last().second < 1_000)
    }

    @Test
    fun `the first candidate gets its head start`() = runBlocking {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val winner = raceStaggered(
            candidates = listOf("lan", "tunnel"),
            stagger = 200,
            attempt = { name ->
                order.add(name)
                if (name == "lan") delay(20)
                name
            },
            discard = {},
        )
        assertEquals("lan", winner)
        assertEquals(listOf("lan"), order)
    }

    @Test
    fun `success that lands after the winner is handed to discard`() = runBlocking {
        val discarded = Collections.synchronizedList(mutableListOf<String>())
        val bothReady = CompletableDeferred<Unit>()
        val ready = java.util.concurrent.atomic.AtomicInteger(0)
        raceStaggered(
            candidates = listOf("a", "b"),
            stagger = 0,
            attempt = { name ->
                // Neither returns until both are ready, so both succeed.
                if (ready.incrementAndGet() == 2) bothReady.complete(Unit)
                bothReady.await()
                name
            },
            discard = { discarded.add(it) },
        )
        assertEquals(1, discarded.size)
    }

    @Test
    fun `a race cancelled after its win still gives the winner back`() = runBlocking {
        val discarded = Collections.synchronizedList(mutableListOf<String>())
        val stubbornStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val race = launch {
            raceStaggered(
                candidates = listOf("fast", "stubborn"),
                stagger = 0,
                attempt = { name ->
                    if (name == "fast") {
                        stubbornStarted.await()
                    } else {
                        stubbornStarted.complete(Unit)
                        // Ignores cancellation, so the race is still waiting
                        // on it when the caller goes away.
                        withContext(NonCancellable) { release.await() }
                    }
                    name
                },
                discard = { discarded.add(it) },
            )
            fail("the race should have been cancelled")
        }
        stubbornStarted.await()
        delay(20) // "fast" has won; the race waits on "stubborn"
        race.cancel()
        release.complete(Unit)
        race.join()
        // The caller never got the winner, and the straggler succeeded
        // after all: neither may stay open.
        assertEquals(listOf("fast", "stubborn"), discarded.toList())
    }

    @Test
    fun `all failing reports the last failure`() = runBlocking {
        try {
            raceStaggered(
                candidates = listOf(1, 2, 3),
                stagger = 10,
                attempt = { n -> throw IllegalStateException("failed $n") },
                discard = {},
            )
            fail("expected RaceFailure")
        } catch (e: RaceFailure) {
            assertEquals("failed 3", e.cause?.message)
        }
    }

    @Test
    fun `nothing to try is a failure`() = runBlocking {
        try {
            raceStaggered(emptyList<String>(), stagger = 10, attempt = { it }, discard = {})
            fail("expected RaceFailure")
        } catch (_: RaceFailure) {
        }
    }
}

class NotificationGateTest {

    @Test
    fun `holds until opened, then flushes in order and passes the rest straight through`() {
        val delivered = mutableListOf<String>()
        val gate = NotificationGate<String> { delivered.add(it) }
        gate.offer("host/state")
        gate.offer("permission/ask")
        assertTrue(delivered.isEmpty())

        gate.open()
        assertEquals(listOf("host/state", "permission/ask"), delivered)

        gate.offer("chat/update")
        assertEquals(listOf("host/state", "permission/ask", "chat/update"), delivered)
    }

    @Test
    fun `opening twice delivers nothing twice`() {
        val delivered = mutableListOf<String>()
        val gate = NotificationGate<String> { delivered.add(it) }
        gate.offer("a")
        gate.open()
        gate.open()
        assertEquals(listOf("a"), delivered)
    }

    @Test
    fun `discarding drops what was held`() {
        val delivered = mutableListOf<String>()
        val gate = NotificationGate<String> { delivered.add(it) }
        gate.offer("a")
        gate.discard()
        gate.open()
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `a frame offered during the flush lands after the held ones`() {
        val delivered = Collections.synchronizedList(mutableListOf<Int>())
        lateinit var gate: NotificationGate<Int>
        val offerer = Thread { repeat(200) { gate.offer(1_000 + it) } }
        gate = NotificationGate { item ->
            if (item == 0) offerer.start()
            delivered.add(item)
        }
        repeat(100) { gate.offer(it) }
        gate.open()
        offerer.join()
        assertEquals((0 until 100).toList(), delivered.take(100))
        assertEquals(300, delivered.size)
    }
}

class FrameMarkTest {

    @Test
    fun `only frames on the same connection compare`() {
        val reply = FrameMark(connection = 2, frame = 10)
        assertTrue(FrameMark(2, 11).isAfter(reply))
        assertFalse(FrameMark(2, 10).isAfter(reply))
        assertFalse(FrameMark(2, 9).isAfter(reply))
        assertFalse(FrameMark(3, 50).isAfter(reply))
    }
}
