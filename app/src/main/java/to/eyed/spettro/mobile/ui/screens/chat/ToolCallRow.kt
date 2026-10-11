package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.eyed.spettro.mobile.core.B64
import to.eyed.spettro.mobile.core.acp.AcpToolImage
import to.eyed.spettro.mobile.model.ToolPresentation
import to.eyed.spettro.mobile.model.DisplayRow
import to.eyed.spettro.mobile.model.ToolGroups
import androidx.compose.runtime.key
import to.eyed.spettro.mobile.model.UnifiedDiff
import to.eyed.spettro.mobile.ui.components.Pill
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
import androidx.compose.foundation.layout.Spacer
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
import to.eyed.spettro.mobile.ui.theme.TabularNums
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

/**
 * The open chat's project folder, so paths inside it are named from there
 * ([ToolPresentation.shortPath]). Provided by ChatScreen around the
 * transcript and the prompt dock.
 */
val LocalProjectPath = staticCompositionLocalOf<String?> { null }

/**
 * The cards an approval of the open chat's is waiting on, by the id each is
 * filed under: they draw amber with "Needs approval", the one state that is
 * the reader's move rather than the agent's.
 */
val LocalAwaitingTools = compositionLocalOf<Set<String>> { emptySet() }

/** Output lines shown before "Show more". */
private const val OUTPUT_LINES = 30

/** Diff lines drawn before "Show all". */
private const val MAX_DIFF_LINES = 400

/**
 * One tool call as one quiet line, the desktop's ToolRow: a status dot, the
 * bold verb ("Read", "Bash"), the argument in mono, and a muted note at the
 * right edge ("+12 −3", "42 lines", "exit 1"). Opening it shows what the
 * call did in a panel under a thin rule: the locations it touched, a real
 * unified diff for an edit, `$ command` and its output for a shell call,
 * and the images it returned.
 */
@Composable
internal fun PlainToolRow(tool: ToolCallItem, modifier: Modifier = Modifier) {
    val colors = LocalSpettroColors.current
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val awaiting = tool.id in LocalAwaitingTools.current
    val command = tool.shellCommand
    val hasDetail = tool.output.isNotBlank() || tool.diffs.isNotEmpty() || tool.images.isNotEmpty() ||
        command != null || tool.locations.isNotEmpty()
    // The stat comes from the real diff, which is an LCS: worked out off the
    // main thread, as DiffView does, so a big rewrite scrolling into view
    // doesn't stall a frame. The note appears once it is ready.
    val stat by produceState<ToolCallItem.DiffStat?>(initialValue = null, tool.diffs) {
        value = if (tool.diffs.isEmpty()) null else withContext(Dispatchers.Default) { tool.diffStat }
    }
    val meta = tool.rowMeta
    val argument = tool.rowArgument
    val verb = tool.displayName
    val denied = tool.denied && tool.status == AcpToolStatus.FAILED
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = SpettroMotion.chevron(),
        label = "toolChevron",
    )
    val spoken = listOfNotNull(
        "$verb $argument".trim(),
        when {
            awaiting -> "needs approval"
            denied -> "denied"
            tool.status == AcpToolStatus.PENDING -> "not started"
            tool.status == AcpToolStatus.IN_PROGRESS -> "running"
            tool.status == AcpToolStatus.FAILED -> "failed"
            else -> null
        },
        stat?.takeIf { it.added > 0 || it.removed > 0 }?.let { "${it.added} added, ${it.removed} removed" },
        meta,
    ).joinToString(", ")

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
                .heightIn(min = 36.dp)
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolStatusGlyph(status = tool.status, denied = denied, awaiting = awaiting)
            Text(text = verb, style = ToolVerb, color = colors.textPrimary, maxLines = 1)
            Text(
                text = argument,
                style = MonoTool,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (awaiting) {
                Text(
                    "Needs approval",
                    style = ToolMeta.copy(fontWeight = FontWeight.Medium),
                    color = colors.warning,
                    maxLines = 1,
                )
            }
            stat?.let { if (it.added > 0 || it.removed > 0) DiffStatLabel(added = it.added, removed = it.removed, fontSize = 12.sp) }
            if (meta != null) {
                Text(
                    meta,
                    style = ToolMeta,
                    color = if (tool.status == AcpToolStatus.FAILED && !denied) colors.danger else colors.textTertiary,
                    maxLines = 1,
                )
            }
            if (hasDetail) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(15.dp)
                        .rotate(chevronAngle),
                )
            }
        }
        if (expanded && hasDetail) {
            ToolPanel(tool = tool, command = command)
        }
    }
}

