package to.eyed.spettro.mobile.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/**
 * Sends the thinking slider's moves to the CLI, one call at a time. Port of
 * the desktop's `useCommit` (ThinkingSlider.tsx).
 *
 * A finger or a held key can produce moves faster than the CLI answers, so
 * moves made while one is in flight collapse into the latest: Low → Max in
 * four presses sends Medium and then Extra high, not four interleaved
 * changes. Each call waits for the one before it ([send] returns when the
 * host has answered): Ultra is two calls, and two replies racing each other
 * would each carry the other option's old value.
 *
 * It lives with the chat rather than the slider, so a sheet closed mid-move
 * still sends where the move had got to. Single-threaded: [commit] is called
 * on the main thread and [scope] runs there.
 */
class ThinkingCommitter(
    private val scope: CoroutineScope,
    /** Whether the CLI's Ultra is on, as the chat's options say right now. */
    private val ultraOnNow: () -> Boolean,
    /**
     * Sends one call and returns once it is answered. A refusal is not an
     * error here: the host files it as a notice and rolls the option back,
     * and the slider follows the options.
     */
    private val send: suspend (ConfigCall) -> Unit,
    /**
     * How long to leave between two calls. Zero for a host that answers when
     * the set is done; an older one answers before it even starts, so the
     * pair is spaced instead of sent back to back.
     */
    private val spacingMs: () -> Long = { 0L },
) {
    private var target: ThinkingStop? = null
    private var running = false

    /**
     * What the CLI's Ultra is, as far as this queue knows: the options while
     * idle, its own last call while running (the options lag a round trip).
     */
    private var ultraOn = false

    /**
     * Queues a move to [stop]. Completes true once the queue has drained —
     * false at once when a run is already in progress and will pick this
     * move up, so only the last move of a burst lets its preview go.
     */
    fun commit(stop: ThinkingStop): Deferred<Boolean> {
        target = stop
        if (running) return CompletableDeferred(false)
        running = true
        ultraOn = ultraOnNow()
        return scope.async {
            try {
                var first = true
                while (true) {
                    val next = target ?: break
                    target = null
                    for (call in Thinking.callsFor(ultraOn, next)) {
                        if (!first) spacingMs().takeIf { it > 0 }?.let { delay(it) }
                        first = false
                        try {
                            send(call)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Filed by the host as a notice; the options roll back.
                        }
                        if (call.configId == Thinking.ULTRA_ID) {
                            ultraOn = (call.value as? ConfigValue.Bool)?.value == true
                        }
                    }
                }
            } finally {
                running = false
            }
            true
        }
    }
}

/** A flight in progress: the stop it set out from, and whether the head has landed. */
data class MeteorFlight(val from: Int, val landed: Boolean = false)

/**
 * When the meteor flies. Port of the desktop's `useMeteor`: a meteor starts
 * when Ultra becomes lit — and only then. Drawn lit already (the slider
 * opened on Ultra) is not an arrival, nor is a redraw that finds it there.
 *
 * Decided while composing, not in an effect after it ([observe] is called
 * with every frame's values): an effect let the frame that lit Ultra draw
 * without its flight — the smoulder starting, the thumb sliding to Ultra —
 * before the flight's own frame undid them.
 */
data class MeteorTrigger(
    /** What the last frame saw: whether Ultra was lit, and the stop the thumb was drawn at. */
    val lit: Boolean,
    val shown: Int,
    val flight: MeteorFlight? = null,
    /** Counts flights, and seeds each one. */
    val key: Int = 0,
    /** Reduced motion: the lit thumb glows up instead of a meteor. */
    val glowIn: Boolean = false,
) {
    /** True with no flight: nothing is in the air to wait for. */
    val landed: Boolean get() = flight?.landed ?: true

    /**
     * The trigger for a frame that draws [lit] with the thumb at [shown],
     * out of [count] stops. The flight sets out from where the eye last saw
     * the thumb; lit with the thumb already on Ultra (a pause lifted), it
     * flies in from the stop before rather than not at all.
     */
    fun observe(lit: Boolean, shown: Int, count: Int, reduced: Boolean): MeteorTrigger {
        if (this.lit == lit && this.shown == shown) return this
        var next = copy(lit = lit, shown = shown)
        if (lit && !this.lit) {
            next = if (reduced) {
                next.copy(glowIn = true)
            } else {
                val ultra = count - 1
                val from = if (this.shown >= ultra) ultra - 1 else this.shown
                next.copy(flight = MeteorFlight(from = maxOf(0, from)), key = key + 1)
            }
        } else if (!lit) {
            next = next.copy(flight = null, glowIn = false)
        }
        return next
    }

    /** The head has reached the stop: the thumb and the lit fill take over. */
    fun land(): MeteorTrigger = if (flight != null) copy(flight = flight.copy(landed = true)) else this

    /** The last spark is out. */
    fun done(): MeteorTrigger = copy(flight = null)
}
