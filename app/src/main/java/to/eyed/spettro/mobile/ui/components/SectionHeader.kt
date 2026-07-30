package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * A quiet uppercase section label — 12sp semibold in the secondary color,
 * used above grouped lists (chat sections, settings groups).
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
    )
}

@Preview(name = "Section header", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun SectionHeaderPreview() {
    SpettroTheme(darkTheme = true) {
        SectionHeader("Pinned chats", modifier = Modifier.padding(24.dp))
    }
}
