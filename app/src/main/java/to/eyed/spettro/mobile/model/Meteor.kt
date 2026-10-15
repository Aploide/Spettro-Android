package to.eyed.spettro.mobile.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The meteor the thinking slider's thumb becomes when it arrives at Ultra.
// Port of the desktop's views/chat/meteor.ts: the plan and the physics here,
// the drawing in ui/components/thinking/MeteorPainter.kt.
//
// The thumb lifts off as a white-hot head and accelerates along the track,
// dragging a tapered plasma streak that *is* the slider's fill: the fill grows
// out from behind the head and cools along its length, from white through
// gold and orange into ember red, the colour the lit fill starts from, so
// there is never a gap between the fire and the bar it leaves. Sparks peel off
// the head, scatter up and down the bar, flicker and cool to ember red. It
// hits the Ultra stop at full speed: a flash, a shockwave along the bar, a
// burst of embers, a few that linger — and then it cools into the lit thumb.
//
// Every frame is a pure function of (progress, seed): each spark is born from
// a seeded random stream and its position at any moment is worked out in
// closed form rather than stepped. So a dropped frame costs nothing, and the
// screenshot harness can photograph any instant of the run and get the same
// picture every time. Units are the desktop's CSS px, which the slider draws
// as dp.

/** A colour as three 0…255 channels, so the fire can be mixed like the desktop's. */
data class Rgb(val r: Double, val g: Double, val b: Double)

data class MeteorPalette(
    val ember: Rgb,
    val flame: Rgb,
    val spark: Rgb,
    val core: Rgb,
    /**
     * Dark backgrounds take the fire as added light; light ones would wash it
     * out to nothing, so there it is painted on like ink, a shade deeper,
     * with a dark-orange halo to stand out from the paper.
     */
    val additive: Boolean,
)

data class Spark(
    /** Birth, in seconds from the start of the run. */
    val born: Double,
    val life: Double,
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    /** Velocity lost per second (exponential drag). */
    val drag: Double,
    val size: Double,
    /** 1 is white-hot; sparks cool as they age. */
    val heat: Double,
    val phase: Double,
    val freq: Double,
)

data class MeteorRun(
    /** Where the head sets out from, and the stop it lands on. */
    val fromX: Double,
    val toX: Double,
    /**
     * Where the slider's own fill stops while the head is in flight: the
     * streak is drawn from here, so fill and fire are one bar.
     */
    val fillX: Double,
    val y: Double,
    /**
     * The bar's height: the streak fills it, the head and plume are sized to
     * it and sparks fade out toward its edges rather than being cut off.
     */
    val height: Double,
    /** Pull on the sparks, in px/s², scaled to the bar's height. */
    val gravity: Double,
    /** The rail's ends, so the burnt-in bar never runs off them, and its length. */
    val railLeft: Double,
    val railRight: Double,
    val track: Double,
    /** Seconds in flight, and for the whole run (flight, impact, cooling). */
    val flight: Double,
    val total: Double,
    /** The share of its final speed the head sets out with. */
    val launch: Double,
    val sparks: List<Spark>,
)

data class MeteorTiming(
    /** Where on the rail (0…1) the head sets out from. */
    val startFrac: Double,
    /** Milliseconds until the head lands, and until the last ember is out. */
    val landsMs: Double,
    val totalMs: Double,
    /** landsMs / totalMs: the run's progress at the moment of impact. */
    val lands: Double,
)

data class MeteorGeometry(
    /** The rail's start and length, and its centre line, in the bar's coordinates. */
    val railLeft: Double,
    val railWidth: Double,
    val y: Double,
    /** The bar's height: the sparks' room to scatter. */
    val height: Double,
    /** The stop the thumb was on (its fill ends there) and the one it flies to, as shares of the rail. */
    val fromFrac: Double,
    val toFrac: Double,
)

/** The head's position along the bar and its velocity (px/s). */
data class HeadAt(val x: Double, val v: Double)

data class Point(val x: Double, val y: Double)

object Meteor {
    /** After impact: the flash, the embers and the cooling into the thumb. */
    const val AFTER = 0.32

    /** The bar height the sizes below were tuned in. */
    const val TUNED_HEIGHT = 16.0

    /**
     * Pull on the sparks, in px/s² in a bar that height: just enough that
     * they arc as they die without falling out of it.
     */
    private const val GRAVITY = 30.0

