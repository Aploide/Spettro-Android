package to.eyed.spettro.mobile.core.headless

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import to.eyed.spettro.mobile.core.SpettroJson
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Connection lifecycle of the SSE stream. */
sealed class HeadlessConnState {
    /** Reader launched, no live stream yet (also the state between retries' dial). */
    data object Connecting : HeadlessConnState()

    /** SSE stream is open and delivering events. */
    data object Streaming : HeadlessConnState()

    /** Stream lost; next reconnect attempt in [nextRetrySeconds]. */
    data class Disconnected(val nextRetrySeconds: Int) : HeadlessConnState()

    /** Not started, or stopped via [HeadlessClient.stop]. */
    data object Stopped : HeadlessConnState()

    /**
     * The server answered 401 — the token is wrong or stale (the CLI generates
     * a fresh token per run). No automatic retry; the user must re-enter it.
     */
    data object AuthFailed : HeadlessConnState()
}

/** `GET /status` shape. */
data class HeadlessStatus(
    val thinking: Boolean,
    val mode: String,
    val activeAgent: String? = null,
    val sessionId: String? = null,
    val messagesCount: Int = 0,
    val tokensUsed: Long = 0,
    /** RFC3339 timestamp string. */
    val startedAt: String = "",
)

/** `POST /messages` reply. A 409 with parseable body is a refusal, not an error. */
data class SubmitResponse(
    val accepted: Boolean,
    val queued: Boolean = false,
    val note: String? = null,
    val error: String? = null,
)

/** `GET /` shape — service identity plus endpoint list usable as a feature probe. */
data class HeadlessServiceInfo(
    val service: String,
    val endpoints: List<String>,
) {
    /** True when the endpoint list advertises [pathFragment] (e.g. "/ask-user"). */
    fun supports(pathFragment: String): Boolean = endpoints.any { it.contains(pathFragment) }
}

/** Outcome of replying to an approval or ask-user prompt. */
enum class HeadlessReplyResult {
    /** Accepted. */
    Ok,

    /**
     * 404 — nothing pending under that id. In TUI mode `/approval` and
     * `/ask-user` never have pending entries (only headless serves them), so
     * the UI should surface this as "answer on the Mac".
     */
    NotPending,

    /** 409 — someone else answered first. */
    AlreadyAnswered,
}

/** The server rejected the bearer token (HTTP 401). */
class HeadlessAuthException : IOException("unauthorized: bearer token rejected")

/** Any non-2xx reply that is not a protocol-level refusal. */
class HeadlessHttpException(val code: Int, val body: String) :
    IOException("HTTP $code: ${body.take(200)}")

/**
 * Client for the spettro CLI HTTP+SSE control plane (Protocol A).
 *
 * Auth is `Authorization: Bearer <token>` on every request. [start] launches a
 * background SSE reader with auto-reconnect (1s doubling to 30s, reset on
 * success) and replay dedupe by `seq` — the server resends the last 64 events
 * on connect. Pure Kotlin + OkHttp; no android.* dependencies.
 */
