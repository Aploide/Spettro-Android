package to.eyed.spettro.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val DOT_COUNT = 8

/** One full revolution: 8 braille frames x 50 ms, matching the TUI. */
private const val PERIOD_MILLIS = 400

/**
 * The TUI's busy spinner, natively — a port of `SpettroSpinner.swift`.
 *
 * The terminal cycles the braille frames one step per 50 ms tick, which reads
 * as a dot orbiting a ring once every 400 ms. This draws that literally:
 * eight dots on a circle, brightness falling off behind the leading one, same
 * period and same direction.
 */
@Composable
fun SpettroSpinner(
    size: Dp = 16.dp,
    color: Color = LocalSpettroColors.current.accentText,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "spettroSpinner")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = DOT_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "leadingDot",
    )

    Canvas(modifier = modifier.size(size)) {
        // Quantize to whole steps so the leading dot jumps ring positions the
        // way the braille frames do, rather than blending between them.
        val leading = progress.toInt() % DOT_COUNT
        val dotRadius = max(1.dp.toPx(), size.toPx() * 0.11f)
        val ringRadius = minOf(this.size.width, this.size.height) / 2f - dotRadius
        for (index in 0 until DOT_COUNT) {
            // Distance *behind* the leading dot, so the trail fades the way
            // the braille frames appear to.
            val trail = (leading - index + DOT_COUNT) % DOT_COUNT
            val alpha = max(0.12f, 1f - trail * 0.16f)
            val angle = (index.toDouble() / DOT_COUNT) * 2.0 * PI - PI / 2.0
            drawCircle(
                color = color.copy(alpha = color.alpha * alpha),
                radius = dotRadius,
                center = Offset(
                    x = center.x + (cos(angle) * ringRadius).toFloat(),
                    y = center.y + (sin(angle) * ringRadius).toFloat(),
                ),
            )
        }
    }
}

/**
 * The desktop's shell spinner: a plain ring turning once every 0.8s — a
 * 22% track with a full-strength arc — in text-secondary. For loading
 * screens and buttons, where the transcript's dot ring would be too loud.
 */
@Composable
fun RingSpinner(
    size: Dp = 18.dp,
    color: Color = LocalSpettroColors.current.textSecondary,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ringSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAngle",
    )
    Canvas(modifier = modifier.size(size)) {
        val stroke = max(1.5.dp.toPx(), this.size.minDimension * 0.11f)
        val inset = stroke / 2f
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        val style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        drawArc(color.copy(alpha = color.alpha * 0.22f), 0f, 360f, false, topLeft, arcSize, style = style)
        drawArc(color, angle - 90f, 90f, false, topLeft, arcSize, style = style)
    }
}

@Preview(name = "Spinner sizes", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun SpettroSpinnerPreview() {
    SpettroTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SpettroSpinner(size = 12.dp)
            SpettroSpinner(size = 16.dp)
            SpettroSpinner(size = 24.dp, color = LocalSpettroColors.current.agentAccent)
            SpettroSpinner(size = 40.dp)
            RingSpinner()
        }
    }
}
