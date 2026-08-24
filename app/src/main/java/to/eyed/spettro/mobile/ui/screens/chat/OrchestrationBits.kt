package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.model.MemberCall
import to.eyed.spettro.mobile.model.OrchCounts
import to.eyed.spettro.mobile.model.OrchStatus
import to.eyed.spettro.mobile.model.WorkflowScript
import to.eyed.spettro.mobile.model.stripInstance
import to.eyed.spettro.mobile.model.truncateInstance
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The small pieces every orchestration surface is built from — the workflow
 * card, the swarm card and the live strip all draw the same meter, the same
 * status glyph, the same member line.
 *
 * They live together because consistency here is the whole readability story:
 * a run shown in the transcript and the same run shown in the live strip must
 * be recognisably one thing, and a member row must look identical whether it
 * sits under a phase, in a swarm, or in the strip. Splitting these across the
 * three call sites is how the three drift apart.
 *
 * These are ports of the TUI's internal/tui/view_workflow.go and view_swarm.go
 * primitives by way of the desktop's OrchestrationBits.tsx, and they carry the
 * decisions those files argue for: a failure is never rounded away to nothing,
 * an instance name is never truncated through its "#N" suffix, and a running
 * member shows what it is doing NOW rather than the item it was handed at
 * launch.
 */

/** How wide an instance name may get before it is clipped, in characters.
 *  Tighter than the desktop's 22: a phone row has to fit a detail as well. */
internal const val INSTANCE_MAX = 16

/**
 * The per-member tint. The TUI looks the spec up in the agent manifest and
 * uses its declared colour; the app has no manifest, so it maps the spec id
 * itself through the same palette — "code" lands on the same green in every
 * front-end, and anything unknown falls back to the accent.
 */
@Composable
internal fun memberTint(specId: String): Color =
    LocalSpettroColors.current.modeColor(specId.ifEmpty { null })

/** The colour a status reads as, everywhere. */
@Composable
internal fun statusColor(status: OrchStatus): Color {
    val colors = LocalSpettroColors.current
    return when (status) {
        OrchStatus.DONE -> colors.diffAdded
        OrchStatus.FAILED -> colors.diffRemoved
        OrchStatus.RUNNING -> colors.accent
    }
}

// ---------------------------------------------------------------------------
// Progress meter
// ---------------------------------------------------------------------------

/**
 * A slim segmented bar: green for done, red for failed, muted track for what
 * is still to come. Done and failed both count as finished — a failed agent is
 * not still working — but failures get their own colour so a red-heavy bar
 * reads as trouble at a glance.
 *
 * Port of progressBar() in internal/tui/view_workflow.go, including its one
 * non-obvious rule: a single failure among fifty agents rounds to zero pixels
 * and would vanish, so any failure at all is forced to a visible width.
 */
@Composable
internal fun OrchProgressMeter(
    counts: OrchCounts,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    height: Dp = 4.dp,
) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(percent = 50)
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (colors.isDark) 0.20f else 0.16f)
    val total = counts.total.coerceAtLeast(0)

    Row(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(track),
    ) {
        if (total == 0) return@Row
        // Enough to read as a segment, not a hairline.
        val minVisible = 0.06f
        var failedFraction = counts.failed.toFloat() / total
        if (counts.failed > 0 && failedFraction < minVisible) failedFraction = minVisible
        var doneFraction = counts.done.toFloat() / total
        if (doneFraction + failedFraction > 1f) doneFraction = 1f - failedFraction

        if (doneFraction > 0f) {
            Box(Modifier.fillMaxWidth(doneFraction).height(height).background(colors.diffAdded))
        }
        if (failedFraction > 0f) {
            // The remaining width is what is left of the row, so this fraction
            // is taken of that remainder rather than of the whole bar.
            val ofRemaining = (failedFraction / (1f - doneFraction)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(ofRemaining).height(height).background(colors.diffRemoved))
        }
    }
}

// ---------------------------------------------------------------------------
// Status cell strip
// ---------------------------------------------------------------------------

/**
 * One small square per member, in dispatch order, with the work the launch
 * ramp has not reached yet drawn as empty cells.
 *
 * A twenty-agent fan-out cannot be a twenty-row list on a phone, and a meter
 * alone collapses it to a single number. The strip sits between the two: it
 * keeps every member individually visible — you can see three reds among
 * seventeen greens — in the height of one line. Cells wrap rather than scroll
 * so a swarm of any size stays one glanceable shape.
 */
