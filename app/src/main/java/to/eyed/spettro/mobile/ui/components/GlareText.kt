package to.eyed.spettro.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/** Seconds for the highlight to cross the label once (port of GlareText.swift). */
private const val PERIOD_MILLIS = 1800

/**
 * The TUI's glare sweep: a highlight travelling along a label to mark it as
 * live — used on the active orchestrator and on each running sub-agent.
 *
 * A moving highlight band rendered as an animated linear-gradient brush over
 * the glyphs: same travel direction and pace as the terminal's per-character
 * recoloring, but continuous rather than quantized to cells.
 *
 * @param isActive false renders the plain label without the sweep, so callers
 *   can toggle liveness without swapping composables (and losing the layout).
 */
@Composable
fun GlareText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = LocalSpettroColors.current.accent,
    isActive: Boolean = true,
) {
    if (!isActive) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }

    val transition = rememberInfiniteTransition(label = "glare")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glarePhase",
    )
    val highlight = lerp(color, Color.White, 0.85f)

    val brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val bandWidth = size.width * 0.35f
            // Start fully off the leading edge and finish fully off the
            // trailing one, so the sweep never pops mid-label.
            val travel = size.width + bandWidth * 2f
            val originX = -bandWidth + travel * phase
            return LinearGradientShader(
                from = Offset(originX - bandWidth / 2f, 0f),
                to = Offset(originX + bandWidth / 2f, 0f),
                colors = listOf(color, highlight, color),
                tileMode = TileMode.Clamp,
            )
        }
    }
    Text(text, modifier = modifier, style = style.merge(TextStyle(brush = brush)))
}

@Preview(name = "Glare", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun GlareTextPreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlareText(text = "coding · editing AppModel.swift")
            GlareText(text = "explore", color = Color(0xFFF59E0B))
            GlareText(text = "not running", isActive = false)
        }
    }
}
