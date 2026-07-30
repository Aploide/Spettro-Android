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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The subscription-tier label, styled like the CLI's TUI header: bold
 * uppercase text in the tier color, and for the max plan an animated rainbow
 * cycling through the same palette the TUI uses. Passing null renders a quiet
 * "NO PLAN" so the disconnected state is still visible.
 *
 * Port of `PlanBadge.swift`; takes the plan as a plain string ("free", "lite",
 * "plus", "pro", "max", or anything else the host reports).
 */
@Composable
fun PlanBadge(
    plan: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
) {
    val normalized = plan?.trim()?.lowercase()
    if (normalized == "max") {
        RainbowText(text = "MAX", fontSize = fontSize, modifier = modifier)
        return
    }

    val colors = LocalSpettroColors.current
    // The TUI's pastel tier colors are tuned for a dark background; the light
    // scheme swaps in darker variants of the same hues.
    val tierColor = when (normalized) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        "lite" -> MaterialTheme.colorScheme.onSurface
        "plus" -> if (colors.isDark) Color(0xFF86EFAC) else Color(0xFF16A34A)
        "pro" -> if (colors.isDark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
        else -> Color(0xFF9CA3AF) // free and unknown tiers
    }
    Text(
        text = (normalized ?: "no plan").uppercase(),
        modifier = modifier,
        color = tierColor,
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold),
    )
}

/** Seconds for the rainbow palette to travel one full cycle. */
private const val RAINBOW_PERIOD_MILLIS = 3000

/** The TUI's six-color rainbow (`rainbow[(i+frame)%len]` in renderPlanLabel). */
private val Rainbow = listOf(
    Color(0xFFFF6B6B),
    Color(0xFFFF9E4F),
    Color(0xFFFFD93D),
    Color(0xFF6BCB77),
    Color(0xFF4D96FF),
    Color(0xFFC77DFF),
)

/**
 * Bold text filled with the scrolling rainbow. The palette scrolls rather
 * than hue-rotating so all six colors stay visible at every instant — the
 * gradient repeats seamlessly because the first color is appended to close
 * the cycle, and the shader tiles it.
 */
@Composable
private fun RainbowText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "rainbow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RAINBOW_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainbowPhase",
    )
    val brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            // One palette cycle spans the label's width; sliding by exactly
            // one width lands on the identical repeat, so the loop is
            // seamless.
            val originX = -size.width * phase
            return LinearGradientShader(
                from = Offset(originX, 0f),
                to = Offset(originX + size.width, 0f),
                colors = Rainbow + Rainbow.first(),
                tileMode = TileMode.Repeated,
            )
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, brush = brush),
    )
}

@Preview(name = "Tiers", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun PlanBadgePreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlanBadge(plan = null, fontSize = 16.sp)
            PlanBadge(plan = "free", fontSize = 16.sp)
            PlanBadge(plan = "lite", fontSize = 16.sp)
            PlanBadge(plan = "plus", fontSize = 16.sp)
            PlanBadge(plan = "pro", fontSize = 16.sp)
            PlanBadge(plan = "max", fontSize = 16.sp)
            PlanBadge(plan = "beta", fontSize = 16.sp)
        }
    }
}

@Preview(name = "Tiers light", showBackground = true, backgroundColor = 0xFFF9F9F7)
@Composable
private fun PlanBadgePreviewLight() {
    SpettroTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlanBadge(plan = "plus", fontSize = 16.sp)
            PlanBadge(plan = "pro", fontSize = 16.sp)
            PlanBadge(plan = "max", fontSize = 16.sp)
        }
    }
}
