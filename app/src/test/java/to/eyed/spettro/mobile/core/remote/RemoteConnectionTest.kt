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
        assertEquals(RemoteRoute.Lan, Endpoint.Lan("192.168.1.2", 7879).route)
    }

    @Test
    fun `the tunnel is wss on the default port with no path`() {
        val tunnel = Endpoint.Tunnel("spettro.example.com")
        assertEquals("wss://spettro.example.com/", tunnel.url)
        assertEquals(RemoteRoute.Internet, tunnel.route)
    }
}

class EndpointPlanTest {

    @Test
    fun `orders Bonjour, then the last address, then the tunnel`() {
        val plan = EndpointPlan.candidates(bonjour, "192.168.1.20", 7879, "spettro.example.com", environment())
        assertEquals(
            listOf(
                Endpoint.Lan("192.168.1.30", 7879),
                Endpoint.Lan("192.168.1.20", 7879),
                Endpoint.Tunnel("spettro.example.com"),
            ),
            plan,
        )
    }

    @Test
    fun `the last address is skipped off Wi-Fi and Ethernet`() {
        val plan = EndpointPlan.candidates(null, "192.168.1.20", 7879, "spettro.example.com", environment(onLocalNetwork = false))
        assertEquals(listOf(Endpoint.Tunnel("spettro.example.com")), plan)
    }

    @Test
    fun `without the local-network permission only the tunnel is left`() {
        val plan = EndpointPlan.candidates(bonjour, "192.168.1.20", 7879, "spettro.example.com", environment(allowed = false))
        assertEquals(listOf(Endpoint.Tunnel("spettro.example.com")), plan)
        assertTrue(EndpointPlan.candidates(bonjour, "192.168.1.20", 7879, null, environment(allowed = false)).isEmpty())
    }

    @Test
    fun `a stored tunnel alone is still somewhere to go`() {
        // No Bonjour and no address used to mean "search forever".
        assertEquals(
            listOf(Endpoint.Tunnel("spettro.example.com")),
            EndpointPlan.candidates(null, null, null, "spettro.example.com", environment()),
        )
        assertTrue(EndpointPlan.candidates(null, null, null, "", environment()).isEmpty())
    }

    @Test
    fun `duplicates collapse`() {
        val plan = EndpointPlan.candidates(bonjour, "192.168.1.30", 7879, null, environment())
        assertEquals(listOf(Endpoint.Lan("192.168.1.30", 7879)), plan)
    }

    @Test
    fun `pairing tries the code's hint, Bonjour, then its tunnel`() {
        val secret = Base64Url.encode(ByteArray(32) { 1 })
        val payload = RemotePairing.parse("h=H&s=$secret&a=192.168.1.20&p=7879&r=spettro.example.com")!!
        assertEquals(
            listOf(
                Endpoint.Lan("192.168.1.20", 7879),
                Endpoint.Lan("192.168.1.30", 7879),
                Endpoint.Tunnel("spettro.example.com"),
            ),
            EndpointPlan.pairingCandidates(payload, bonjour, environment()),
        )
        assertEquals(
            listOf(Endpoint.Tunnel("spettro.example.com")),
            EndpointPlan.pairingCandidates(payload, bonjour, environment(allowed = false)),
        )
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
