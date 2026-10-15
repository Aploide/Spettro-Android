package to.eyed.spettro.mobile.model

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

// The lit Ultra bar at rest. Port of the desktop's views/chat/embers.ts: the
// plan here, the drawing in ui/components/thinking/EmberPainter.kt.
//
// Once the meteor has burnt out, the fill it left keeps smouldering while the
// slider is open: a slow wave of heat runs along it toward the white-hot end,
// and a handful of embers drift the same way, bobbing a little and warming as
// they go. It is the bar's own fire breathing — never brighter than the fill,
// never outside it, slow enough to sit in the corner of the eye while the
// caption below is read.
//
// Like the meteor, a frame is a pure function of (time, seed): each ember
// loops along the bar in closed form, so the harness can freeze any instant
// and a dropped frame costs nothing.

data class Mote(
    /** Pixels per second along the bar. */
    val speed: Double,
    /** Where on its loop it is at t = 0 (0…1). */
    val offset: Double,
    /** How far it bobs off the centre line, and how fast. */
    val amp: Double,
    val bob: Double,
    val phase: Double,
    /** Radius of its bright core. */
    val size: Double,
    /** How fast it twinkles, in radians a second. */
    val freq: Double,
)

data class EmberField(
    /** The fill's ends along the bar, and the bar's centre line and height. */
    val left: Double,
    val right: Double,
    val y: Double,
    val height: Double,
    val motes: List<Mote>,
)

object Embers {
    /** Seconds for a wave of heat to run the length of the bar. */
    const val WAVE = 3.8

    /**
     * Off-screen run-up at each end of an ember's loop, so it fades in and
     * out inside the bar instead of popping at its edge.
     */
    const val PAD = 6.0

    /** The seed the slider plants its smoulder with. */
    const val SEED = 11

    /**
     * Plans the embers for a bar whose fill ends at [fillEnd] (the Ultra
     * stop, from the bar's left end): about one every 40px, never fewer than
     * five.
     */
    fun plan(fillEnd: Double, height: Double, seed: Int = SEED): EmberField {
        val rand = Meteor.random(seed)
        val half = height / 2
        val count = max(5, (fillEnd / 40).roundToInt())
        val motes = List(count) { i ->
            val speed = 12 + rand() * 20
            // Spread evenly round the loop, then jittered: never a clump.
            val offset = (i + rand() * 0.7) / count
            val amp = half * (0.15 + rand() * 0.35)
            val bob = 0.8 + rand() * 1.4
            val phase = rand() * PI * 2
            val size = 0.75 + rand() * 0.7
            val freq = 2 + rand() * 3
            Mote(speed, offset, amp, bob, phase, size, freq)
        }
        return EmberField(left = 0.0, right = fillEnd, y = half, height = height, motes = motes)
    }

    fun frac(v: Double): Double = v - kotlin.math.floor(v)
}
