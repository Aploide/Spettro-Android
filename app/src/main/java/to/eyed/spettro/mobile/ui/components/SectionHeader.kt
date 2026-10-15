package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Eyebrow
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * A section eyebrow above a grouped card (form.css `.form-section-title`):
 * 11sp semibold, 0.05em tracking, text-secondary. Pass the label in sentence
 * case ("Paired devices"); it is uppercased here, at render time, the way the
 * desktop does it in CSS — so the source string stays readable to a screen
 * reader and in search.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = LocalSpettroColors.current.textSecondary,
        style = Eyebrow,
    )
}

/**
 * The caption under a grouped card (form.css `.form-section-footer`): 11sp at
 * 1.45, text-secondary at 80%, explaining the section in a sentence.
 */
@Composable
fun SectionFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = LocalSpettroColors.current.textSecondary.copy(alpha = 0.8f),
        style = Eyebrow.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 16.sp),
    )
}

@Preview(name = "Section header", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun SectionHeaderPreview() {
    SpettroTheme(darkTheme = true) {
        SectionHeader("Paired devices", modifier = Modifier.padding(24.dp))
    }
}