/**
 * The folded "Read 5 files" line (the desktop's ToolGroup): it reads like
 * one more tool row — the same done dot, the same weight — and opens to the
 * rows it stands for, each still expandable on its own.
 */
@Composable
fun ToolGroupRow(group: DisplayRow.Tools, modifier: Modifier = Modifier) {
    val colors = LocalSpettroColors.current
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = SpettroMotion.chevron(),
        label = "groupChevron",
    )
    val summary = ToolGroups.summary(group.tools)
    Column(modifier = modifier.fillMaxWidth().animateContentSize(transcriptSpring())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusXs))
                .clickable { expanded = !expanded }
                .heightIn(min = 36.dp)
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .semantics(mergeDescendants = true) { contentDescription = summary },
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolStatusGlyph(status = AcpToolStatus.COMPLETED)
            Text(summary, style = ToolVerb, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp).rotate(chevronAngle),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 9.dp, bottom = Dimens.spacingXs)
                    .drawBehind { drawRect(colors.hairline, size = Size(1.dp.toPx(), size.height)) }
                    .padding(start = 6.dp),
            ) {
                group.tools.forEach { key(it.id) { PlainToolRow(tool = it) } }
            }
        }
    }
}

/** The row's right-edge note: label size, tertiary, tabular digits. */
private val ToolMeta = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TabularNums)

/**
 * The opened row (`.tr-tool-panel`): indented to the glyph's centre, behind
 * a 1dp hairline rule.
 */
@Composable
private fun ToolPanel(tool: ToolCallItem, command: String?) {
    val colors = LocalSpettroColors.current
    val projectPath = LocalProjectPath.current
    val output = tool.output.trimEnd()
    // An edit's text is a one-line receipt of what its diff already shows.
    val showOutput = output.isNotEmpty() && (tool.diffs.isEmpty() || tool.status == AcpToolStatus.FAILED)
    // A diff already names its file, with every line numbered.
    val diffPaths = tool.diffs.map { it.path }.toSet()
    val locations = tool.locations.filter { it.path !in diffPaths }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 9.dp, top = 2.dp, bottom = Dimens.spacingSm)
            .drawBehind {
                drawRect(colors.hairline, size = Size(1.dp.toPx(), size.height))
            }
            .padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        if (locations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                locations.forEach { location ->
                    Text(
                        text = buildAnnotatedString {
                            append(ToolPresentation.shortPath(location.path, projectPath))
                            location.line?.let { line ->
                                withStyle(SpanStyle(color = colors.textTertiary)) { append(":$line") }
                            }
                        },
                        style = MonoSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        tool.diffs.forEach { DiffView(diff = it) }
        if (command != null || showOutput) {
            OutputBlock(command = command, output = if (showOutput) output else "")
        }
        if (tool.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                tool.images.forEach { ToolImageThumb(it) }
            }
        }
    }
}

/**
 * The desktop's tool status glyph, a 7dp mark in a 16dp slot: a hollow
 * tertiary ring not yet started, a pulsing accent dot while running, a
 * success dot done, a danger cross failed, amber with a halo while the CLI
 * waits on the user's approval, and a struck-through ring when the user
 * turned it down — their answer, not a failure.
 */
@Composable
internal fun ToolStatusGlyph(status: AcpToolStatus, denied: Boolean = false, awaiting: Boolean = false) {
    val colors = LocalSpettroColors.current
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when {
            awaiting -> StatusDot(color = colors.warning, halo = true)
            denied -> Canvas(Modifier.size(8.dp)) {
                val stroke = 1.5.dp.toPx()
                drawCircle(colors.textTertiary, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
                drawLine(colors.textTertiary, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = stroke)
            }
            status == AcpToolStatus.IN_PROGRESS -> StatusDot(color = colors.accent, pulsing = true)
            status == AcpToolStatus.COMPLETED -> StatusDot(color = colors.success)
            status == AcpToolStatus.FAILED -> Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(12.dp),
            )
            else -> Canvas(Modifier.size(8.dp)) {
                val stroke = 1.5.dp.toPx()
                drawCircle(colors.textTertiary, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
            }
        }
    }
}

