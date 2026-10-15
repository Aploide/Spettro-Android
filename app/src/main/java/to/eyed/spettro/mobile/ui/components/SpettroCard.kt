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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/** Which ground a [SpettroCard] sits on. */
enum class CardVariant {
    /** `.card`: surface-raised under a hairline. */
    Raised,

    /** Code and output blocks: surface-sunken under a hairline. */
    Sunken,

    /** The error card: danger at 8% on the canvas, a danger-30% border. */
    Error,
}

/**
 * The app's card surface, the desktop's `.card`: a raised fill plus a 1dp
 * hairline stroke on the same rounded rectangle. Deliberately flat — cards
 * never cast a shadow on the desktop; only floating layers (menus, popovers,
 * toasts, the composer) do.
 *
 * @param filled false renders just the hairline outline over a clear fill.
 */
@Composable
fun SpettroCard(
    modifier: Modifier = Modifier,
    radius: Dp = Dimens.radiusMd,
    filled: Boolean = true,
    variant: CardVariant = CardVariant.Raised,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    val fill = when {
        !filled -> androidx.compose.ui.graphics.Color.Transparent
        variant == CardVariant.Sunken -> colors.surfaceSunken
        variant == CardVariant.Error -> colors.danger.copy(alpha = 0.08f).compositeOver(colors.canvas)
        else -> colors.surfaceRaised
    }
    val stroke = if (variant == CardVariant.Error) colors.danger.copy(alpha = 0.3f) else colors.hairline
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = fill,
        contentColor = colors.textPrimary,
        border = BorderStroke(Dimens.hairlineWidth, stroke),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Preview(name = "Card light", showBackground = true, backgroundColor = 0xFFFAFAFA)
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

@Preview(name = "Card dark", showBackground = true, backgroundColor = 0xFF1C1C1E)
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
