package to.eyed.spettro.mobile.ui.components.thinking

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import to.eyed.spettro.mobile.model.Meteor
import to.eyed.spettro.mobile.model.Meteor.clamp01
import to.eyed.spettro.mobile.model.MeteorPalette
import to.eyed.spettro.mobile.model.MeteorRun
import to.eyed.spettro.mobile.model.Rgb
import to.eyed.spettro.mobile.ui.theme.SpettroColors

// The meteor's drawing: drawMeteor in the desktop's meteor.ts, line for line,
// from a canvas 2D context to a DrawScope. The scope is in the desktop's CSS
// px — the slider scales its canvas by the density first, as the desktop
// sets its transform to the device pixel ratio — and the slider draws it on
// a layer of its own, so 'lighter' (BlendMode.Plus) adds only among the
// meteor's own strokes, as on the desktop's separate canvas.

/** Ultra's fire, read from the theme's --ultra-* tokens. Dark schemes take it as added light. */
fun SpettroColors.ultraPalette(): MeteorPalette = MeteorPalette(
    ember = ultraEmber.toRgb(),
    flame = ultraFlame.toRgb(),
    spark = ultraSpark.toRgb(),
    core = ultraCore.toRgb(),
    additive = isDark,
)

private fun Color.toRgb() = Rgb(red * 255.0, green * 255.0, blue * 255.0)

/** The desktop's `rgba()`: channels rounded, alpha clamped. */
internal fun rgba(c: Rgb, alpha: Double): Color = Color(
    red = c.r.roundToInt().coerceIn(0, 255),
    green = c.g.roundToInt().coerceIn(0, 255),
    blue = c.b.roundToInt().coerceIn(0, 255),
    alpha = (clamp01(alpha) * 255).roundToInt(),
)

private fun Double.f() = toFloat()

private fun stops(vararg pairs: Pair<Double, Color>): Array<Pair<Float, Color>> =
    Array(pairs.size) { pairs[it].first.toFloat() to pairs[it].second }

