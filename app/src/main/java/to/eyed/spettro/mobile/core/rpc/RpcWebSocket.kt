package to.eyed.spettro.mobile.core.rpc

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicReference

/**
 * Opens an OkHttp WebSocket to [url] and returns a [JsonRpcPeer] speaking
 * one JSON-RPC message per text frame over it.
 *
 * OkHttp buffers sends queued before the socket finishes opening, so the peer
 * is usable immediately; in this protocol the host speaks first anyway.
 */
fun connectWebSocketPeer(client: OkHttpClient, url: String): JsonRpcPeer {
    val socketRef = AtomicReference<WebSocket?>(null)
    val peer = JsonRpcPeer(object : RpcTransport {
        override fun send(text: String): Boolean = socketRef.get()?.send(text) ?: false

        override fun close(code: Int, reason: String) {
            val socket = socketRef.get() ?: return
            if (!socket.close(code, reason)) socket.cancel()
        }
    })
    val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            peer.handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            peer.handleClosed(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            peer.handleClosed(-1, t.message ?: "socket failure")
        }
    }
    socketRef.set(client.newWebSocket(Request.Builder().url(url).build(), listener))
    return peer
}
