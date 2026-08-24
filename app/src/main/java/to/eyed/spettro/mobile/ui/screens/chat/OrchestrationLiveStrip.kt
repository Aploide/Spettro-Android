package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import to.eyed.spettro.mobile.model.MemberCall
import to.eyed.spettro.mobile.model.OrchRun
import to.eyed.spettro.mobile.model.OrchStatus
import to.eyed.spettro.mobile.model.PhaseState
import to.eyed.spettro.mobile.model.TranscriptRow
import to.eyed.spettro.mobile.model.activeRuns
import to.eyed.spettro.mobile.model.stripInstance
import to.eyed.spettro.mobile.model.truncateInstance
import to.eyed.spettro.mobile.ui.components.GlareText
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The live edge of an in-flight run, pinned above the composer.
 *
 * The transcript card is the *record* of a run: every phase, every member,
 * every tool it called, kept forever and scrolled past. That is the wrong
 * instrument for the twenty seconds while a fan-out is in flight, because the
 * thing you want then is a single question answered continuously — who is
 * still working, and on what — and in the card that answer is buried under the
 * members that have already finished and scrolls away the moment the model says
 * anything. On a phone it scrolls away immediately: the transcript IS the
 * screen.
 *
 * The desktop answers this with a narrow column docked beside the transcript
 * (the analogue of the TUI's ctrl+b side panel). A 426dp phone has no column to
 * spare, so the same idea becomes a one-line strip above the composer — always
 * visible, never scrolled — that opens into a sheet holding the full live view.
 *
 * Three behaviours are load-bearing:
 *
 *  1. A finished run does not blink out. `activeRuns` drops it the instant its
 *     status flips, which in a fast workflow means the line you were reading
 *     vanishes mid-sentence. Instead the strip holds the last snapshot for a
 *     beat, shows it settled, and only then lets it go — so the eye always
 *     lands on an answer.
 *  2. A running member's detail is its *latest tool call*, not the task it was
 *     launched with. In a twenty-member fan-out the launch items are
 *     near-identical and say nothing about progress; the live detail is the
 *     entire reason to watch this strip rather than the card.
 *  3. Nothing that moves here moves layout. This recomposes several times a
 *     second during a big fan-out, so the strip is a fixed height, every string
 *     is clamped to one line, and a detail that wrapped would make the composer
 *     jump under the user's thumb.
 */

/** How long a finished run stays on the strip before it is let go. Long enough
 *  to be read on a glance back at the screen, short enough that the strip never
 *  becomes a log. */
private const val HOLD_MILLIS = 4_000L

@Composable
fun OrchestrationLiveStrip(
    rows: List<TranscriptRow>,
    modifier: Modifier = Modifier,
) {
    val live = remember(rows) { activeRuns(rows) }
    // The last run we showed, kept past its own death so the strip can settle
    // rather than disappear. Keyed by tool id: a *different* run starting is a
    // new thing to show, not a continuation of the old one.
    var held by remember { mutableStateOf<OrchRun?>(null) }
    var holding by remember { mutableStateOf(false) }

    LaunchedEffect(live.firstOrNull()?.tool?.id, live.isEmpty()) {
        val current = live.firstOrNull()
        if (current != null) {
            held = current
            holding = false
            return@LaunchedEffect
        }
        if (held == null) return@LaunchedEffect
        // Re-read the settled run out of the rows so the held snapshot shows
        // its final counts rather than the ones it had a frame before it ended.
        held = settledRun(rows, held?.tool?.id) ?: held
        holding = true
        delay(HOLD_MILLIS)
        held = null
        holding = false
    }

    // While live, always render the *current* object: `held` is only a fallback
    // for the beat after the run ends.
    val shown = live.firstOrNull() ?: held
    var sheetOpen by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
        exit = fadeOut(tween(240)) + shrinkVertically(tween(240)),
        modifier = modifier,
    ) {
        val run = shown ?: return@AnimatedVisibility
        StripLine(
            run = run,
            extraRuns = (live.size - 1).coerceAtLeast(0),
            settled = holding,
            onTap = { sheetOpen = true },
        )
    }

    if (sheetOpen && shown != null) {
        LiveRunSheet(
            runs = if (live.isEmpty()) listOfNotNull(held) else live,
            onDismiss = { sheetOpen = false },
        )
    }
}

/** The settled version of a run, once its status has flipped. */
private fun settledRun(rows: List<TranscriptRow>, toolId: String?): OrchRun? {
    if (toolId == null) return null
    return rows.filterIsInstance<TranscriptRow.Run>()
        .map { it.run }
        .firstOrNull { it.tool.id == toolId }
}

// ---------------------------------------------------------------------------
// The strip itself
// ---------------------------------------------------------------------------

@Composable
private fun StripLine(
    run: OrchRun,
    extraRuns: Int,
    settled: Boolean,
    onTap: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val accent = when {
        settled && run.status == OrchStatus.FAILED -> colors.diffRemoved
        settled -> colors.diffAdded
        run is OrchRun.Swarm -> colors.agentAccent
        else -> colors.accent
    }
    val latest = latestActivity(run)

    Column(Modifier.fillMaxWidth().background(colors.surfaceRaised)) {
        HairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(horizontal = Dimens.spacingLg, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (run is OrchRun.Swarm) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp),
                )
            } else {
                WorkflowMark(tint = accent, size = 12.dp)
            }
            GlareText(
                text = if (run is OrchRun.Swarm) "swarm" else run.displayTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accent,
                isActive = !settled,
            )
            if (extraRuns > 0) {
                OrchPill(text = "+$extraRuns", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OrchProgressMeter(counts = run.counts, width = 44.dp)
            OrchRatio(finished = run.counts.finished, total = run.counts.total)
            // The live edge: whichever member moved most recently, and what it
            // is doing. Once the run is over this is the conclusion instead.
            Text(
                text = if (settled) settledLine(run) else latest,
                style = MonoSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DisclosureChevron(expanded = false, size = 12.dp)
        }
    }
}