@Composable
internal fun AgentCellStrip(
    members: List<MemberCall>,
    pending: Int,
    modifier: Modifier = Modifier,
    cell: Dp = 7.dp,
) {
    val colors = LocalSpettroColors.current
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val shape = RoundedCornerShape(2.dp)
    WrapRow(
        modifier = modifier,
        spacing = 3.dp,
        lineSpacing = 3.dp,
    ) {
        members.forEach { member ->
            val fill = when (member.status) {
                OrchStatus.DONE -> colors.diffAdded
                OrchStatus.FAILED -> colors.diffRemoved
                // The accent, deliberately, and NOT the member's spec tint.
                // The tint says what kind of agent this is, which is real
                // information — but half the palette is a green, so a swarm of
                // `code` agents drew running-green cells beside done-green
                // ones and the one question the strip exists to answer stopped
                // having an answer. The spec tint lives on the member's name,
                // where it competes with nothing.
                OrchStatus.RUNNING -> colors.accent
            }
            // A replayed member did no work this run; it is drawn hollow so a
            // resumed workflow does not look like it re-ran everything.
            if (member.cached) {
                Box(
                    Modifier.size(cell).clip(shape)
                        .background(fill.copy(alpha = 0.18f))
                        .border(1.dp, fill.copy(alpha = 0.55f), shape),
                )
            } else {
                Box(Modifier.size(cell).clip(shape).background(fill))
            }
        }
        repeat(pending) {
            Box(Modifier.size(cell).clip(shape).background(ghost))
        }
    }
}

/**
 * A minimal flow layout: children laid left to right, wrapping to a new line
 * when the next one will not fit. `FlowRow` would do this, but it is still
 * experimental in the Compose version this app pins, and the strip needs
 * nothing beyond even spacing.
 */
@Composable
private fun WrapRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    lineSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val lineGap = lineSpacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        var x = 0
        var y = 0
        var lineHeight = 0
        var widest = 0
        val positions = ArrayList<Pair<Int, Int>>(placeables.size)
        for (placeable in placeables) {
            if (x > 0 && x + placeable.width > maxWidth) {
                widest = maxOf(widest, x - gap)
                x = 0
                y += lineHeight + lineGap
                lineHeight = 0
            }
            positions += x to y
            x += placeable.width + gap
            lineHeight = maxOf(lineHeight, placeable.height)
        }
        widest = maxOf(widest, x - gap).coerceAtLeast(0)

        layout(widest.coerceAtMost(maxWidth), y + lineHeight) {
            placeables.forEachIndexed { i, placeable ->
                placeable.placeRelative(positions[i].first, positions[i].second)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status glyph
// ---------------------------------------------------------------------------

/** ▶ running / ✓ done / ✗ failed — the three states anything orchestrated is
 *  ever in, drawn the same size everywhere so rows stay aligned. */
@Composable
internal fun OrchStatusGlyph(
    status: OrchStatus,
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    runningTint: Color? = null,
) {
    val colors = LocalSpettroColors.current
    when (status) {
        OrchStatus.RUNNING -> SpettroSpinner(
            size = size,
            color = runningTint ?: colors.accent,
            modifier = modifier,
        )
        OrchStatus.DONE -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = "done",
            tint = colors.diffAdded,
            modifier = modifier.size(size),
        )
        OrchStatus.FAILED -> Icon(
            imageVector = Icons.Outlined.Cancel,
            contentDescription = "failed",
            tint = colors.diffRemoved,
            modifier = modifier.size(size),
        )
    }
}

// ---------------------------------------------------------------------------
// Counts label
// ---------------------------------------------------------------------------

/**
 * "3 running · 5 done · 1 failed · 2 replayed", with the zero terms dropped —
 * port of workflowRun.headline(). A run that has dispatched nothing yet has
 * nothing to say here and renders empty rather than a row of zeroes.
 *
 * The failed term keeps its own colour: on a phone this line is often the only
 * place a failure is spelled out, and a red word survives a glance that a red
 * sliver in the meter does not.
 */
@Composable
internal fun OrchCountsLabel(
    counts: OrchCounts,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
) {
    val colors = LocalSpettroColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    data class Term(val text: String, val failed: Boolean = false)

    val terms = buildList {
        if (counts.running > 0) add(Term("${counts.running} running"))
        if (counts.done > 0) add(Term("${counts.done} done"))
        if (counts.failed > 0) add(Term("${counts.failed} failed", failed = true))
        if (counts.cached > 0) add(Term("${counts.cached} replayed"))
    }
    if (terms.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        terms.forEachIndexed { index, term ->
            if (index > 0) {
                Text("·", fontSize = fontSize, color = muted.copy(alpha = 0.5f))
            }
            Text(
                text = term.text,
                fontSize = fontSize,
                fontWeight = if (term.failed) FontWeight.SemiBold else FontWeight.Medium,
                fontFamily = null,
                color = if (term.failed) colors.diffRemoved else muted,
                maxLines = 1,
            )
        }
    }
}

/** "8/12" in tabular figures, so the ratio does not jitter as it counts up. */
@Composable
internal fun OrchRatio(
    finished: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = "$finished/$total",
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Disclosure chevron
// ---------------------------------------------------------------------------

/** The transcript's expand affordance, rotated by the same spring everywhere. */
@Composable
internal fun DisclosureChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
) {
    val angle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = transcriptSpring(),
        label = "orchChevron",
    )
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = tint,
        modifier = modifier.size(size).rotate(angle),
    )
}

