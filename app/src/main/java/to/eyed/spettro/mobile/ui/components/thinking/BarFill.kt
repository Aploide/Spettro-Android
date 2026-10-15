package to.eyed.spettro.mobile.ui.components.thinking

import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.model.ThinkingStop

/**
 * The slider bar's fill, its stops and its knob as plain numbers, apart
 * from the drawing so they can be tested. The desktop states the same rules
 * as classes and styles on the slider (thinking-slider--off, --unset,
 * thinking-tick--passed, the fill's translateX).
 */
internal object BarFill {

    /**
     * Whether the bar is drawn empty at stop [at]: the Off stop, or a level
     * that is none of the stops (-1). Nothing is filled, not even the end cap.
     */
    fun isEmpty(at: Int, stops: List<ThinkingStop>): Boolean =
        at < 0 || stops.getOrNull(at)?.id == Thinking.OFF

    /**
     * Where the fill ends, from the bar's left edge: the stop at [f] along
     * the [span] between the caps, less the end cap as it retracts ([emptied]
     * runs 0 to 1 toward an empty bar, alongside the knob's slide).
     */
    fun end(cap: Float, span: Float, f: Float, emptied: Float): Float = cap * (1 - emptied) + span * f

    /** Whether stop [i], at [fracI] along the bar, is lit as passed: inside the fill, and never on an empty bar. */
    fun passed(i: Int, shown: Int, fracI: Float, fillFrac: Float, empty: Boolean): Boolean =
        i <= shown && fracI <= fillFrac && !empty

    /** The knob's look at stop [at]: the hollow ring only for an unset level; the Off stop wears the normal knob. */
    fun thumbLook(at: Int, pausedLook: Boolean, litLook: Boolean): ThumbLook = when {
        at < 0 -> ThumbLook.Unset
        pausedLook -> ThumbLook.Paused
        litLook -> ThumbLook.Lit
        else -> ThumbLook.Normal
    }
}

/** Unset: a level that is none of the stops; the Off stop is Normal. */
internal enum class ThumbLook { Normal, Unset, Lit, Paused }
