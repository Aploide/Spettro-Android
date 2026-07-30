package to.eyed.spettro.mobile.core.rpc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import to.eyed.spettro.mobile.core.SpettroJson
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A JSON-RPC error surfaced to a caller awaiting a request. */
class RpcException(val code: Int, message: String) : Exception(message) {
    companion object {
        const val PARSE_ERROR = -32700
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val CONNECTION_CLOSED = -32000

        fun closed(reason: String = "connection closed") =
            RpcException(CONNECTION_CLOSED, reason)
    }
}

/**
 * What [JsonRpcPeer] writes to. In production this is an OkHttp WebSocket
 * (see [connectWebSocketPeer]); in tests, anything that captures strings.
 */
interface RpcTransport {
    /** Sends one JSON message as one text frame. Returns false when the socket is gone. */
    fun send(text: String): Boolean

    /** Starts an orderly close. */
    fun close(code: Int, reason: String)
}

/**
 * A symmetric JSON-RPC 2.0 endpoint: both ends send requests, responses, and
 * notifications, one JSON message per WebSocket text frame.
 *
 * Outgoing request ids are monotonically increasing Ints starting at 1.
 * Inbound request ids are echoed back in the exact form they arrived in
 * (int or string).
 *
 * Thread-safety: transport callbacks arrive on arbitrary threads. Pending
 * continuations live in a concurrent map; handlers are volatile and invoked
 * off the main thread — callers hop themselves.
 */
class JsonRpcPeer(
    private val transport: RpcTransport,
    private val json: Json = SpettroJson,
) {
    /** Fired for every inbound notification (a frame with `method` but no `id`). */
    @Volatile
    var onNotification: ((method: String, params: JsonElement?) -> Unit)? = null

    /**
     * Fired for every inbound request. Call `respond` exactly once; extra
     * calls are ignored. When unset, requests are answered with -32601.
     */
    @Volatile
    var onRequest: ((method: String, params: JsonElement?, respond: (Result<JsonElement>) -> Unit) -> Unit)? = null

    /** Fired once when the underlying transport closes or fails. */
    @Volatile
    var onClose: ((code: Int, reason: String) -> Unit)? = null

    @Volatile
    var onLog: ((String) -> Unit)? = null

    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()
    private val closed = AtomicBoolean(false)
    private val closeDelivered = AtomicBoolean(false)

    val isOpen: Boolean get() = !closed.get()

    // MARK: Outbound

    /**
     * Sends a request and suspends until its response arrives.
     * @throws RpcException with the peer's error code/message on an error
     *         response, or [RpcException.CONNECTION_CLOSED] when the socket dies.
     */
    suspend fun request(method: String, params: JsonObject = EMPTY_PARAMS): JsonElement {
        if (closed.get()) throw RpcException.closed()
        val id = nextId.getAndIncrement()
        val waiter = CompletableDeferred<JsonElement>()
        pending[id] = waiter
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val sent = try {
            transport.send(json.encodeToString(JsonObject.serializer(), frame))
        } catch (t: Throwable) {
            pending.remove(id)
            throw RpcException.closed(t.message ?: "send failed")
        }
        if (!sent) {
            pending.remove(id)
            throw RpcException.closed()
        }
        try {
            return waiter.await()
        } finally {
            pending.remove(id)
        }
    }

    /** Sends a notification (no id, no reply). Failures are silent by design. */
    fun notify(method: String, params: JsonObject = EMPTY_PARAMS) {
        if (closed.get()) return
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        try {
            transport.send(json.encodeToString(JsonObject.serializer(), frame))
        } catch (_: Throwable) {
        }
    }

    /** Closes the transport and fails everything still pending. */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                transport.close(1000, "client closed")
            } catch (_: Throwable) {
            }
            failPending("connection closed")
        }
    }

    // MARK: Inbound (driven by the transport owner)

    /** Feed one inbound text frame. */
    fun handleMessage(text: String) {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: Exception) {
            onLog?.invoke("dropping unparseable frame")
            return
        }
        val obj = root as? JsonObject ?: run {
            onLog?.invoke("dropping non-object frame")
            return
        }

        val methodEl = obj["method"]
        if (methodEl is JsonPrimitive && methodEl.isString) {
            val method = methodEl.content
            val idEl = obj["id"]
            if (idEl == null || idEl is JsonNull) {
                onNotification?.invoke(method, obj["params"])
            } else {
                dispatchRequest(method, idEl, obj["params"])
            }
            return
        }

        // A response to one of ours. Our ids are always Ints.
        val idEl = obj["id"] as? JsonPrimitive ?: return
        val id = idEl.intOrNull ?: return
        val waiter = pending.remove(id) ?: return
        val error = obj["error"] as? JsonObject
        if (error != null) {
            val code = (error["code"] as? JsonPrimitive)?.intOrNull ?: -1
            val message = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "unknown error"
            waiter.completeExceptionally(RpcException(code, message))
        } else {
            waiter.complete(obj["result"] ?: JsonNull)
        }
    }

    /** Tell the peer the transport closed or failed. Fails everything pending. */
    fun handleClosed(code: Int, reason: String) {
        closed.set(true)
        failPending(reason.ifEmpty { "connection closed" })
        if (closeDelivered.compareAndSet(false, true)) {
            onClose?.invoke(code, reason)
        }
    }

    // MARK: Private

    private fun dispatchRequest(method: String, id: JsonElement, params: JsonElement?) {
        val handler = onRequest
        if (handler == null) {
            respond(id, Result.failure(RpcException(RpcException.METHOD_NOT_FOUND, "method not supported: $method")))
            return
        }
        // One-shot: a handler that answers twice must not put a second frame
        // with the same id on the wire.
        val answered = AtomicBoolean(false)
        handler(method, params) { result ->
            if (answered.compareAndSet(false, true)) respond(id, result)
        }
    }

    private fun respond(id: JsonElement, result: Result<JsonElement>) {
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            // Echo the id in the exact form it arrived (int or string).
            put("id", id)
            result.fold(
                onSuccess = { put("result", it) },
                onFailure = { error ->
                    val fault = error as? RpcException
                    put("error", buildJsonObject {
                        put("code", fault?.code ?: -32603)
                        put("message", error.message ?: "internal error")
                    })
                },
            )
        }
        try {
            transport.send(json.encodeToString(JsonObject.serializer(), frame))
        } catch (_: Throwable) {
        }
    }

    private fun failPending(reason: String) {
        val waiters = pending.values.toList()
        pending.clear()
        for (waiter in waiters) waiter.completeExceptionally(RpcException.closed(reason))
    }

    companion object {
        val EMPTY_PARAMS = JsonObject(emptyMap())
    }
}