/**
 * What the run is doing right now, in one line: the running member that most
 * recently produced a tool call, and that call. Falls back to the member's task
 * when it has not called anything yet, and to a count when nothing is running —
 * an Ultra swarm mid-ramp has members it has accepted but not launched.
 */
private fun latestActivity(run: OrchRun): String {
    val running = run.members.filter { it.status == OrchStatus.RUNNING }
    if (running.isEmpty()) {
        return if (run.counts.total > run.members.size) "launching…" else "waiting…"
    }
    val withWork = running.lastOrNull { it.children.isNotEmpty() } ?: running.first()
    val last = withWork.children.lastOrNull()
    val detail = if (last != null) {
        stripInstance(last.displayDetail, withWork.instance)
    } else {
        withWork.task
    }
    val name = truncateInstance(withWork.instance, INSTANCE_MAX)
    val text = detail.replace("\n", " ")
    return if (text.isEmpty()) name else "$name  $text"
}

/** The one line a just-finished run leaves behind. */
private fun settledLine(run: OrchRun): String = when {
    run.counts.failed > 0 -> "${run.counts.done} done · ${run.counts.failed} failed"
    run.counts.done > 0 -> "${run.counts.done} done"
    run.status == OrchStatus.FAILED -> "failed"
    else -> "finished"
}

// ---------------------------------------------------------------------------
// The sheet
// ---------------------------------------------------------------------------

/**
 * The whole live view: every in-flight run, its declared plan, and a row for
 * each member that is still working.
 *
 * Phases stay listed — including the ones nothing has reached, because knowing
 * what is still coming is half the value of a declared plan — but only
 * *running* members get a row. The finished ones are in the transcript card;
 * repeating them here would turn the one surface that answers "what now" into a
 * second copy of the record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveRunSheet(
    runs: List<OrchRun>,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingLg,
                    bottom = Dimens.spacingXl,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        ) {
            runs.forEach { run -> LiveRunSection(run) }
        }
    }
}

@Composable
private fun LiveRunSection(run: OrchRun) {
    val colors = LocalSpettroColors.current
    val accent = if (run is OrchRun.Swarm) colors.agentAccent else colors.accent

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (run is OrchRun.Swarm) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                WorkflowMark(tint = accent, size = 13.dp)
            }
            Text(
                text = run.displayTitle,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OrchRatio(finished = run.counts.finished, total = run.counts.total)
        }

        OrchProgressMeter(
            counts = run.counts,
            width = 1000.dp,
            height = 5.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        OrchCountsLabel(counts = run.counts)

        val pending = (run as? OrchRun.Swarm)?.pending?.size ?: 0
        if (run.members.isNotEmpty() || pending > 0) {
            AgentCellStrip(members = run.members, pending = pending, cell = 9.dp)
        }

        when (run) {
            is OrchRun.Workflow -> run.phases
                .filter { it.title.isNotEmpty() || it.members.isNotEmpty() }
                .forEach { phase -> LivePhase(phase.title, phase.state, phase.members) }
            is OrchRun.Swarm -> LiveMembers(run.members)
        }
    }
}

@Composable
private fun LivePhase(
    title: String,
    state: PhaseState,
    members: List<MemberCall>,
) {
    val colors = LocalSpettroColors.current
    val tint = when (state) {
        PhaseState.RUNNING -> colors.accent
        PhaseState.FAILED -> colors.diffRemoved
        PhaseState.DONE -> colors.diffAdded
        PhaseState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
            Text(
                text = title.ifEmpty { "no phase" },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (state == PhaseState.PENDING) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = when (state) {
                    PhaseState.PENDING -> "pending"
                    PhaseState.DONE -> "done"
                    PhaseState.FAILED -> "failed"
                    PhaseState.RUNNING -> "${members.count { it.status == OrchStatus.RUNNING }} running"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFeatureSettings = TabularNums,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        LiveMembers(members, indent = 12.dp)
    }
}

/** Only the members still working: the finished ones are in the card. */
@Composable
private fun LiveMembers(
    members: List<MemberCall>,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val running = members.filter { it.status == OrchStatus.RUNNING }
    if (running.isEmpty()) return
    Column(
        modifier = Modifier.padding(start = indent),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        running.forEach { member ->
            val last = member.children.lastOrNull()
            val detail = if (last != null) {
                stripInstance(last.displayDetail, member.instance)
            } else {
                member.task
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrchStatusGlyph(
                    status = OrchStatus.RUNNING,
                    size = 11.dp,
                    runningTint = memberTint(member.specId),
                )
                Text(
                    text = truncateInstance(member.instance, INSTANCE_MAX),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = memberTint(member.specId),
                    maxLines = 1,
                )
                Text(
                    text = detail.replace("\n", " "),
                    style = MonoSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// MARK: Previews

@Preview(name = "Live strip", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 400)
@Composable
private fun LiveStripPreview() {
    SpettroTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            OrchestrationLiveStrip(rows = OrchestrationPreviewData.liveWorkflowRows)
            OrchestrationLiveStrip(rows = OrchestrationPreviewData.liveSwarmRows)
        }
    }
}
