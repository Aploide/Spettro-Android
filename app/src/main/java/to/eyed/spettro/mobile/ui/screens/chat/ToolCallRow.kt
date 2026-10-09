package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.theme.MonoTool
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.components.Shimmer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Canvas
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

/** The tool header's verb: the desktop's 13.5px at 600. */
private val ToolVerb = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)

/**
 * One tool call as a quiet, expandable row, as the desktop draws it: a
 * status glyph in a 16dp slot, the verb, the human-readable argument in mono
 * — never raw JSON — and a +N/-N diff stat. Tapping opens the output or a
 * red/green line diff in a panel set in behind a hairline rule. Sub-agent
 * calls render as their own card.
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
        animationSpec = SpettroMotion.chevron(),
        label = "toolChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusXs))
                .clickable(enabled = hasDetail) { expanded = !expanded }
                .heightIn(min = 32.dp)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolStatusGlyph(status = tool.status, denied = tool.denied)
            Text(
                text = tool.displayName,
                style = ToolVerb,
                color = colors.textPrimary,
            )
            Text(
                text = tool.displayDetail,
                style = MonoTool,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            tool.diffStat?.let { stat ->
                if (stat.added > 0 || stat.removed > 0) {
                    DiffStatLabel(added = stat.added, removed = stat.removed, fontSize = 12.sp)
                }
            }
            if (hasDetail) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(15.dp)
                        .rotate(chevronAngle),
                )
            }
        }
        if (expanded && hasDetail) {
            // The desktop's expanded panel: indented to the glyph's centre,
            // behind a 1dp hairline rule.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 7.dp, top = 2.dp, bottom = Dimens.spacingSm)
                    .drawBehind {
                        drawRect(colors.hairline, size = Size(1.dp.toPx(), size.height))
                    }
                    .padding(start = 14.dp),
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

/**
 * The desktop's tool status glyph, a 7dp mark in a 16dp slot: a hollow
 * tertiary ring while pending, a pulsing accent dot while running, a success
 * dot when done, the danger icon when it failed, and a struck-through ring
 * when the user turned it down.
 */
@Composable
private fun ToolStatusGlyph(status: AcpToolStatus, denied: Boolean = false) {
    val colors = LocalSpettroColors.current
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when {
            denied -> Canvas(Modifier.size(9.dp).semantics { contentDescription = "Denied" }) {
                val stroke = 1.5.dp.toPx()
                drawCircle(colors.textTertiary, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
                drawLine(colors.textTertiary, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = stroke)
            }
            status == AcpToolStatus.PENDING -> Canvas(Modifier.size(8.dp)) {
                val stroke = 1.5.dp.toPx()
                drawCircle(colors.textTertiary, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
            }
            status == AcpToolStatus.IN_PROGRESS -> StatusDot(color = colors.accent, pulsing = true)
            status == AcpToolStatus.COMPLETED -> StatusDot(
                color = colors.success,
                modifier = Modifier.semantics { contentDescription = "Completed" },
            )
            status == AcpToolStatus.FAILED -> Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Failed",
                tint = colors.danger,
                modifier = Modifier.size(14.dp),
            )
            else -> Unit
        }
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
            .background(colors.surfaceSunken)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text = output.trim(),
            style = MonoSmall,
            color = colors.textSecondary,
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
                tint = colors.textTertiary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = ToolCallItem.shortPath(diff.path),
                style = MonoSmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
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
                .background(colors.surfaceRaised)
                .border(Dimens.hairlineWidth, colors.hairline, shape)
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                old.take(MAX_DIFF_LINES).forEach { line ->
                    DiffLine(line = line, prefix = "-", tint = colors.danger)
                }
                if (old.size > MAX_DIFF_LINES) TruncationNote(old.size - MAX_DIFF_LINES)
                new.take(MAX_DIFF_LINES).forEach { line ->
                    DiffLine(line = line, prefix = "+", tint = colors.success)
                }
                if (new.size > MAX_DIFF_LINES) TruncationNote(new.size - MAX_DIFF_LINES)
            }
        }
    }
}

@Composable
private fun DiffLine(line: String, prefix: String, tint: androidx.compose.ui.graphics.Color) {
    val style = MonoSmall
    Row(modifier = Modifier.fillMaxWidth().background(tint.copy(alpha = 0.12f))) {
        Text(
            text = prefix,
            style = style,
            color = tint,
            modifier = Modifier.width(18.dp).padding(start = Dimens.spacingXs),
        )
        Text(
            text = line.ifEmpty { " " },
            style = style,
            color = LocalSpettroColors.current.textPrimary,
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
        color = LocalSpettroColors.current.textTertiary,
        modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 2.dp),
    )
}

/**
 * The card for `agent` tool calls (the desktop's sub-agent card): raised,
 * a hairline, and a straight 2dp rail in the agent accent down its left
 * edge, so it is obvious another agent has spun up, who it is, what it was
 * asked to do, and — once it reports back — its summary as markdown.
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
        animationSpec = SpettroMotion.chevron(),
        label = "agentChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .drawBehind {
                val inset = 10.dp.toPx()
                drawRect(
                    colors.agentAccent,
                    topLeft = Offset(0f, inset),
                    size = Size(2.dp.toPx(), (size.height - inset * 2).coerceAtLeast(0f)),
                )
            }
            .animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasSummary) { expanded = !expanded }
                .padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = "Agent",
                tint = colors.agentAccent,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = call.agent,
                        style = ToolVerb,
                        color = colors.textPrimary,
                    )
                    if (isRunning) {
                        Shimmer(
                            text = "Working…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                call.task?.takeIf { it.isNotEmpty() }?.let { task ->
                    Text(
                        text = task,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                isRunning -> StatusDot(color = colors.accent, pulsing = true)
                failed -> Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed",
                    tint = colors.danger,
                    modifier = Modifier.size(14.dp),
                )
                else -> StatusDot(
                    color = colors.success,
                    modifier = Modifier.semantics { contentDescription = "Completed" },
                )
            }
            if (hasSummary) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(15.dp)
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

@Preview(name = "Tool rows dark", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
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

@Preview(name = "Tool rows light", showBackground = true, backgroundColor = 0xFFFAFAFA, widthDp = 380)
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

@Preview(name = "Diff view", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun ToolDiffViewPreview() {
    SpettroTheme(darkTheme = true) {
        ToolDiffView(
            diffs = ChatPreviewData.editTool.diffs,
            modifier = Modifier.padding(Dimens.spacingLg),
        )
    }
}