/**
 * A shell call's `$ command` and its output, or any tool's output, held to
 * the first 30 lines until asked for the rest (`.tr-output`).
 */
@Composable
private fun OutputBlock(command: String?, output: String) {
    val colors = LocalSpettroColors.current
    var all by rememberSaveable(output) { mutableStateOf(false) }
    val lines = if (output.isEmpty()) emptyList() else output.split("\n")
    val hidden = if (all) 0 else (lines.size - OUTPUT_LINES).coerceAtLeast(0)
    val shown = if (hidden > 0) lines.take(OUTPUT_LINES).joinToString("\n") else output
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(Dimens.hairlineWidth, colors.hairline, shape),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        ) {
            if (command != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textTertiary)) { append("$ ") }
                        append(command)
                    },
                    style = MonoSmall,
                    color = colors.textPrimary,
                    softWrap = false,
                )
            }
            if (shown.isNotEmpty()) {
                SelectionContainer {
                    Text(text = shown, style = MonoSmall, color = colors.textSecondary, softWrap = false)
                }
            }
        }
        if (hidden > 0) {
            MoreButton(if (hidden == 1) "Show 1 more line" else "Show $hidden more lines") { all = true }
        }
    }
}

/** The quiet "Show more" link under an output or a diff (`.tr-more`). */
@Composable
private fun MoreButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
        color = LocalSpettroColors.current.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/**
 * One file's change as a unified diff, the desktop's DiffView: the path,
 * "New file" for a created one, the stat; then old and new line numbers, the
 * sign, and the text, added and removed rows on their tinted grounds, hunks
 * after the first headed `@@ −a,b +c,d @@`. The transcript draws up to 400
 * lines before "Show all"; an approval card asks for a glance (30) and says
 * "Show full diff".
 */
@Composable
fun DiffView(
    diff: ToolCallItem.ToolDiff,
    modifier: Modifier = Modifier,
    maxLines: Int = MAX_DIFF_LINES,
    moreLabel: String? = null,
) {
    val colors = LocalSpettroColors.current
    var all by rememberSaveable(diff.path, diff.newText.length) { mutableStateOf(false) }
    // Worked out off the main thread for a big file; the head shows meanwhile.
    val unified by produceState<UnifiedDiff.Diff?>(initialValue = null, diff) {
        value = withContext(Dispatchers.Default) { diff.unified }
    }
    val name = ToolPresentation.shortPath(diff.path, LocalProjectPath.current)
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(Dimens.hairlineWidth, colors.hairline, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceRaised)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MonoSmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
                maxLines = 1,
                // Cut in the middle, as the desktop does: the file's name stays.
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
            if (diff.oldText == null) Pill("New file")
            unified?.let { DiffStatLabel(added = it.added, removed = it.removed, fontSize = 11.sp) }
        }
        HairlineDivider()
        val d = unified ?: return@Column
        val total = d.lineCount
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSunken)
                // An approval's glance scrolls inside 300dp; the transcript's
                // diff is as tall as its lines (the list scrolls it).
                .then(
                    if (maxLines < MAX_DIFF_LINES) {
                        Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                )
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max).padding(vertical = 2.dp)) {
                if (d.hunks.isEmpty()) {
                    Text(
                        "No changes",
                        style = MonoSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                var budget = if (all) Int.MAX_VALUE else maxLines
                d.hunks.forEachIndexed { index, hunk ->
                    if (budget <= 0) return@forEachIndexed
                    val lines = hunk.lines.take(budget)
                    budget -= lines.size
                    if (index > 0) {
                        Text(
                            text = "@@ −${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@",
                            style = MonoSmall,
                            color = colors.textTertiary,
                            softWrap = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.hoverFill)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                    lines.forEach { DiffRow(it) }
                }
            }
        }
        if (!all && total > maxLines) {
            MoreButton(moreLabel ?: "Show all $total lines") { all = true }
        }
    }
}

private val LineNumberStyle = TextStyle(fontFamily = MonoSmall.fontFamily, fontSize = 11.sp, lineHeight = 18.sp, fontFeatureSettings = TabularNums)

