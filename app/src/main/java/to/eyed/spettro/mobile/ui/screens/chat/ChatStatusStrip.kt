package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.core.acp.AcpUsage
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.components.ModeChip
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The thin strip under the chat's top bar: the mode chip on the left and
 * context-window occupancy on the right, tinted red past 90%. The run's
 * live readout is the transcript's tail, and the plan is the todo tab over
 * the composer, as on the desktop.
 */
@Composable
fun ChatStatusStrip(
    usage: AcpUsage?,
    modeName: String?,
    modeColorName: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (modeName != null) {
                ModeChip(label = modeName, colorName = modeColorName ?: modeName)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            usage?.let { UsageReadout(it) }
        }
        HairlineDivider()
    }
}

@Composable
private fun UsageReadout(usage: AcpUsage) {
    val colors = LocalSpettroColors.current
    val fraction = if (usage.size > 0) usage.used.toFloat() / usage.size else 0f
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cumulative session tokens — the same figure the PC shows.
        usage.tokensUsed?.takeIf { it > 0 }?.let { total ->
            Text(
                text = "${formatTokens(total)} tok",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = contextPercentLabel(usage.used, usage.size),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
            color = if (fraction > 0.9f) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The context meter's figure as the desktop's percentLabel words it: whole
 * percents rounded (20.5% reads 21%, as on the computer, not 20), and a
 * context that has anything in it never reads 0% — "<1%".
 */
internal fun contextPercentLabel(used: Int, size: Int): String {
    // In doubles, as JavaScript reckons it: 41k of 200k is 20.5, not 20.49….
    val fraction = if (size > 0) used.toDouble() / size else 0.0
    val percent = Math.round(fraction * 100)
    return if (percent == 0L && fraction > 0.0) "<1%" else "$percent%"
}

private fun formatTokens(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1_000f)
    else -> count.toString()
}

// MARK: Previews

@Preview(name = "Busy", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun ChatStatusStripPreviewBusy() {
    SpettroTheme(darkTheme = true) {
        ChatStatusStrip(
            usage = ChatPreviewData.usage,
            modeName = "Coding",
            modeColorName = "coding",
        )
    }
}

@Preview(name = "Idle, hot context", showBackground = true, backgroundColor = 0xFFFAFAFA, widthDp = 380)
@Composable
private fun ChatStatusStripPreviewIdle() {
    SpettroTheme(darkTheme = false) {
        ChatStatusStrip(
            usage = ChatPreviewData.usageHot,
            modeName = "plan",
            modeColorName = "plan",
        )
    }
}
