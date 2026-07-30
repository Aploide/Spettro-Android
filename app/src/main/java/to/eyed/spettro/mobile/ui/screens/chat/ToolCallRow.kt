package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpToolStatus
import to.eyed.spettro.mobile.model.ToolCallItem
import to.eyed.spettro.mobile.model.ToolIcon
import to.eyed.spettro.mobile.ui.components.DiffStatLabel
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/** The Material icon for a tool row, mapped from the ACP tool kind. */
private val ToolIcon.vector: ImageVector
    get() = when (this) {
        ToolIcon.READ -> Icons.Outlined.Description
        ToolIcon.EDIT -> Icons.Outlined.Edit
        ToolIcon.DELETE -> Icons.Outlined.Delete
        ToolIcon.MOVE -> Icons.Outlined.DriveFileMove
        ToolIcon.SEARCH -> Icons.Outlined.Search
        ToolIcon.EXECUTE -> Icons.Outlined.Terminal
        ToolIcon.THINK -> Icons.Outlined.Psychology
        ToolIcon.FETCH -> Icons.Outlined.Language
        ToolIcon.SWITCH_MODE -> Icons.Outlined.SwapHoriz
        ToolIcon.OTHER -> Icons.Outlined.Build
    }

/** Spring used for every expand/collapse in the transcript. */
internal fun <T> transcriptSpring() =
    spring<T>(stiffness = Spring.StiffnessMediumLow)

/**
 * One tool call as a quiet, expandable row (port of ToolCallView.swift): an
 * icon, a short verb, the human-readable detail — never raw JSON — a +N/-N
 * diff stat, and a live status glyph. Tapping reveals the output or a
 * red/green line diff. Sub-agent calls render as a dedicated tinted card.
 */
@Composable
fun ToolCallRow(
    tool: ToolCallItem,
    modifier: Modifier = Modifier,
) {
    val subAgent = tool.subAgentCall
    if (subAgent != null) {
        SubAgentCard(tool = tool, call = subAgent, modifier = modifier)
    } else {
        PlainToolRow(tool = tool, modifier = modifier)
    }
}

@Composable
private fun PlainToolRow(tool: ToolCallItem, modifier: Modifier) {
    val colors = LocalSpettroColors.current
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val hasDetail = tool.output.isNotEmpty() || tool.diffs.isNotEmpty()
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = transcriptSpring(),
        label = "toolChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(if (expanded) colors.surfaceRaised else colors.surfaceRaised.copy(alpha = 0f))
            .animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasDetail) { expanded = !expanded }
                .padding(horizontal = Dimens.spacingSm, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tool.icon.vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = tool.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tool.displayDetail,
                style = MonoSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            tool.diffStat?.let { stat ->
                if (stat.added > 0 || stat.removed > 0) {
                    DiffStatLabel(added = stat.added, removed = stat.removed, fontSize = 11.sp)
                }
            }
            ToolStatusGlyph(status = tool.status)
            if (hasDetail) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(chevronAngle),
                )
            }
        }
        if (expanded && hasDetail) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.spacingXl,
                        end = Dimens.spacingSm,
                        bottom = Dimens.spacingSm,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                if (tool.diffs.isNotEmpty()) {
                    ToolDiffView(diffs = tool.diffs)
                }
                if (tool.output.isNotEmpty()) {
                    ToolOutputBlock(output = tool.output)
                }
            }
        }
    }
}

@Composable
private fun ToolStatusGlyph(status: AcpToolStatus) {
    val colors = LocalSpettroColors.current
    when (status) {
        AcpToolStatus.PENDING, AcpToolStatus.IN_PROGRESS ->
            SpettroSpinner(size = 12.dp, color = colors.accent)
        AcpToolStatus.COMPLETED -> Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = "Completed",
            tint = colors.diffAdded,
            modifier = Modifier.size(14.dp),
        )
        AcpToolStatus.FAILED -> Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "Failed",
            tint = colors.diffRemoved,
            modifier = Modifier.size(14.dp),
        )
        AcpToolStatus.UNKNOWN -> Unit
    }
}

/** Tool output: monospace 12sp, capped at ~12 lines, scrolls horizontally. */
@Composable
private fun ToolOutputBlock(output: String) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.canvas)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text = output.trim(),
            style = MonoSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Dimens.spacingSm),
        )
    }
}

/**
 * A minimal red/green line view of file edits: for each diff, the changed
 * region only — removed lines prefixed "-" on a translucent red ground, added
 * lines "+" on green — in 12sp monospace with horizontal scrolling.
 */
@Composable
fun ToolDiffView(
    diffs: List<ToolCallItem.ToolDiff>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        diffs.forEach { diff -> SingleDiff(diff) }
    }
}

private const val MAX_DIFF_LINES = 80

