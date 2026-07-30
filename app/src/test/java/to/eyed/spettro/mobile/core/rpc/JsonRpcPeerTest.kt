package to.eyed.spettro.mobile.core.rpc

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import to.eyed.spettro.mobile.core.SpettroJson

private class FakeTransport : RpcTransport {
    val sent = mutableListOf<String>()
    var open = true
    var closedWith: Pair<Int, String>? = null

    override fun send(text: String): Boolean {
        if (!open) return false
        sent.add(text)
        return true
    }

    override fun close(code: Int, reason: String) {
        open = false
        closedWith = code to reason
    }

    fun lastFrame(): JsonObject = SpettroJson.parseToJsonElement(sent.last()).jsonObject
}

class JsonRpcPeerTest {

    @Test
    fun `request frames are well formed with monotonic int ids from 1`() = runBlocking {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            peer.request("a/method", buildJsonObject { put("x", 1) })
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            peer.request("b/method")
        }
        assertEquals(2, transport.sent.size)

        val frame1 = SpettroJson.parseToJsonElement(transport.sent[0]).jsonObject
        assertEquals("2.0", frame1["jsonrpc"]!!.jsonPrimitive.content)
        assertEquals(1, frame1["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("a/method", frame1["method"]!!.jsonPrimitive.content)
        assertEquals(1, frame1["params"]!!.jsonObject["x"]!!.jsonPrimitive.content.toInt())

        val frame2 = SpettroJson.parseToJsonElement(transport.sent[1]).jsonObject
        assertEquals(2, frame2["id"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, frame2["params"]!!.jsonObject.size) // empty params = {}

        // Answer out of order: id 2 first, then id 1.
        peer.handleMessage("""{"jsonrpc":"2.0","id":2,"result":{"ok":true}}""")
        peer.handleMessage("""{"jsonrpc":"2.0","id":1,"result":"one"}""")

        assertEquals("one", first.await().jsonPrimitive.content)
        assertEquals("true", second.await().jsonObject["ok"]!!.jsonPrimitive.content)
    }

    @Test
    fun `error responses throw RpcException with code and message`() = runBlocking {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { peer.request("auth") }
        }
        peer.handleMessage("""{"jsonrpc":"2.0","id":1,"error":{"code":-33002,"message":"badProof"}}""")
        val error = pending.await().exceptionOrNull()
        if (error !is RpcException) fail("expected RpcException, got $error")
        error as RpcException
        assertEquals(-33002, error.code)
        assertEquals("badProof", error.message)
    }

    @Test
    fun `notifications are delivered and carry no id`() {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        var seen: Pair<String, JsonObject?>? = null
        peer.onNotification = { method, params -> seen = method to params?.jsonObject }

        peer.handleMessage("""{"jsonrpc":"2.0","method":"_spettro/remote/hello","params":{"hostID":"h1"}}""")
        assertEquals("_spettro/remote/hello", seen!!.first)
        assertEquals("h1", seen!!.second!!["hostID"]!!.jsonPrimitive.content)

        peer.notify("client/note", buildJsonObject { put("n", 5) })
        val frame = transport.lastFrame()
        assertNull(frame["id"])
        assertEquals("client/note", frame["method"]!!.jsonPrimitive.content)
    }

    @Test
    fun `inbound request with int id echoes the id as an int`() {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        peer.onRequest = { _, _, respond -> respond(Result.success(JsonPrimitive("done"))) }

        peer.handleMessage("""{"jsonrpc":"2.0","id":42,"method":"host/ping","params":{}}""")
        val frame = transport.lastFrame()
        // Exact form: an unquoted 42, not "42".
        assertTrue(transport.sent.last().contains("\"id\":42"))
        assertFalse(transport.sent.last().contains("\"id\":\"42\""))
        assertEquals("done", frame["result"]!!.jsonPrimitive.content)
    }

    @Test
    fun `inbound request with string id echoes the id as a string`() {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        peer.onRequest = { _, _, respond -> respond(Result.success(JsonPrimitive("done"))) }

        peer.handleMessage("""{"jsonrpc":"2.0","id":"abc-1","method":"host/ping"}""")
        assertTrue(transport.sent.last().contains("\"id\":\"abc-1\""))
    }

    @Test
    fun `inbound request without a handler gets method-not-found`() {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        peer.handleMessage("""{"jsonrpc":"2.0","id":7,"method":"host/unknown"}""")
        val frame = transport.lastFrame()
        val error = frame["error"]!!.jsonObject
        assertEquals(-32601, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `a handler that responds twice only sends one frame`() {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        peer.onRequest = { _, _, respond ->
            respond(Result.success(JsonPrimitive(1)))
            respond(Result.success(JsonPrimitive(2)))
        }
        peer.handleMessage("""{"jsonrpc":"2.0","id":1,"method":"host/ping"}""")
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun `closing fails pending requests`() = runBlocking {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { peer.request("slow") }
        }
        peer.handleClosed(1006, "gone")
        val error = pending.await().exceptionOrNull()
        if (error !is RpcException) fail("expected RpcException, got $error")
        assertEquals(RpcException.CONNECTION_CLOSED, (error as RpcException).code)
        // And later requests are refused outright.
        try {
            peer.request("late")
            fail("expected RpcException")
        } catch (e: RpcException) {
            assertEquals(RpcException.CONNECTION_CLOSED, e.code)
        }
    }

    @Test
    fun `unparseable and unknown frames are dropped quietly`() {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        var logged = 0
        peer.onLog = { logged++ }
        peer.handleMessage("not json at all")
        peer.handleMessage("[1,2,3]")
        peer.handleMessage("""{"jsonrpc":"2.0","id":999,"result":"nobody waiting"}""")
        assertEquals(2, logged)
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun `null result resolves to JsonNull`() = runBlocking {
        val transport = FakeTransport()
        val peer = JsonRpcPeer(transport)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { peer.request("chats/close") }
        peer.handleMessage("""{"jsonrpc":"2.0","id":1,"result":null}""")
        assertEquals(kotlinx.serialization.json.JsonNull, pending.await())
    }
}
