package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The git-stat readout: "+N" in [to.eyed.spettro.mobile.ui.theme.SpettroColors.diffAdded]
 * and "-N" in diffRemoved, with monospaced digits so the label doesn't
 * shimmy as counts change.
 */
@Composable
fun DiffStatLabel(
    added: Int,
    removed: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
) {
    val colors = LocalSpettroColors.current
    val style = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TabularNums,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+$added", color = colors.diffAdded, style = style)
        Text("-$removed", color = colors.diffRemoved, style = style)
    }
}

@Preview(name = "Diff stat", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun DiffStatLabelPreview() {
    SpettroTheme(darkTheme = true) {
        DiffStatLabel(added = 128, removed = 47, modifier = Modifier.padding(24.dp))
    }
}