@Composable
private fun SingleDiff(diff: ToolCallItem.ToolDiff) {
    val colors = LocalSpettroColors.current
    val (old, new) = diff.changedLines
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = ToolCallItem.shortPath(diff.path),
                style = MonoSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DiffStatLabel(added = new.size, removed = old.size, fontSize = 11.sp)
        }
        val shape = RoundedCornerShape(Dimens.radiusSm)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.canvas)
                .border(Dimens.hairlineWidth, colors.hairline, shape)
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                old.take(MAX_DIFF_LINES).forEach { line ->
                    DiffLine(line = line, prefix = "-", tint = colors.diffRemoved)
                }
                if (old.size > MAX_DIFF_LINES) TruncationNote(old.size - MAX_DIFF_LINES)
                new.take(MAX_DIFF_LINES).forEach { line ->
                    DiffLine(line = line, prefix = "+", tint = colors.diffAdded)
                }
                if (new.size > MAX_DIFF_LINES) TruncationNote(new.size - MAX_DIFF_LINES)
            }
        }
    }
}

@Composable
private fun DiffLine(line: String, prefix: String, tint: androidx.compose.ui.graphics.Color) {
    val style = MonoSmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    Row(modifier = Modifier.fillMaxWidth().background(tint.copy(alpha = 0.10f))) {
        Text(
            text = prefix,
            style = style,
            color = tint,
            modifier = Modifier.width(16.dp).padding(start = Dimens.spacingXs),
        )
        Text(
            text = line.ifEmpty { " " },
            style = style,
            color = tint,
            softWrap = false,
            modifier = Modifier.padding(end = Dimens.spacingSm),
        )
    }
}

@Composable
private fun TruncationNote(count: Int) {
    Text(
        text = "… $count more lines",
        style = MonoSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
    )
}

/**
 * The dedicated card for `agent` tool calls: makes it obvious another agent
 * has spun up, who it is, what it was asked to do, and — once it reports
 * back — its summary rendered as markdown. Tinted with the agent accent.
 */
@Composable
private fun SubAgentCard(
    tool: ToolCallItem,
    call: ToolCallItem.SubAgentCall,
    modifier: Modifier,
) {
    val colors = LocalSpettroColors.current
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val result = tool.subAgentResult
    val hasSummary = result != null && result.summary.isNotEmpty()
    val isRunning = tool.status == AcpToolStatus.PENDING || tool.status == AcpToolStatus.IN_PROGRESS
    val failed = tool.status == AcpToolStatus.FAILED || result?.status == "error"
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = transcriptSpring(),
        label = "agentChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.agentAccent.copy(alpha = if (colors.isDark) 0.07f else 0.05f))
            .border(Dimens.hairlineWidth, colors.agentAccent.copy(alpha = 0.25f), shape)
            .animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasSummary) { expanded = !expanded }
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = colors.agentAccent,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = call.agent,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.agentAccent,
                    )
                    Text(
                        text = "AGENT",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                        color = colors.agentAccent,
                        modifier = Modifier
                            .background(colors.agentAccent.copy(alpha = 0.18f), CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    if (isRunning) {
                        Text(
                            text = "working…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                call.task?.takeIf { it.isNotEmpty() }?.let { task ->
                    Text(
                        text = task,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                isRunning -> SpettroSpinner(size = 12.dp, color = colors.agentAccent)
                failed -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed",
                    tint = colors.diffRemoved,
                    modifier = Modifier.size(14.dp),
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Completed",
                    tint = colors.diffAdded,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (hasSummary) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(chevronAngle),
                )
            }
        }
        if (expanded && result != null && result.summary.isNotEmpty()) {
            HairlineDivider()
            MarkdownText(
                text = result.summary,
                modifier = Modifier.padding(Dimens.spacingMd),
            )
        }
    }
}

// MARK: Previews

@Preview(name = "Tool rows dark", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
@Composable
private fun ToolCallRowPreviewDark() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            ToolCallRow(tool = ChatPreviewData.readTool)
            ToolCallRow(tool = ChatPreviewData.runningTool)
            ToolCallRow(tool = ChatPreviewData.failedTool)
            ToolCallRow(tool = ChatPreviewData.editTool)
            ToolCallRow(tool = ChatPreviewData.agentTool)
            ToolCallRow(tool = ChatPreviewData.runningAgentTool)
        }
    }
}

@Preview(name = "Tool rows light", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 380)
@Composable
private fun ToolCallRowPreviewLight() {
    SpettroTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            ToolCallRow(tool = ChatPreviewData.executeTool)
            ToolCallRow(tool = ChatPreviewData.editTool)
            ToolCallRow(tool = ChatPreviewData.agentTool)
        }
    }
}

@Preview(name = "Diff view", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
@Composable
private fun ToolDiffViewPreview() {
    SpettroTheme(darkTheme = true) {
        ToolDiffView(
            diffs = ChatPreviewData.editTool.diffs,
            modifier = Modifier.padding(Dimens.spacingLg),
        )
    }
}