@Composable
private fun DiffRow(line: UnifiedDiff.Line) {
    val colors = LocalSpettroColors.current
    val ground = when (line.kind) {
        UnifiedDiff.Kind.ADDED -> colors.diffAddedBg
        UnifiedDiff.Kind.REMOVED -> colors.diffRemovedBg
        UnifiedDiff.Kind.CONTEXT -> Color.Transparent
    }
    val sign = when (line.kind) {
        UnifiedDiff.Kind.ADDED -> "+"
        UnifiedDiff.Kind.REMOVED -> "−"
        UnifiedDiff.Kind.CONTEXT -> " "
    }
    val spoken = when (line.kind) {
        UnifiedDiff.Kind.ADDED -> "Added: "
        UnifiedDiff.Kind.REMOVED -> "Removed: "
        UnifiedDiff.Kind.CONTEXT -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ground)
            .semantics(mergeDescendants = true) { contentDescription = spoken + line.text },
    ) {
        Text(
            line.oldNo?.toString() ?: "",
            style = LineNumberStyle,
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp).padding(end = 6.dp),
        )
        Text(
            line.newNo?.toString() ?: "",
            style = LineNumberStyle,
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp).padding(end = 6.dp),
        )
        Text(
            sign,
            style = MonoSmall.copy(lineHeight = 18.sp),
            color = when (line.kind) {
                UnifiedDiff.Kind.ADDED -> colors.success
                UnifiedDiff.Kind.REMOVED -> colors.danger
                UnifiedDiff.Kind.CONTEXT -> colors.textTertiary
            },
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = buildAnnotatedString {
                append(line.text.ifEmpty { " " })
                if (line.noNewline && line.kind != UnifiedDiff.Kind.CONTEXT) {
                    withStyle(SpanStyle(color = colors.textTertiary)) { append("  No newline at end of file") }
                }
            },
            style = MonoSmall.copy(lineHeight = 18.sp),
            color = if (line.kind == UnifiedDiff.Kind.CONTEXT) colors.textSecondary else colors.textPrimary,
            softWrap = false,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}

/** An image a tool returned, as a small thumbnail. */
@Composable
private fun ToolImageThumb(image: AcpToolImage) {
    val bitmap = remember(image.data) {
        B64.decodeStd(image.data)?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(Dimens.radiusSm)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Image from the tool",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(shape)
                .border(Dimens.hairlineWidth, LocalSpettroColors.current.hairline, shape),
        )
    }
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
        AgentCardHeader(
            agent = call.agent,
            task = call.task,
            expanded = expanded,
            hasBody = hasSummary,
            onToggle = { expanded = !expanded },
            glyph = {
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
            },
        )
        if (expanded && result != null && result.summary.isNotEmpty()) {
            HairlineDivider()
            MarkdownText(
                text = result.summary,
                modifier = Modifier.padding(Dimens.spacingMd),
            )
        }
    }
}

/**
 * A sub-agent card's head, as the desktop's `.tr-agent-header`: the status
 * glyph, "Agent" in 600 · the agent's name in the secondary ink, how many
 * tools it ran, and the task underneath in 13sp secondary — one line until
 * the card opens. The card's rail says "another agent"; no icon or eyebrow
 * repeats it.
 */
@Composable
internal fun AgentCardHeader(
    agent: String,
    task: String?,
    expanded: Boolean,
    hasBody: Boolean,
    onToggle: () -> Unit,
    glyph: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    toolCount: Int = 0,
) {
    val colors = LocalSpettroColors.current
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = SpettroMotion.chevron(),
        label = "agentChevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasBody, onClick = onToggle)
            .padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 3.dp).size(14.dp), contentAlignment = Alignment.Center) { glyph() }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Agent",
                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text("·", style = TextStyle(fontSize = 13.5.sp), color = colors.textTertiary)
                Text(
                    agent,
                    style = TextStyle(fontSize = 13.5.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (toolCount > 0) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (toolCount == 1) "1 tool call" else "$toolCount tool calls",
                        style = TextStyle(fontSize = 12.sp, fontFeatureSettings = TabularNums),
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
            task?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasBody) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.textTertiary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(15.dp)
                    .rotate(chevronAngle),
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
        DiffView(
            diff = ChatPreviewData.editTool.diffs.first(),
            modifier = Modifier.padding(Dimens.spacingLg),
        )
    }
}