// ---------------------------------------------------------------------------
// Pills
// ---------------------------------------------------------------------------

/** A small tinted capsule: "worktree", "replayed", "N queued". */
@Composable
internal fun OrchPill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(9.dp))
        }
        Text(
            text = text,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = tint,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Member row
// ---------------------------------------------------------------------------

/**
 * One sub-agent line: status glyph, instance in its spec's tint, and its live
 * detail. Expanding reveals the tool calls it made and the summary it reported.
 *
 * The detail is deliberately the member's *latest* tool call rather than the
 * task it was launched with: in a twenty-member fan-out the launch items are
 * near-identical and tell you nothing about progress, which is the readability
 * fix internal/tui/view_swarm.go was written for. Once a member finishes, the
 * live detail stops meaning anything and the row falls back to its task.
 */
@Composable
internal fun MemberRow(
    member: MemberCall,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showReason: Boolean = true,
) {
    val colors = LocalSpettroColors.current
    val tint = memberTint(member.specId)
    val running = member.status == OrchStatus.RUNNING
    // `resultText` rather than `result?.summary`: an agent given a schema
    // returns its structured value and one asked a question returns prose, and
    // neither carries a `summary` field. A member that produced output must
    // never render as a row with nothing behind it.
    val summary = member.resultText
    val hasDetail = member.children.isNotEmpty() || summary.isNotEmpty()
    val last = member.children.lastOrNull()
    // displayDetail re-applies the "[code#3] " title prefix; here the row
    // already says whose work this is, so repeating it just eats the width.
    val live = if (running && last != null) {
        stripInstance(last.displayDetail, member.instance)
    } else {
        ""
    }
    val detail = live.ifEmpty { member.task }.replace("\n", " ")
    val reason = if (showReason) member.failureReason else ""

    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(transcriptSpring()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .clickable(enabled = hasDetail, onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrchStatusGlyph(status = member.status, size = 12.dp, runningTint = tint)
            Text(
                text = truncateInstance(member.instance, INSTANCE_MAX),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                // A finished member fades: what is still moving should lead
                // the eye, and in a wide fan-out that is the only difference
                // the reader is scanning for.
                color = if (running) tint else tint.copy(alpha = 0.72f),
                maxLines = 1,
            )
            if (member.cached) {
                OrchPill(
                    text = "replayed",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = Icons.Outlined.Replay,
                )
            }
            Text(
                text = detail,
                style = MonoSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (member.children.isNotEmpty()) {
                Text(
                    text = "${member.children.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFeatureSettings = TabularNums,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.hairline)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (hasDetail) DisclosureChevron(expanded = expanded, size = 12.dp)
        }

        // A failure whose cause is one tap away is a failure the card hid, and
        // the text is already in hand.
        if (reason.isNotEmpty()) {
            Text(
                text = reason.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.diffRemoved,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 22.dp, end = 4.dp, bottom = 3.dp),
            )
        }

        if (expanded && hasDetail) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp, bottom = Dimens.spacingXs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                member.children.forEach { child ->
                    ToolCallRow(tool = child)
                }
                if (summary.isNotEmpty()) {
                    if (member.resultIsJSON) {
                        OrchCodeBlock(text = summary)
                    } else {
                        MarkdownText(text = summary)
                    }
                }
            }
        }
    }
}

/** Preformatted machine output — a member's structured return, a script. */
@Composable
internal fun OrchCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 14,
) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.canvas)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text = text,
            style = MonoSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Dimens.spacingSm),
        )
    }
}

