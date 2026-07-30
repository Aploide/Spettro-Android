package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/** A 1dp divider in the hairline color — the seam between flat regions. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = Dimens.hairlineWidth,
        color = LocalSpettroColors.current.hairline,
    )
}

@Preview(name = "Hairline", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun HairlineDividerPreview() {
    SpettroTheme(darkTheme = true) {
        HairlineDivider(modifier = Modifier.padding(24.dp).width(200.dp))
    }
}
