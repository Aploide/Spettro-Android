package to.eyed.spettro.mobile.ui.components.thinking

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import to.eyed.spettro.mobile.model.EmberField
import to.eyed.spettro.mobile.model.Embers
import to.eyed.spettro.mobile.model.Meteor
import to.eyed.spettro.mobile.model.Meteor.clamp01
import to.eyed.spettro.mobile.model.MeteorPalette

// Lit Ultra's smoulder: drawEmbers in the desktop's embers.ts, line for line.
// Like the meteor's, the scope is in CSS px and the field is clipped by the
// bar it lies in.

/** Draws the field at [t] seconds. The caller's scope is in CSS px and starts empty. */
fun DrawScope.drawEmbers(field: EmberField, t: Double, palette: MeteorPalette) {
    val left = field.left
    val right = field.right
    val length = right - left
    if (length <= 0) return
    val add = palette.additive
    val lighter = if (add) BlendMode.Plus else BlendMode.SrcOver

    // The heat: two soft bright bands, out of step, running toward the stop.
    // Light on the fire, never a stripe of its own: wide and low.
    val bands = listOf(
        doubleArrayOf(Embers.WAVE, 0.0, if (add) 0.2 else 0.26, 0.42),
        doubleArrayOf(Embers.WAVE * 1.37, 0.55, if (add) 0.1 else 0.14, 0.3),
    )
    for ((period, offset, strength, width) in bands.map { it.toList() }) {
        val w = length * width
        val centre = left - w + Embers.frac(t / period + offset) * (length + 2 * w)
        // Hotter toward the stop, as the fill is.
        val at = clamp01((centre - left) / length)
        val c = Meteor.mix(palette.spark, palette.core, at)
        val from = max(left, centre - w)
        val to = min(right, centre + w)
        if (to <= from) continue
        val band = Brush.horizontalGradient(
            0f to rgba(c, 0.0),
            0.5f to rgba(c, strength * (0.6 + 0.4 * at)),
            1f to rgba(c, 0.0),
            startX = (centre - w).toFloat(),
            endX = (centre + w).toFloat(),
        )
        drawRect(
            band,
            topLeft = Offset(from.toFloat(), 0f),
            size = Size((to - from).toFloat(), field.height.toFloat()),
            blendMode = lighter,
        )
    }

    // The embers: tiny motes with a glow, heating as they near the stop.
    val loop = length + 2 * Embers.PAD
    for (m in field.motes) {
        val u = Embers.frac(m.offset + (t * m.speed) / loop)
        val x = left - Embers.PAD + u * loop
        if (x < left - m.size * 3 || x > right + m.size * 3) continue
        val along = clamp01((x - left) / length)
        // In and out along the loop, so none appears or vanishes in mid-bar.
        val life = sin(PI * u).pow(0.7)
        val twinkle = 0.7 + 0.3 * sin(t * m.freq + m.phase)
        val alpha = 0.75 * life * twinkle
        if (alpha <= 0.02) continue
        val my = field.y + m.amp * sin(t * m.bob + m.phase)
        // Brighter than the fill under it wherever it is: gold over the ember
        // end, near-white over the hot end.
        val color = Meteor.mix(Meteor.heatColor(palette, 0.55 + 0.45 * along), palette.core, if (add) 0.35 else 0.6)
        val center = Offset(x.toFloat(), my.toFloat())
        val glowR = (m.size * 3.2).toFloat()
        drawCircle(
            Brush.radialGradient(
                0f to rgba(color, alpha * 0.45),
                1f to rgba(color, 0.0),
                center = center,
                radius = glowR,
            ),
            radius = glowR,
            center = center,
            blendMode = lighter,
        )
        drawCircle(rgba(color, alpha), radius = m.size.toFloat(), center = center, blendMode = lighter)
    }
}
