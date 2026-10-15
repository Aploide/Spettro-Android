package to.eyed.spettro.mobile.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A press on the bar, and where it has been dragged. [at] is the stop under
 * the finger — the one a release selects, and what the words name. [rest] is
 * where the thumb is drawn meanwhile: the same, except over Ultra while Ultra
 * isn't on. Ultra is reached by the meteor, on release, from where the thumb
 * stands.
 */
data class SliderPress(
    val at: Int,
    val rest: Int,
    /** The finger has crossed into another stop: from then the thumb is under it, with no slide behind. */
    val moved: Boolean,
)

/** The keys the slider answers, whatever the keyboard calls them. */
enum class SliderKey {
    /** Right or up. */
    Forward,

    /** Left or down. */
    Back,
    Home,
    End,
}

/**
 * What the thinking slider does with a press, a drag, a key or TalkBack, and
 * how long its preview stands in for the options. Port of the moves in the
 * desktop's ThinkingSlider.tsx (`select`, the press handlers, the preview's
 * effect), out of the composable so they can be tested without a screen.
 *
 * A move previews at once — the thumb goes where it was put — and is sent
 * through [commit] (the chat's ThinkingCommitter). The preview gives way once
 * the options say what it says, and only once the move's calls are all
 * through: a burst that comes back to where it began matches the options
 * before its first call's update has landed, and letting go then would show
 * that update — High, the meteor relaunching — on its way back. If the
 * options never come round (a refusal rolls them back), it lets go
 * [Thinking.SETTLE_MS] after the calls, where the CLI is.
 *
 * [pending] and [press] are snapshot state, read while composing. Everything
 * runs on the main thread; [scope] is the slider's.
 */
class SliderMoves(
    private val scope: CoroutineScope,
    /** Sends a move; completes true for the move the queue drains on. */
    private val commit: (ThinkingStop) -> Deferred<Boolean>,
    /** Told the stop a move previews in the same frame, and null once it lets go. */
    private val onPreview: (ThinkingStop?) -> Unit = {},
    base: ThinkingState,
    disabled: Boolean = false,
) {
    /** The move standing in for the options, until they catch up. */
    var pending: ThinkingStop? by mutableStateOf(null)
        private set

    /** The press in progress, if any. */
    var press: SliderPress? by mutableStateOf(null)
        private set

    /** The pending move's calls have all returned: the preview waits for the options. */
    var sent: Boolean = false
        private set

    private var base = base
    private var disabled = disabled

    /** A held key's move, waiting for the repeats to stop. */
    private class Held(val job: Job, val send: () -> Unit)

    private var held: Held? = null
    private var settle: Job? = null
    private var settledOn: Triple<ThinkingStop?, Boolean, Boolean>? = null

    /** The slider as drawn over [base]: the options, or the move previewing over them. */
    fun state(base: ThinkingState = this.base): ThinkingState =
        pending?.let { Thinking.previewState(base, it) } ?: base

    /** The options as they now are, and whether the model can think at all. Called after each frame. */
    fun update(base: ThinkingState, disabled: Boolean) {
        this.base = base
        this.disabled = disabled
        reconsider()
    }

    /**
     * Moves to stop [i]. A [held] key's move is previewed at once but sent
     * only once its repeats stop ([Thinking.HELD_KEY_MS]) or the key comes
     * up: a key held down repeats some 30 times a second, and the CLI hears
     * where it stops rather than every stop on the way. Returns whether it
     * moved anything.
     */
    fun select(i: Int, held: Boolean = false): Boolean {
        val now = state()
        if (disabled || i < 0 || i > now.stops.lastIndex || i == now.index) return false
        val stop = now.stops[i]
        preview(stop)
        sent = false
        val send = {
            this.held = null
            val result = commit(stop)
            scope.launch {
                // Only the last move in a burst lets the preview go, so the
                // thumb never steps back through a stale value between two.
                if (result.await() && pending == stop) {
                    sent = true
                    reconsider()
                }
            }
            Unit
        }
        this.held?.job?.cancel()
        this.held = if (held) Held(scope.launch { delay(Thinking.HELD_KEY_MS); send() }, send) else null
        if (!held) send()
        reconsider()
        return true
    }

    /** A key: returns whether it moved the slider (a key it answers is taken either way). */
    fun key(key: SliderKey, repeat: Boolean): Boolean {
        if (disabled) return false
        val now = state()
        val from = now.index
        val last = now.stops.lastIndex
        val next = when (key) {
            SliderKey.Forward -> minOf(last, from + 1)
            // From Off, or an unset level, there is nothing further left.
            SliderKey.Back -> if (from < 0) null else maxOf(0, from - 1)
            SliderKey.Home -> 0
            SliderKey.End -> last
        } ?: return false
        return select(next, held = repeat)
    }

    /** A key came up: a held move goes now. */
    fun keyUp() {
        held?.let {
            it.job.cancel()
            it.send()
        }
    }

    /** TalkBack's set-progress, against the value the slider reports (see [Thinking.a11yTarget]). */
    fun setProgress(target: Float): Boolean {
        val now = state()
        val i = Thinking.a11yTarget(now.index, target, now.stops.lastIndex) ?: return false
        select(i)
        return true
    }

    /**
     * A finger down over stop [i] (null off the rail: where the thumb is).
     * Returns whether it landed on another stop than the thumb's.
     */
    fun pressAt(i: Int?): Boolean {
        if (disabled) return false
        val now = state()
        val at = i ?: now.index
        press = place(now, at, rest = now.index, moved = false)
        return at != now.index
    }

    /** The finger is over stop [i]. Returns whether it crossed into another stop. */
    fun dragTo(i: Int?): Boolean {
        val p = press ?: return false
        if (i == null || i == p.at) return false
        press = place(state(), i, p.rest, moved = true)
        return true
    }

    /** The finger came up: the stop under it is selected. */
    fun release() {
        val p = press ?: return
        press = null
        select(p.at)
    }

    /** The press was taken away (the system claimed it): nothing is selected. */
    fun cancelPress() {
        press = null
    }

    /** The slider is going away: a held key's move still goes, and the preview is let go. */
    fun dispose() {
        keyUp()
        settle?.cancel()
        onPreview(null)
    }

    /** Over an Ultra that isn't on, the thumb stays where it last stood. */
    private fun place(now: ThinkingState, i: Int, rest: Int, moved: Boolean) =
        SliderPress(i, if (i == now.ultraIndex && now.index != now.ultraIndex) rest else i, moved)

    private fun preview(stop: ThinkingStop?) {
        pending = stop
        // The chip's words change in the same frame as the slider's.
        onPreview(stop)
    }

    /** Lets the preview go, now or after the settle, once it has been sent. */
    private fun reconsider() {
        val p = pending
        val caughtUp = Thinking.caughtUp(base, p)
        val key = Triple(p, sent, caughtUp)
        if (key == settledOn) return
        settledOn = key
        settle?.cancel()
        settle = null
        if (p == null || !sent) return
        if (caughtUp) {
            letGo()
        } else {
            settle = scope.launch {
                delay(Thinking.SETTLE_MS)
                letGo()
            }
        }
    }

    private fun letGo() {
        preview(null)
        sent = false
        reconsider()
    }
}