    /**
     * How long the plasma behind the head stays white-hot to amber, in
     * seconds of travel: the tail's length is the head's speed times this.
     */
    private const val PERSIST = 0.24

    /**
     * How a run from [fromFrac] to [toFrac] (shares of the rail) is timed.
     * Always from the stop the thumb was on: the thumb is hidden the moment
     * the head appears, so a head set out from anywhere else is the thumb
     * jumping. A short run streaks anyway: it sets out fast (`launch`) and
     * trails a tail of its own from the first frame.
     */
    fun timing(fromFrac: Double, toFrac: Double): MeteorTiming {
        val startFrac = clamp01(fromFrac)
        val span = abs(toFrac - startFrac)
        val flight = 0.16 + 0.26 * span
        val total = flight + AFTER
        return MeteorTiming(
            startFrac = startFrac,
            landsMs = flight * 1000,
            totalMs = total * 1000,
            lands = flight / total,
        )
    }

    /**
     * mulberry32: a tiny seeded generator, so a run is reproducible. Int
     * arithmetic wraps exactly as `Math.imul` and `>>> 0` do.
     */
    fun random(seed: Int): () -> Double {
        var a = seed
        return {
            a += 0x6d2b79f5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    /** Roughly normal, mean 0 and spread ~1: most sparks near the line, a few flung wide. */
    private fun spread(rand: () -> Double): Double = (rand() + rand() + rand() - 1.5) * 2

    fun clamp01(v: Double): Double = min(1.0, max(0.0, v))

    fun easeOutCubic(u: Double): Double = 1 - (1 - u).pow(3)

    /**
     * Where the head is [t] seconds in, and its velocity. It sets out already
     * moving and accelerates all the way into the stop.
     */
    fun head(run: MeteorRun, t: Double): HeadAt {
        val u = clamp01(t / run.flight)
        val d = run.toX - run.fromX
        val a = run.launch
        val p = a * u + (1 - a) * u * u
        val v = if (u >= 1) 0.0 else ((a + 2 * (1 - a) * u) * d) / run.flight
        return HeadAt(run.fromX + d * p, v)
    }

    /**
     * The tail's length at [t]: the head's speed times how long the plasma
     * stays hot, grown in over the first frames, and after impact swallowed
     * by the stop as the rest of the streak piles into it.
     */
    fun tailLength(run: MeteorRun, t: Double): Double {
        val cap = run.track * 0.48
        if (t < run.flight) {
            val grow = clamp01(t / 0.07)
            // Never a stub: even setting out it trails a streak of its own.
            val floor = run.track * 0.14
            return min(cap, max(floor, abs(head(run, t).v) * PERSIST)) * grow
        }
        val atImpact = min(cap, abs(head(run, run.flight - 1e-6).v) * PERSIST)
        val k = clamp01((t - run.flight) / 0.13)
        return atImpact * (1 - k).pow(2)
    }

    /** Plans a run: the head's path and every spark it will shed. */
    fun plan(g: MeteorGeometry, seed: Int): MeteorRun {
        val rand = random(seed)
        val timing = timing(g.fromFrac, g.toFrac)
        fun x(f: Double) = g.railLeft + f * g.railWidth
        val fromX = x(timing.startFrac)
        val toX = x(g.toFrac)
        val dir = if (toX >= fromX) 1.0 else -1.0
        val span = abs(toX - fromX) / max(1.0, g.railWidth)
        var run = MeteorRun(
            fromX = fromX,
            toX = toX,
            fillX = x(g.fromFrac),
            y = g.y,
            height = g.height,
            gravity = GRAVITY * (g.height / TUNED_HEIGHT),
            railLeft = g.railLeft,
            railRight = g.railLeft + g.railWidth,
            track = g.railWidth,
            flight = timing.landsMs / 1000,
            total = timing.totalMs / 1000,
            // A long run has room to build up speed; a short one comes in streaking.
            launch = 0.25 + 0.45 * (1 - min(1.0, span)),
            sparks = emptyList(),
        )
        // Vertical room, as a share of the bar the numbers were tuned in.
        val room = min(1.5, g.height / TUNED_HEIGHT)
        // The latest a spark may still be alight: the run's end.
        fun until(born: Double, life: Double) = min(life, run.total - born)
        val sparks = ArrayList<Spark>(120)

        // The wake: shed all along the flight, more of them the faster the
        // head goes, carrying a little of its speed and thrown up and down
        // the bar. The rand() calls keep the desktop's order exactly.
        val wake = (36 + 44 * min(1.0, span)).roundToInt()
        for (i in 0 until wake) {
            val born = run.flight * sqrt((i + rand()) / wake)
            val at = head(run, born)
            val life = 0.2 + rand().pow(1.4) * 0.5
            val life2 = until(born, life)
            val sx = at.x - dir * rand() * 5
            val sy = run.y + (rand() - 0.5) * 4 * room
            val vx = at.v * (0.08 + rand() * 0.32) - dir * rand() * 70
            val vy = spread(rand) * 20 * room
            val drag = 2.4 + rand() * 3
            val size = 0.9 + rand().pow(2) * 1.5
            val heat = 0.72 + rand() * 0.28
            val phase = rand() * PI * 2
            val freq = 30 + rand() * 55
            sparks += Spark(born, life2, sx, sy, vx, vy, drag, size, heat, phase, freq)
        }

        // The impact: a splash flung back along the bar — the stop sits in
        // the bar's end cap, so anything thrown forward would be clipped the
        // instant it left, and anything thrown up or down the moment after.
        val burst = 30
        for (i in 0 until burst) {
            val angle = PI + (i.toDouble() / burst - 0.5) * PI * 1.7 + (rand() - 0.5) * 0.35
            val speed = 90 + rand() * 250
            val born = run.flight + rand() * 0.04
            val life = until(born, 0.22 + rand() * 0.36)
            val sy = run.y + (rand() - 0.5) * 3 * room
            val vx = dir * kotlin.math.cos(angle) * speed
            val vy = sin(angle) * speed * 0.17 * room
            val drag = 4.5 + rand() * 4
            val size = 1 + rand() * 1.4
            val heat = 0.88 + rand() * 0.12
            val phase = rand() * PI * 2
            val freq = 40 + rand() * 50
            sparks += Spark(born, life, run.toX, sy, vx, vy, drag, size, heat, phase, freq)
        }

        // Embers: slow, amber, lingering around the stop after everything
        // else is out, and gone before the run ends.
        val embers = 10
        for (i in 0 until embers) {
            val born = run.flight + 0.02 + rand() * 0.08
            val life = until(born, 0.28 + rand() * 0.1)
            val sx = run.toX - dir * rand() * 14
            val sy = run.y + (rand() - 0.5) * 6 * room
            val vx = -dir * (8 + rand() * 40)
            val vy = (rand() - 0.6) * 12 * room
            val drag = 1.5 + rand() * 1.5
            val size = 1.1 + rand() * 0.8
            val heat = 0.45 + rand() * 0.25
            val phase = rand() * PI * 2
            val freq = 12 + rand() * 18
            sparks += Spark(born, life, sx, sy, vx, vy, drag, size, heat, phase, freq)
        }
        run = run.copy(sparks = sparks)
        return run
    }

    fun mix(a: Rgb, b: Rgb, t: Double): Rgb =
        Rgb(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)

    /** A temperature to a colour: ember → flame → spark → white-hot core. */
    fun heatColor(p: MeteorPalette, heat: Double): Rgb {
        val h = clamp01(heat)
        return when {
            h > 2.0 / 3 -> mix(p.spark, p.core, (h - 2.0 / 3) * 3)
            h > 1.0 / 3 -> mix(p.flame, p.spark, (h - 1.0 / 3) * 3)
            else -> mix(p.ember, p.flame, h * 3)
        }
    }

    /** Where a spark is [age] seconds after its birth. */
    fun sparkAt(run: MeteorRun, s: Spark, age: Double): Point {
        val fall = (1 - exp(-s.drag * age)) / s.drag
        return Point(s.x + s.vx * fall, s.y + s.vy * fall + 0.5 * run.gravity * age * age)
    }

    /** A flicker in [0.7, 1.08] that never repeats in step: three detuned sines. */
    fun flicker(t: Double): Double =
        0.89 + 0.07 * sin(t * 71) + 0.05 * sin(t * 43 + 1.3) + 0.04 * sin(t * 127 + 2.1)
}
