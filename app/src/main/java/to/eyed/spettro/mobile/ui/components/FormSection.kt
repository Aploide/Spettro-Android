package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * A grouped form section (form.css): the eyebrow [title] above a card of
 * rows, and an optional [footer] caption under it. Rows are separated with
 * [FormDivider].
 */
@Composable
fun FormSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        if (title != null) {
            SectionHeader(title, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
        }
        SpettroCard(modifier = Modifier.fillMaxWidth(), content = content)
        if (footer != null) {
            SectionFooter(footer, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp))
        }
    }
}

/**
 * One row of a grouped form: at least 48dp tall (the desktop's 40px row grown
 * for a finger), the label on the left in text-primary, the value on the right
 * in text-secondary. With [onClick] it is tappable and, unless a [trailing]
 * is given, shows a chevron.
 */
@Composable
fun FormRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    icon: ImageVector? = null,
    labelColor: Color = LocalSpettroColors.current.textPrimary,
    valueColor: Color = LocalSpettroColors.current.textSecondary,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = Dimens.minTouch)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        // With a value, the value takes the free width (right-aligned, and
        // ellipsized if it must be) and the label keeps its own.
        Column(
            if (value != null) Modifier else Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = labelColor, style = RowLabel)
            if (supporting != null) {
                Text(supporting, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (value != null) {
            Text(
                value,
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (trailing != null) {
            trailing()
        } else if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A form row's label: between the desktop's 13px and the phone's prose size. */
val RowLabel = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp)

/** The hairline between two rows of a form card. */
@Composable
fun FormDivider() {
    HairlineDivider()
}

@Preview(name = "Form", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 360)
@Composable
private fun FormSectionPreview() {
    SpettroTheme(darkTheme = true) {
        FormSection(
            title = "About",
            footer = "Spettro Remote protocol v1",
            modifier = Modifier.padding(16.dp),
        ) {
            FormRow("Version", value = "0.1.7")
            FormDivider()
            FormRow("Models & providers", icon = Icons.Outlined.Info, onClick = {})
            FormDivider()
            FormRow("Dark theme", trailing = { SpettroSwitch(checked = true, onCheckedChange = {}) })
        }
    }
}
