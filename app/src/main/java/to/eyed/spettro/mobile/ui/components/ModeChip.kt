package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * A small mode-tinted chip: the mode's color at a low-opacity fill with the
 * label in the full color, so "coding" reads green and "plan" reads purple
 * everywhere, exactly as in the TUI and the macOS app.
 *
 * @param colorName a manifest color name ("green", "cyan", ...) or a mode id
 *   ("plan", "coding", ...); null falls back to the accent.
 */
@Composable
fun ModeChip(
    label: String,
    colorName: String?,
    modifier: Modifier = Modifier,
) {
    val tint = LocalSpettroColors.current.modeColor(colorName)
    Row(
        modifier = modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(Dimens.radiusInputPill))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = tint,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Preview(name = "Mode chips", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun ModeChipPreview() {
    SpettroTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip("plan", "plan")
            ModeChip("coding", "coding")
            ModeChip("ask", "ask")
            ModeChip("explore", "yellow")
            ModeChip("default", null)
        }
    }
}
