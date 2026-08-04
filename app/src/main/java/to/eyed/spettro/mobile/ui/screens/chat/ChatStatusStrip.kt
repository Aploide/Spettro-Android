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
import to.eyed.spettro.mobile.ui.components.RunTicker
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The thin strip under the chat's top bar: the live run ticker (or the mode
 * chip when idle) on the left, plan progress in the middle — tappable to
 * unfold the agent's task list — and context-window occupancy on the right,
 * tinted red past 90%.
 */
@Composable
fun ChatStatusStrip(
    isBusy: Boolean,
    runStartedAt: Long?,
    liveTokens: Long?,
    plan: List<AcpPlanEntry>,
    usage: AcpUsage?,
    modeName: String?,
    modeColorName: String?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    var planExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBusy) {
                RunTicker(
                    startedAt = runStartedAt,
                    tokens = liveTokens,
                    color = colors.modeColor(modeColorName ?: modeName),
                )
            } else if (modeName != null) {
                ModeChip(label = modeName, colorName = modeColorName ?: modeName)
            }

            if (plan.isNotEmpty()) {
                PlanProgress(
                    plan = plan,
                    expanded = planExpanded,
                    onToggle = { planExpanded = !planExpanded },
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            usage?.let { UsageReadout(it) }
        }
        if (planExpanded && plan.isNotEmpty()) {
            PlanList(plan = plan)
        }
        HairlineDivider()
    }
}

@Composable
private fun PlanProgress(
    plan: List<AcpPlanEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val done = plan.count { it.status == "completed" }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickable(onClick = onToggle)
            .padding(horizontal = Dimens.spacingXs, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Checklist,
            contentDescription = "Plan",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "$done/${plan.size}",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { if (plan.isEmpty()) 0f else done.toFloat() / plan.size },
            color = LocalSpettroColors.current.accent,
            trackColor = LocalSpettroColors.current.hairline,
            drawStopIndicator = {},
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(percent = 50)),
        )
    }
}

/** The unfolded plan: one row per entry with a status glyph. */
@Composable
private fun PlanList(plan: List<AcpPlanEntry>) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                bottom = Dimens.spacingSm,
            ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        plan.forEach { entry ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (entry.status) {
                    "completed" -> Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Completed",
                        tint = colors.diffAdded,
                        modifier = Modifier.size(12.dp),
                    )
                    "in_progress" -> SpettroSpinner(size = 10.dp, color = colors.accent)
                    else -> Icon(
                        imageVector = Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = if (entry.status == "completed") {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    ),
                    color = if (entry.status == "in_progress") {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UsageReadout(usage: AcpUsage) {
    val colors = LocalSpettroColors.current
    val fraction = usage.used.toFloat() / usage.size
    val percent = (fraction * 100).toInt()
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
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
            color = if (fraction > 0.9f) colors.diffRemoved else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTokens(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1_000f)
    else -> count.toString()
}

// MARK: Previews

@Preview(name = "Busy", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
@Composable
private fun ChatStatusStripPreviewBusy() {
    SpettroTheme(darkTheme = true) {
        ChatStatusStrip(
            isBusy = true,
            runStartedAt = System.currentTimeMillis() - 42_000,
            liveTokens = 12_800,
            plan = ChatPreviewData.plan,
            usage = ChatPreviewData.usage,
            modeName = "coding",
            modeColorName = "green",
        )
    }
}

@Preview(name = "Idle, hot context", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 380)
@Composable
private fun ChatStatusStripPreviewIdle() {
    SpettroTheme(darkTheme = false) {
        ChatStatusStrip(
            isBusy = false,
            runStartedAt = null,
            liveTokens = null,
            plan = ChatPreviewData.plan,
            usage = ChatPreviewData.usageHot,
            modeName = "plan",
            modeColorName = "purple",
        )
    }
}