class HeadlessClient(
    baseUrl: String,
    private val token: String,
    httpClient: OkHttpClient = OkHttpClient(),
    private val initialBackoffMs: Long = 1_000L,
    private val maxBackoffMs: Long = 30_000L,
) {
    private val root: String = normalizeRoot(baseUrl)

    private val restClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Heartbeat comments arrive every 15s; a 45s read timeout detects a dead
    // TCP connection without ever tripping on a healthy idle stream.
    private val sseClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connection = MutableStateFlow<HeadlessConnState>(HeadlessConnState.Stopped)
    val connection: StateFlow<HeadlessConnState> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<HeadlessEvent>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<HeadlessEvent> = _events.asSharedFlow()

    @Volatile private var readerJob: Job? = null
    @Volatile private var activeCall: Call? = null

    /** Highest event seq seen; replayed/older events are dropped. */
    @Volatile private var maxSeq: Long = 0L

    /** Launches the SSE reader. Safe to call again after [stop]. No-op while running. */
    @Synchronized
    fun start() {
        if (readerJob?.isActive == true) return
        _connection.value = HeadlessConnState.Connecting
        readerJob = scope.launch { runReader() }
    }

    /** Cancels the SSE reader and marks the connection [HeadlessConnState.Stopped]. */
    @Synchronized
    fun stop() {
        readerJob?.cancel()
        readerJob = null
        activeCall?.cancel()
        activeCall = null
        _connection.value = HeadlessConnState.Stopped
    }

    // ---- SSE reader ---------------------------------------------------------

    private suspend fun runReader() {
        var backoffMs = initialBackoffMs
        while (currentCoroutineContext().isActive) {
            _connection.value = HeadlessConnState.Connecting
            var authFailed = false
            try {
                val request = Request.Builder()
                    .url("$root/events")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "text/event-stream")
                    .build()
                val call = sseClient.newCall(request)
                activeCall = call
                call.execute().use { response ->
                    when {
                        response.code == 401 -> authFailed = true
                        !response.isSuccessful ->
                            throw IOException("SSE connect failed: HTTP ${response.code}")
                        else -> {
                            _connection.value = HeadlessConnState.Streaming
                            backoffMs = initialBackoffMs
                            readSseFrames(response)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Fall through to retry.
            } catch (_: Exception) {
                // Defensive: a parse bug must not kill the reader loop.
            } finally {
                activeCall = null
            }

            if (authFailed) {
                _connection.value = HeadlessConnState.AuthFailed
                return
            }
            if (!currentCoroutineContext().isActive) return

            val retrySeconds = (backoffMs / 1000L).toInt().coerceAtLeast(1)
            _connection.value = HeadlessConnState.Disconnected(retrySeconds)
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
        }
    }

    /**
     * Reads SSE frames until EOF/error. Framing per server: `event: <kind>\n`,
     * `id: <seq>\n`, `data: <json>\n\n`; comment lines start with ":" (heartbeat
     * is `: ping`). The data JSON is the full envelope (seq/kind/at/data), so
     * the event/id lines are redundant and ignored.
     */
    private fun readSseFrames(response: Response) {
        val source = response.body.source()
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: return // EOF
            when {
                line.isEmpty() -> {
                    dispatchFrame(data.toString())
                    data.setLength(0)
                }
                line.startsWith(":") -> Unit // comment / heartbeat
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    var value = line.substring("data:".length)
                    if (value.startsWith(" ")) value = value.substring(1)
                    data.append(value)
                }
                else -> Unit // event:/id:/retry: — redundant with the envelope
            }
        }
    }

    private fun dispatchFrame(payload: String) {
        if (payload.isBlank()) return
        val envelope = try {
            SpettroJson.parseToJsonElement(payload) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return
        val seq = envelope.long("seq") ?: 0L
        if (seq > 0) {
            // Replay (last 64 events on every connect) and cross-reconnect
            // duplicates are dropped here. Gaps are normal (slow-subscriber
            // drops server-side) — only ordering is assumed, not contiguity.
            if (seq <= maxSeq) return
            maxSeq = seq
        }
        val event = HeadlessEvent.parse(envelope) ?: return
        _events.tryEmit(event)
    }

    // ---- REST endpoints -----------------------------------------------------

    /** `GET /` — service info; use [HeadlessServiceInfo.supports] as a feature probe. */
    suspend fun probe(): HeadlessServiceInfo {
        val res = call("/", null)
        if (res.code != 200) throw HeadlessHttpException(res.code, res.body)
        val obj = parseObject(res) ?: throw HeadlessHttpException(res.code, res.body)
        return HeadlessServiceInfo(
            service = obj.str("service") ?: "",
            endpoints = obj.strList("endpoints"),
        )
    }

    /** `GET /status`. */
    suspend fun status(): HeadlessStatus {
        val res = call("/status", null)
        if (res.code != 200) throw HeadlessHttpException(res.code, res.body)
        val obj = parseObject(res) ?: throw HeadlessHttpException(res.code, res.body)
        return HeadlessStatus(
            thinking = obj.bool("thinking") ?: false,
            mode = obj.str("mode") ?: "",
            activeAgent = obj.str("active_agent"),
            sessionId = obj.str("session_id"),
            messagesCount = obj.int("messages_count") ?: 0,
            tokensUsed = obj.long("tokens_used") ?: 0L,
            startedAt = obj.str("started_at") ?: "",
        )
    }

    /**
     * `POST /messages`. A message starting with "/" is a slash command (never
     * queued). Returns the server's verdict: a 409 whose body parses as a
     * [SubmitResponse] is a normal refusal (`accepted = false`), not an error.
     */
    suspend fun sendMessage(text: String): SubmitResponse {
        val res = call("/messages", buildJsonObject { put("message", text) })
        val obj = parseObject(res)
        if ((res.code == 200 || res.code == 409) && obj != null) {
            return SubmitResponse(
                accepted = obj.bool("accepted") ?: false,
                queued = obj.bool("queued") ?: false,
                note = obj.str("note")?.takeIf { it.isNotEmpty() },
                error = obj.str("error")?.takeIf { it.isNotEmpty() },
            )
        }
        throw HeadlessHttpException(res.code, res.body)
    }

    /** `POST /interrupt` — cancels the active run (coalesced server-side). */
    suspend fun interrupt() {
        val res = call("/interrupt", buildJsonObject { })
        if (res.code != 200) throw HeadlessHttpException(res.code, res.body)
    }

    /**
     * `POST /approval`. decision: "allow-once" | "allow-always" | anything else
     * = deny; [instead] rides along with a deny as "do this instead".
     */
    suspend fun approve(
        toolId: String,
        decision: String,
        instead: String? = null,
    ): HeadlessReplyResult {
        val body = buildJsonObject {
            put("tool_id", toolId)
            put("decision", decision)
            if (!instead.isNullOrEmpty()) put("instead", instead)
        }
        return replyResult(call("/approval", body))
    }

    /**
     * `POST /ask-user` (v2): [answers] is keyed by question header; a
     * multi-select answer is comma-separated option labels; unanswered
     * questions are simply omitted (they come back "skipped" to the model).
     */
    suspend fun answerAskUser(questionId: String, answers: Map<String, String>): HeadlessReplyResult {
        val body = buildJsonObject {
            put("question_id", questionId)
            putJsonObject("answers") {
                answers.forEach { (header, answer) -> put(header, answer) }
            }
        }
        return replyResult(call("/ask-user", body))
    }

    /** `POST /ask-user` (v1): one flat answer, applied to the first question. */
    suspend fun answerAskUser(questionId: String, answer: String): HeadlessReplyResult {
        val body = buildJsonObject {
            put("question_id", questionId)
            put("answer", answer)
        }
        return replyResult(call("/ask-user", body))
    }

    private fun replyResult(res: HttpResult): HeadlessReplyResult = when (res.code) {
        200 -> HeadlessReplyResult.Ok
        404 -> HeadlessReplyResult.NotPending
        409 -> HeadlessReplyResult.AlreadyAnswered
        else -> throw HeadlessHttpException(res.code, res.body)
    }

    // ---- HTTP plumbing ------------------------------------------------------

    private class HttpResult(val code: Int, val body: String)

    private suspend fun call(path: String, post: JsonObject?): HttpResult {
        val builder = Request.Builder()
            .url(root + path)
            .header("Authorization", "Bearer $token")
        if (post != null) {
            builder.post(post.toString().toRequestBody(JSON_MEDIA))
        }
        val response = restClient.newCall(builder.build()).await()
        response.use {
            val body = try {
                it.body.string()
            } catch (_: IOException) {
                ""
            }
            if (it.code == 401) {
                // Token changed or expired — the CLI regenerates it per run.
                _connection.value = HeadlessConnState.AuthFailed
                throw HeadlessAuthException()
            }
            return HttpResult(it.code, body)
        }
    }

    private fun parseObject(res: HttpResult): JsonObject? = try {
        SpettroJson.parseToJsonElement(res.body) as? JsonObject
    } catch (_: Exception) {
        null
    }

    private suspend fun okhttp3.Call.await(): Response =
        suspendCancellableCoroutine { cont ->
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }
            })
            cont.invokeOnCancellation { cancel() }
        }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private fun normalizeRoot(baseUrl: String): String {
            var url = baseUrl.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            return url
        }
    }
}
