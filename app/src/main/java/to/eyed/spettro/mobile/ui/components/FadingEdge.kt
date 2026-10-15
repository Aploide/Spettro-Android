package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the bottom edge of a scrolling region out while there is more below
 * it, so a clipped list reads as one that scrolls. Goes before the
 * `verticalScroll` it describes, so the fade sits on the viewport rather
 * than on the content. It erases the content to transparent instead of
 * painting a colour over it, so it works on any surface.
 */
fun Modifier.fadeWhenMoreBelow(state: ScrollState, height: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (state.canScrollForward) {
            val fade = height.toPx().coerceAtMost(size.height / 2)
            val top = size.height - fade
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = top, endY = size.height),
                topLeft = Offset(0f, top),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstOut,
            )
        }
    }
