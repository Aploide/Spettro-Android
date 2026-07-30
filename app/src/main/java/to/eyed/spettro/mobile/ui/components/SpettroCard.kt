package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The app's card surface, the Compose port of the iOS `.card()` modifier:
 * a `surfaceRaised` fill plus a 1dp hairline stroke on the same rounded
 * rectangle. Deliberately flat — no shadow, no tonal elevation; depth is
 * communicated by the near-black canvas underneath.
 *
 * Used for tool calls, notices, reasoning blocks, and panels.
 *
 * @param filled false renders just the hairline outline over a clear fill.
 */
@Composable
fun SpettroCard(
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.radiusMd,
    filled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = if (filled) colors.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Preview(name = "Card light", showBackground = true, backgroundColor = 0xFFF9F9F7)
@Composable
private fun SpettroCardPreviewLight() {
    SpettroTheme(darkTheme = false) {
        SpettroCard(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                "Reasoning: the composer should keep focus after send.",
                modifier = Modifier.padding(Dimens.spacingMd),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(name = "Card dark", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun SpettroCardPreviewDark() {
    SpettroTheme(darkTheme = true) {
        SpettroCard(modifier = Modifier.padding(Dimens.spacingLg), radius = Dimens.radiusSm) {
            Text(
                "install.log — 214 lines",
                modifier = Modifier.padding(Dimens.spacingSm),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
