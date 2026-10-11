package to.eyed.spettro.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.rememberReducedMotion

/**
 * A small status indicator dot — connection state, tool status, unread and
 * busy markers. While [pulsing] it breathes the way the desktop's running
 * tool glyph does: 1.4s, down to 45% opacity at 80% scale. With [halo] it
 * wears a 3dp ring of its own colour at 22% (a tool awaiting approval).
 * With animations off ([rememberReducedMotion]) a pulsing dot holds still
 * at full strength, as the desktop's does under `prefers-reduced-motion`.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    size: Dp = 7.dp,
    halo: Boolean = false,
) {
    var alpha = 1f
    var scale = 1f
    if (pulsing && !rememberReducedMotion()) {
        val transition = rememberInfiniteTransition(label = "statusDot")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "statusDotPulse",
        )
        alpha = 1f - 0.55f * pulse
        scale = 1f - 0.2f * pulse
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(if (halo) size + 6.dp else size)
            .then(if (halo) Modifier.background(color.copy(alpha = 0.22f), CircleShape) else Modifier),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .background(color, CircleShape),
        )
    }
}

@Preview(name = "Status dots", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun StatusDotPreview() {
    SpettroTheme(darkTheme = true) {
        val colors = LocalSpettroColors.current
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(color = colors.success)
            StatusDot(color = colors.accent, pulsing = true)
            StatusDot(color = colors.danger)
            StatusDot(color = colors.agentAccent, pulsing = true)
        }
    }
}