/** Draws the run at [progress] (0…1). The caller's scope is in CSS px and starts empty. */
fun DrawScope.drawMeteor(run: MeteorRun, progress: Double, palette: MeteorPalette) {
    val t = clamp01(progress) * run.total
    val (x, v) = Meteor.head(run, t)
    val dir = if (run.toX >= run.fromX) 1.0 else -1.0
    // The rail's end it set out from: a tail is never drawn far past it, as
    // if the thumb had come from off the slider.
    val back = abs(x - (if (dir > 0) run.railLeft else run.railRight)) + 8
    val landed = t >= run.flight
    val since = t - run.flight
    // After impact the head cools: everything it draws fades out over the
    // thumb, which fades in underneath.
    val cool = if (landed) clamp01(since / (run.total - run.flight)) else 0.0
    val heat = 1 - cool.pow(0.8)
    val shimmer = Meteor.flicker(t)
    val length = min(back, Meteor.tailLength(run, t))
    val add = palette.additive
    val lighter = if (add) BlendMode.Plus else BlendMode.SrcOver
    val speed = min(1.0, abs(v) / (run.track * 2.6))
    val h = run.height
    // Sizes across the bar, as a share of the bar they were tuned in.
    val k = h / Meteor.TUNED_HEIGHT
    val y = run.y

    // The light the head throws on the bar ahead of it and behind; at impact,
    // the whole end of the bar flares for an instant.
    kotlin.run {
        val flare = if (landed) (1 - clamp01(since / 0.3)).pow(2) else 0.0
        val r = 70 + 90 * flare
        // Flattened to a little more than the bar, so it reads as the bar lit
        // from inside rather than a glow cut off by its edges.
        val squash = (h * 1.1) / r
        val a = (if (add) 0.2 else 0.1) * heat * shimmer + (if (add) 0.3 else 0.14) * flare
        val glow = Brush.radialGradient(
            *stops(
                0.0 to rgba(if (add) palette.flame else palette.ember, a),
                0.4 to rgba(palette.ember, a * 0.35),
                1.0 to rgba(palette.ember, 0.0),
            ),
            center = Offset.Zero,
            radius = r.f(),
        )
        withTransform({
            translate(x.f(), y.f())
            scale(1f, squash.f(), pivot = Offset.Zero)
        }) {
            drawRect(glow, topLeft = Offset(-r.f(), -r.f()), size = Size(2 * r.f(), 2 * r.f()), blendMode = lighter)
        }
    }

    // The streak as fill: the slider's bar, burnt in from where its fill
    // stood to the head, white-hot at the head and cooling back to ember.
    // Painted, not added, so it is the bar itself rather than light on it.
    // Set out from the first stop (Off, or Low when the CLI lists no Off), it
    // burns the end cap too; from further along, the
    // tail that reaches back over the fill fades in from nothing, so the
    // accent turns to fire rather than being cut off by it. After impact the
    // real fill is under it and it fades away.
    val barAlpha = if (landed) 1 - clamp01(since / 0.22) else 1.0
    val fromLow = abs(run.fillX - (if (dir > 0) run.railLeft else run.railRight)) < 1
    val barEnd = if (dir > 0) run.railLeft - h / 2 else run.railRight + h / 2
    val tailEnd = x - dir * length
    // The burn always reaches a little way back over the accent fill and
    // fades in there, so the accent warms into fire at no hard edge.
    val blend = max(2 * h, 28.0)
    val barFrom = when {
        fromLow -> barEnd
        dir > 0 -> max(barEnd, min(run.fillX - blend, tailEnd))
        else -> min(barEnd, max(run.fillX + blend, tailEnd))
    }
    if (barAlpha > 0 && abs(x - barFrom) > 0.5) {
        val len = abs(x - barFrom)
        // Distance back from the head to where the streak is over the fill,
        // and how far it takes to come in there. Landed, the real fill is
        // under all of it: its cooled tail fades into that, with no seam.
        val ahead = if (dir > 0) x - run.fillX else run.fillX - x
        val over = if (landed) 0.0 else if (fromLow) len else max(0.0, ahead)
        val fadeIn = if (landed) len * 0.7 else min(len - over, max(blend, length * 0.5))
        val hot = if (add) palette.core else palette.spark
        val ramp = listOf(
            0.0 to hot,
            length * 0.12 to palette.spark,
            length * 0.45 to palette.flame,
            max(length, 1.0) to palette.ember,
        )
        fun colorAt(d: Double): Rgb {
            for (i in 1 until ramp.size) {
                val (d1, c1) = ramp[i]
                val (d0, c0) = ramp[i - 1]
                if (d <= d1) return Meteor.mix(c0, c1, if (d1 > d0) (d - d0) / (d1 - d0) else 1.0)
            }
            return palette.ember
        }
        fun alphaAt(d: Double): Double =
            if (d <= over || fadeIn <= 0) barAlpha else barAlpha * clamp01((len - d) / fadeIn)
        val marks = (ramp.map { it.first } + listOf(over, len - fadeIn, len))
            .filter { it in 0.0..len }
            .sorted()
        val bar = Brush.linearGradient(
            *Array(marks.size) { i -> (marks[i] / len).f() to rgba(colorAt(marks[i]), alphaAt(marks[i])) },
            start = Offset(x.f(), y.f()),
            end = Offset(barFrom.f(), y.f()),
        )
        // Round at the head, as the bar's own ends are: a square front shows
        // as a hard white edge across the bar where the corona is thinnest.
        val r = h / 2
        val path = Path().apply {
            moveTo(barFrom.f(), (y - r).f())
            lineTo(x.f(), (y - r).f())
            arcTo(
                Rect(Offset(x.f(), y.f()), r.f()),
                startAngleDegrees = -90f,
                sweepAngleDegrees = if (dir > 0) 180f else -180f,
                forceMoveTo = false,
            )
            lineTo(barFrom.f(), (y + r).f())
            close()
        }
        drawPath(path, bar)
    }

    // The plasma over it: a tapered streak, wide and soft, then narrow and hot.
    if (length > 1) {
        if (!add) {
            // On paper a halo of dark orange, so the fire has a ground to burn on.
            plume(
                x, y, dir, length * 1.05, 14 * k, BlendMode.SrcOver,
                0.0 to rgba(palette.ember, 0.14 * heat),
                0.5 to rgba(palette.ember, 0.05 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
        }
        if (add) {
            plume(
                x, y, dir, length * 1.15, 13 * k * shimmer, lighter,
                0.0 to rgba(palette.flame, 0.5 * heat),
                0.35 to rgba(palette.ember, 0.22 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
            plume(
                x, y, dir, length, 6.5 * k, lighter,
                0.0 to rgba(palette.core, heat),
                0.14 to rgba(palette.spark, 0.95 * heat),
                0.45 to rgba(palette.flame, 0.6 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
        } else {
            plume(
                x, y, dir, length * 1.15, 13 * k * shimmer, lighter,
                0.0 to rgba(palette.flame, 0.3 * heat),
                0.4 to rgba(palette.flame, 0.1 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
            plume(
                x, y, dir, length, 6.5 * k, lighter,
                0.0 to rgba(palette.spark, heat),
                0.3 to rgba(palette.spark, 0.9 * heat),
                0.6 to rgba(palette.flame, 0.7 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
        }
    }

    // Sparks, as short streaks along their motion: a dot reads as dust, a
    // streak as something hot and fast.
    for (s in run.sparks) {
        val age = t - s.born
        if (age < 0 || age > s.life) continue
        val left = 1 - age / s.life
        // Flicker, with the odd near-dropout so the wake twinkles rather than
        // pulses in step.
        var twinkle = 0.62 + 0.38 * sin(t * s.freq + s.phase)
        if (sin(t * s.freq * 1.7 + s.phase * 3) > 0.88) twinkle *= 0.2
        val now = Meteor.sparkAt(run, s, age)
        // Toward the bar's edge a spark thins out rather than being cut off
        // by it: the clip is a safety net, not something you should ever see.
        val edge = clamp01((h / 2 - abs(now.y - y)) / (h * 0.22))
        val alpha = min(1.0, 1.25 * left.pow(0.7) * twinkle) * edge
        if (alpha <= 0.02) continue
        val before = Meteor.sparkAt(run, s, max(0.0, age - 0.026))
        // Cooling: white → amber → red, and then out.
        var color = Meteor.heatColor(palette, s.heat * (0.25 + 0.75 * left))
        // Painted, a spark lands on the streak or the lit fill, and the light
        // scheme's fire is a deep orange: a step toward the white-hot core
        // reads on it where a step toward ember would vanish.
        if (!add) color = Meteor.mix(color, palette.core, 0.2 + 0.3 * left)
        val width = s.size * (0.6 + 0.4 * left) * (if (add) 1.0 else 1.15)
        if (add && left > 0.5) {
            // Light adds up: a faint disc under a hot spark reads as its glow.
            drawCircle(
                rgba(color, alpha * 0.22 * (left - 0.5) * 2),
                radius = (width * 1.5).f(),
                center = Offset(now.x.f(), now.y.f()),
                blendMode = lighter,
            )
        }
        // A stroke much fatter than it is long rasterises as a ring, so a
        // spark barely moving is drawn as a dot.
        val dx = now.x - before.x
        val dy = now.y - before.y
        if (dx * dx + dy * dy > width * width) {
            drawLine(
                rgba(color, alpha),
                start = Offset(before.x.f(), before.y.f()),
                end = Offset(now.x.f(), now.y.f()),
                strokeWidth = width.f(),
                cap = StrokeCap.Round,
                blendMode = lighter,
            )
        } else {
            drawCircle(
                rgba(color, alpha),
                radius = (width / 2).f(),
                center = Offset(now.x.f(), now.y.f()),
                blendMode = lighter,
            )
        }
    }

    if (landed && since < 0.32) {
        // The shockwave: a pulse of heat thrown back along the bar from the
        // stop, a soft band the bar's height with a hot leading edge,
        // running back and fading as it goes.
        val w = since / 0.32
        val r = 10 + 44 * Meteor.easeOutCubic(w)
        val thick = 14 * (1 - w) + 6
        // In over the first few milliseconds, so it leaves the head rather
        // than starting round it.
        val fade = clamp01(w / 0.1) * (1 - w).pow(1.4)
        val front = x - dir * r
        val lead = thick * 0.6
        val edge = if (add) palette.spark else palette.flame
        val crest = thick / (thick + lead)
        val wave = Brush.linearGradient(
            *stops(
                0.0 to rgba(palette.ember, 0.0),
                crest * 0.6 to rgba(if (add) palette.flame else palette.ember, 0.25 * fade),
                crest to rgba(edge, 0.6 * fade),
                1.0 to rgba(edge, 0.0),
            ),
            start = Offset((front + dir * thick).f(), y.f()),
            end = Offset((front - dir * lead).f(), y.f()),
        )
        drawRect(
            wave,
            topLeft = Offset(min(front - dir * lead, front + dir * thick).f(), (y - h / 2).f()),
            size = Size((thick + lead).f(), h.f()),
            blendMode = lighter,
        )
    }

    // The head: a corona, stretched along the way it is going, around a
    // white-hot core; brightest and biggest the instant it lands.
    val flash = if (landed) 1 + 0.3 * (1 - clamp01(since / 0.14)).pow(2) else 1.0
    // Cooling, it shrinks to the size of the thumb that takes its place.
    val size = (1 - 0.3 * cool) * shimmer * flash
    val stretch = 1 + 0.9 * speed
    withTransform({
        translate(x.f(), y.f())
        scale(stretch.f(), 1f, pivot = Offset.Zero)
        translate((dir * -2 * speed).f(), 0f)
    }) {
        if (!add) {
            val haloR = 20 * k * size
            drawCircle(
                Brush.radialGradient(
                    *stops(
                        0.0 to rgba(palette.ember, 0.4 * heat),
                        0.5 to rgba(palette.ember, 0.16 * heat),
                        1.0 to rgba(palette.ember, 0.0),
                    ),
                    center = Offset.Zero,
                    radius = haloR.f(),
                ),
                radius = haloR.f(),
                center = Offset.Zero,
            )
        }
        // Painted on paper the corona is solid colour, so it is drawn
        // smaller. Wider than the bar either way: clipped by it, it is the
        // bar glowing white-hot around the head.
        val reach = (if (add) 17.0 else 13.5) * k * size
        val corona = if (add) {
            stops(
                0.0 to rgba(palette.core, heat),
                0.2 to rgba(palette.core, 0.95 * heat),
                0.36 to rgba(palette.spark, 0.85 * heat),
                0.6 to rgba(palette.flame, 0.45 * heat),
                0.8 to rgba(palette.ember, 0.18 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
        } else {
            // Painted, the heat has to come from saturation: gold straight
            // off the core, through orange, into a thin red rim.
            stops(
                0.0 to rgba(palette.spark, heat),
                0.3 to rgba(palette.spark, heat),
                0.5 to rgba(palette.flame, 0.85 * heat),
                0.72 to rgba(palette.ember, 0.3 * heat),
                1.0 to rgba(palette.ember, 0.0),
            )
        }
        drawCircle(
            Brush.radialGradient(*corona, center = Offset.Zero, radius = reach.f()),
            radius = reach.f(),
            center = Offset.Zero,
            blendMode = lighter,
        )
        drawCircle(
            rgba(palette.core, heat),
            radius = ((if (add) 4.6 else 3.4) * k * size).f(),
            center = Offset.Zero,
        )
    }
}

/**
 * A tapered flame from the head back along the path: round at the front,
 * narrowing to a point [length] behind.
 */
private fun DrawScope.plume(
    x: Double,
    y: Double,
    dir: Double,
    length: Double,
    width: Double,
    blendMode: BlendMode,
    vararg colorStops: Pair<Double, Color>,
) {
    if (length < 1) return
    val end = x - dir * length
    val fill = Brush.linearGradient(
        *stops(*colorStops),
        start = Offset(x.f(), y.f()),
        end = Offset(end.f(), y.f()),
    )
    val half = width / 2
    val path = Path().apply {
        moveTo(x.f(), (y - half).f())
        cubicTo(
            (x - dir * length * 0.35).f(), (y - half * 0.8).f(),
            (x - dir * length * 0.7).f(), (y - half * 0.25).f(),
            end.f(), y.f(),
        )
        cubicTo(
            (x - dir * length * 0.7).f(), (y + half * 0.25).f(),
            (x - dir * length * 0.35).f(), (y + half * 0.8).f(),
            x.f(), (y + half).f(),
        )
        // Round the front: bottom → front → top, the way the head is moving.
        arcTo(
            Rect(Offset(x.f(), y.f()), half.f()),
            startAngleDegrees = 90f,
            sweepAngleDegrees = if (dir > 0) -180f else 180f,
            forceMoveTo = false,
        )
        close()
    }
    drawPath(path, fill, blendMode = blendMode)
}
