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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * A small status indicator dot — connection state, tool completion, busy
 * markers. Subtly pulses its opacity while [pulsing] is true.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    size: Dp = 8.dp,
) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "statusDot")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "statusDotPulse",
        )
        pulse
    } else {
        1f
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

@Preview(name = "Status dots", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun StatusDotPreview() {
    SpettroTheme(darkTheme = true) {
        val colors = LocalSpettroColors.current
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(color = colors.diffAdded)
            StatusDot(color = colors.accent, pulsing = true)
            StatusDot(color = colors.diffRemoved)
            StatusDot(color = colors.agentAccent, pulsing = true)
        }
    }
}