// ---------------------------------------------------------------------------
// A workflow that never started
// ---------------------------------------------------------------------------

/**
 * The `workflow` tool call on its own — a script the model submitted that
 * started no run, which in practice means it failed on the way in (a saved
 * workflow that does not exist, a script that would not parse).
 *
 * It is drawn here rather than by the ordinary tool row because that row shows
 * a call's arguments, and this call's arguments are an entire JavaScript
 * program: several hundred characters of escaped source, rendered as one line
 * of raw JSON with a red badge on the end. The failure is the point and the
 * source is the detail, so that is the order they appear in — the program is
 * behind a disclosure, and what the reader gets for free is which workflow
 * failed and why.
 */
@Composable
internal fun ScriptCallRow(
    script: WorkflowScript,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val name = script.savedAs.ifEmpty { "workflow" }
    val reason = script.error.ifEmpty { script.tool.output.trim() }
    val hasSource = script.source.isNotEmpty() || script.returned.isNotEmpty()
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val tint = if (script.status == OrchStatus.FAILED) colors.diffRemoved else colors.accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = if (colors.isDark) 0.07f else 0.05f))
            .border(Dimens.hairlineWidth, tint.copy(alpha = 0.25f), shape)
            .animateContentSize(transcriptSpring()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasSource, onClick = onToggle)
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                WorkflowMark(tint = tint, size = 14.dp)
                Text(
                    text = "Workflow",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = tint,
                )
                Text(
                    text = "· $name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OrchStatusGlyph(status = script.status, size = 13.dp)
                if (hasSource) DisclosureChevron(expanded = expanded, size = 12.dp)
            }
            if (reason.isNotEmpty()) {
                Text(
                    text = reason.split("\n").first(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (script.status == OrchStatus.FAILED) {
                        colors.diffRemoved
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (expanded && hasSource) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.spacingMd,
                        end = Dimens.spacingMd,
                        bottom = Dimens.spacingSm,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            ) {
                if (script.source.isNotEmpty()) OrchCodeBlock(text = script.source, maxLines = 24)
                if (script.returned.isNotEmpty()) {
                    OrchSectionLabel("returned")
                    OrchCodeBlock(text = script.returned)
                }
            }
        }
    }
}

/** A tiny uppercase caption above a block. */
@Composable
internal fun OrchSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier,
    )
}

/**
 * The workflow glyph: three stacked bars fanning from a spine, the flowchart
 * mark the desktop card uses. Drawn rather than borrowed from Material, whose
 * closest icon (AccountTree) reads as a file tree.
 */
@Composable
internal fun WorkflowMark(tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.14f)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .width(if (index == 1) size * 0.86f else size * 0.60f)
                        .height(size * 0.16f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(tint.copy(alpha = if (index == 1) 1f else 0.7f)),
                )
            }
        }
    }
}

// MARK: Previews

@Preview(name = "Orchestration bits", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 400)
@Composable
private fun OrchestrationBitsPreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            OrchProgressMeter(OrchCounts(total = 12, running = 3, done = 8, failed = 1), width = 128.dp)
            OrchCountsLabel(OrchCounts(total = 12, running = 3, done = 8, failed = 1, cached = 2))
            AgentCellStrip(members = OrchestrationPreviewData.liveSwarm.members, pending = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                OrchStatusGlyph(OrchStatus.RUNNING)
                OrchStatusGlyph(OrchStatus.DONE)
                OrchStatusGlyph(OrchStatus.FAILED)
                WorkflowMark(tint = LocalSpettroColors.current.accent, size = 14.dp)
            }
            OrchestrationPreviewData.liveSwarm.members.take(3).forEach { member ->
                MemberRow(member = member, expanded = false, onToggle = {})
            }
        }
    }
}
