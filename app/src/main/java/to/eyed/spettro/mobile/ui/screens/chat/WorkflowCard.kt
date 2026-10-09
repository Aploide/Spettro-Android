package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import to.eyed.spettro.mobile.model.MemberCall
import to.eyed.spettro.mobile.model.OrchStatus
import to.eyed.spettro.mobile.model.PhaseState
import to.eyed.spettro.mobile.model.RunStatus
import to.eyed.spettro.mobile.model.WorkflowPhase
import to.eyed.spettro.mobile.model.WorkflowRun
import to.eyed.spettro.mobile.model.capMembers
import to.eyed.spettro.mobile.model.epochMillisOrNull
import to.eyed.spettro.mobile.model.planLabel
import to.eyed.spettro.mobile.model.runStateDetail
import to.eyed.spettro.mobile.model.runStateLine
import to.eyed.spettro.mobile.ui.components.GlareText
import to.eyed.spettro.mobile.ui.components.elapsedLabel
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The workflow card: a phase tree in the transcript, not a list of agents.
 *
 * A workflow is the one kind of run whose shape was decided before it started
 * — the script declared its phases up front — so the card draws the whole plan
 * from the first frame and fills it in as agents land. That is the point being
 * defended here, and it is the same argument internal/tui/view_workflow.go
 * makes on the terminal side: a flat agent list turns a 3-phase, 20-agent run
 * into twenty indistinguishable lines, and worse, it can only ever show work
 * that has already happened. A phase nobody has reached yet is exactly the
 * information a reader wants, so it is drawn dimmed rather than omitted.
 *
 * The phone departs from the desktop card in one deliberate way: it opens
 * COLLAPSED. A desktop transcript is a wide column with room for a run to sit
 * open beside the conversation; a phone transcript is the whole screen, and a
 * card that opened expanded would push the answer the run produced off the
 * bottom of it. The collapsed line is therefore built to be worth reading on
 * its own — name, live badge, meter, ratio and clock while running; the CLI's
 * own conclusion once finished — rather than a truncation of the open one.
 *
 * The one exception is load-bearing. A run that FAILED opens expanded, because
 * the state a reader scrolls back to is the state that owes them the most, and
 * a failure folded behind a chevron is a failure the card hid. That is the bug
 * the desktop's settled-card rework was written to prevent, and honouring the
 * collapse default there would reintroduce it.
 *
 * Inside, a settled run drops successful *detail*, never *structure*. The
 * phase spine stays, each phase keeps its cell strip and its "3/3", every
 * failed member keeps its row AND gains the reason it failed, and the
 * successes — the least interesting rows on the card — fold into one "N done"
 * that expands.
 *
 * Two states are neither live nor over, and each says so in words rather than
 * with a spinner. A run *paused* at a checkpoint is waiting for Spettro to
 * answer a question it asked; a spinner there reads as hung. A card a later
 * turn *continued* is superseded by the card further down — it keeps the
 * state its turn left (usually paused), says "Continued below" instead of
 * waiting forever, and steps back to a hairline. A *stopped* run was ended on
 * purpose and says why, rather than passing for a success.
 */

/** How many member rows a single phase shows before it starts hiding them.
 *  Half the desktop's 12: a phone row is a third of the height of the screen's
 *  worth of list, and a phase that filled the viewport would bury the phases
 *  under it. */
private const val PHASE_ROW_CAP = 6

/** Log lines shown inline before the tail hides behind a disclosure. */
private const val LOG_INLINE_MAX = 3

/** The gutter the phase rail is drawn in. */
private val RAIL_WIDTH = 14.dp

@Composable
fun WorkflowCard(
    run: WorkflowRun,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val continued = run.continued
    val running = run.status == RunStatus.RUNNING && !continued
    val settled = !running
    // Finished, failed or stopped: nothing more will run. A paused run (and
    // the card a later turn continued) may still reach its remaining phases.
    val over = !continued &&
        (run.status == RunStatus.DONE || run.status == RunStatus.FAILED || run.status == RunStatus.STOPPED)
    // A failure is never folded away; everything else starts closed and keeps
    // whatever the reader last did to it. A continued card is history the card
    // below repeats in full, so it is the last thing that should open itself.
    var expanded by rememberSaveable(run.tool.id) {
        mutableStateOf(run.status == RunStatus.FAILED && !continued)
    }
    var logOpen by rememberSaveable(run.tool.id) { mutableStateOf(false) }
    var scriptOpen by rememberSaveable(run.tool.id) { mutableStateOf(false) }

    val elapsed = rememberElapsed(running, run.tool.timestamp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Ink and wash are separate: the first has to be readable at 12sp, the
    // second is a background and darkening it to clear a text ratio would only
    // make the card muddy. A run that is neither working nor over says so in
    // its colour too: amber while it waits, grey once it was stopped or handed
    // on — never the accent, which would read as still moving.
    val accent = when {
        continued -> muted
        run.status == RunStatus.FAILED -> colors.dangerInk
        run.status == RunStatus.RUNNING -> colors.accentInk
        run.status == RunStatus.PAUSED -> colors.warning
        run.status == RunStatus.STOPPED -> muted
        else -> colors.agentInk
    }
    val wash = when {
        continued -> muted
        run.status == RunStatus.FAILED -> colors.diffRemoved
        run.status == RunStatus.RUNNING -> colors.accent
        run.status == RunStatus.PAUSED -> colors.warning
        run.status == RunStatus.STOPPED -> muted
        else -> colors.agentAccent
    }
    val shape = RoundedCornerShape(Dimens.radiusMd)

    // The one-line form is a readout, not a truncation of the open one: while
    // running it says how far along and how long, and once settled it says
    // what the run is about now — the question a paused run asks, why a
    // stopped one stopped, what a finished one concluded.
    val tail = if (running) {
        when {
            run.counts.total > 0 && elapsed.isNotEmpty() -> elapsed
            run.counts.total > 0 -> ""
            elapsed.isEmpty() -> "Starting…"
            else -> elapsed
        }
    } else {
        runStateDetail(run)
    }

    val phases = run.phases.filter { it.title.isNotEmpty() || it.members.isNotEmpty() }
    val plan = planLabel(run)
    val state = runStateLine(run)
    // A run that died before any agent started has nothing to measure: an
    // empty grey bar there reads as "0% of something", so it goes.
    val metered = run.counts.total > 0 || running

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (continued) {
                    Color.Transparent
                } else {
                    wash.copy(alpha = if (colors.isDark) 0.06f else 0.045f)
                },
            )
            .border(Dimens.hairlineWidth, wash.copy(alpha = 0.25f), shape)
            .animateContentSize(transcriptSpring()),
    ) {
        // ---- header -------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                WorkflowMark(tint = accent.copy(alpha = if (continued) 0.7f else 1f), size = 14.dp)
                // Name and badge share one weighted slot, so a long name
                // ellipsizes beside its badge and the chevron stays pinned to
                // the right edge however long the name is.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    // The name shimmers only while the run is live — the same
                    // "this is moving" signal the reasoning header uses.
                    GlareText(
                        text = run.displayTitle,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = accent,
                        isActive = running,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    RunBadge(run = run)
                }
                DisclosureChevron(expanded = expanded, size = 13.dp)
            }

            if (metered || tail.isNotEmpty() || expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (metered) {
                        OrchProgressMeter(counts = run.counts, width = if (expanded) 96.dp else 72.dp)
                    }
                    if (run.counts.total > 0) {
                        OrchRatio(finished = run.counts.finished, total = run.counts.total)
                    }
                    if (expanded) {
                        OrchCountsLabel(counts = run.counts, modifier = Modifier.weight(1f))
                        if (running && elapsed.isNotEmpty()) ElapsedLabel(elapsed)
                    } else if (tail.isNotEmpty()) {
                        SummaryTail(
                            text = tail,
                            failedTerm = if (run.counts.failed > 0) {
                                "${run.counts.failed} failed"
                            } else {
                                ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // What the run was sized for — a tier and a token budget — said in
            // words once the card is open. It is how the user asked for the
            // run ("ultracode +500k"), so it is worth a line of its own.
            if (expanded && plan.isNotEmpty()) {
                Text(
                    text = plan,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
                    color = muted.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The strip belongs to the header rather than the body: it is the
            // one thing that has to survive the card being closed. A settled
            // run whose failures were only a red sliver in the meter is the
            // exact bug the desktop's rework was written to prevent.
            if (run.members.isNotEmpty()) {
                AgentCellStrip(members = run.members)
            }
        }

        // ---- body ---------------------------------------------------------
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.spacingMd,
                        end = Dimens.spacingMd,
                        bottom = Dimens.spacingSm,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                if (state.isNotEmpty()) StateLine(run = run, text = state)

                if (run.description.isNotEmpty()) {
                    Text(
                        text = run.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (phases.isEmpty()) {
                    Text(
                        text = if (running) "Waiting for the first agent…" else "No agents ran",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        phases.forEachIndexed { index, phase ->
                            PhaseGroup(
                                phase = phase,
                                compact = settled,
                                over = over,
                                isLast = index == phases.lastIndex,
                                runId = run.tool.id,
                            )
                        }
                    }
                }

                if (run.logs.isNotEmpty()) {
                    LogBlock(
                        logs = run.logs,
                        dropped = run.droppedLogLines,
                        compact = settled,
                        open = logOpen,
                        onToggle = { logOpen = !logOpen },
                    )
                }

                run.script?.let { script ->
                    if (script.source.isNotEmpty() || script.returned.isNotEmpty()) {
                        Disclosure(
                            label = "Script",
                            open = scriptOpen,
                            onToggle = { scriptOpen = !scriptOpen },
                        ) {
                            if (script.source.isNotEmpty()) {
                                OrchCodeBlock(text = script.source, maxLines = 24)
                            }
                            if (script.returned.isNotEmpty()) {
                                OrchSectionLabel("returned")
                                OrchCodeBlock(text = script.returned)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header pieces
// ---------------------------------------------------------------------------

/**
 * The status pill beside the name. A finished success gets no pill — the meter
 * and the counts already say it — only the green check a finished tool row
 * wears, so a run that worked looks as finished as one that failed looks
 * failed. Every other state is spelled out, so none of them depends on colour
 * alone.
 */
@Composable
private fun RunBadge(run: WorkflowRun) {
    val colors = LocalSpettroColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (run.continued) {
        OrchPill(text = "Continued below", tint = muted, icon = Icons.Outlined.ArrowDownward)
        return
    }
    when (run.status) {
        RunStatus.RUNNING -> Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpettroSpinner(size = 8.dp, color = colors.accentInk)
            Text(
                text = "Running",
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = colors.accentInk,
            )
        }
        RunStatus.PAUSED -> OrchPill(text = "Paused", tint = colors.warning, icon = Icons.Filled.PauseCircle)
        RunStatus.STOPPED -> OrchPill(text = "Stopped", tint = muted, icon = Icons.Filled.StopCircle)
        RunStatus.FAILED -> OrchPill(text = "Failed", tint = colors.dangerInk, icon = Icons.Filled.Cancel)
        RunStatus.DONE -> OrchStatusGlyph(status = OrchStatus.DONE, size = 13.dp)
    }
}

/**
 * The sentence an open card leads with when the run is neither simply working
 * nor simply finished: why it failed, what it waits for, why it stopped, where
 * it went. On a tinted ground of its own, because it is the one line on the
 * card that tells the reader what to do with it.
 */
@Composable
private fun StateLine(run: WorkflowRun, text: String) {
    val colors = LocalSpettroColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ground = when {
        run.continued -> Color.Transparent
        run.status == RunStatus.FAILED -> colors.diffRemoved.copy(alpha = 0.09f)
        run.status == RunStatus.PAUSED -> colors.warning.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val ink = if (run.continued || run.status == RunStatus.STOPPED) {
        muted
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(ground)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (run.continued) {
            Icon(
                imageVector = Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = muted.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp).size(12.dp),
            )
        } else {
            RunStatusGlyph(status = run.status, size = 12.dp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = ink,
            modifier = Modifier.weight(1f),
        )
        val checkpoint = run.pausedAt?.checkpointId.orEmpty()
        if (!run.continued && checkpoint.isNotEmpty()) {
            Text(
                text = "at $checkpoint",
                style = MonoSmall.copy(fontSize = 10.sp, lineHeight = 17.sp),
                color = muted.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/**
 * The settled run's conclusion — "6 agents · 1 failed · 1 replayed" — with only
 * the failure term in red.
 *
 * Tinting the whole line was worse than not tinting it at all: it painted the
 * agent count and the replay count as though they were failures too, and on a
 * live run it painted the clock. The failure is the part that has to catch the
 * eye, so it is the only part that does.
 */
@Composable
private fun SummaryTail(
    text: String,
    failedTerm: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(text, failedTerm) {
        buildAnnotatedString {
            val at = if (failedTerm.isEmpty()) -1 else text.indexOf(failedTerm)
            if (at < 0) {
                append(text)
                return@buildAnnotatedString
            }
            append(text.substring(0, at))
            withStyle(SpanStyle(color = colors.dangerInk, fontWeight = FontWeight.SemiBold)) {
                append(failedTerm)
            }
            append(text.substring(at + failedTerm.length))
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
        color = muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ElapsedLabel(elapsed: String) {
    Text(
        text = elapsed,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------------------
// One phase
// ---------------------------------------------------------------------------

/**
 * A phase header plus its members, hung off a vertical rail so the plan reads
 * as an ordered spine rather than a stack of unrelated boxes.
 *
 * The header carries the phase's own cell strip — one square per member — which
 * is what makes a wide phase legible on a phone without scrolling it: twelve
 * agents are twelve squares, and three of them being red is visible before a
 * single row is read.
 *
 * A phase opens expanded when it is running or has failures and closed when it
 * finished cleanly: what is moving and what broke are what the reader came for,
 * and a settled phase's roll of successes is the least interesting thing on the
 * card. A pending phase has nothing to open — and on a run that has ended it
 * says "Not run" rather than "Pending", which would promise work that is not
 * coming.
 */
@Composable
private fun PhaseGroup(
    phase: WorkflowPhase,
    compact: Boolean,
    over: Boolean,
    isLast: Boolean,
    runId: String,
) {
    val colors = LocalSpettroColors.current
    val state = phase.state
    val key = "$runId-${phase.title}"
    var open by rememberSaveable(key) {
        mutableStateOf(state == PhaseState.RUNNING || state == PhaseState.FAILED)
    }
    var showAll by rememberSaveable(key) { mutableStateOf(false) }

    val total = phase.members.size
    val quiet = phase.members.count { it.status == OrchStatus.DONE }

    val shown: List<MemberCall>
    val hidden: Int
    if (compact && !showAll) {
        shown = phase.members.filter { it.status != OrchStatus.DONE }
        hidden = quiet
    } else {
        val capped = capMembers(phase.members, if (showAll) total else PHASE_ROW_CAP)
        shown = capped.first
        hidden = capped.second
    }
    val toggleable = if (compact) quiet > 0 else hidden > 0 || (showAll && total > PHASE_ROW_CAP)
    val toggleLabel = when {
        showAll -> "show fewer"
        compact -> "$hidden done"
        else -> "… $hidden more"
    }

    val railTint = when (state) {
        PhaseState.RUNNING -> colors.accentMark
        PhaseState.FAILED -> colors.dangerMark
        PhaseState.DONE -> colors.successMark
        PhaseState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }

    // The rail is painted rather than laid out: a `weight(1f)` spacer inside a
    // Column whose height comes from a Row's content has no bounded height to
    // take a fraction of, so it collapses to nothing and the dots float
    // unconnected. Drawing it behind the row sidesteps the measure pass
    // entirely and costs one line per phase.
    val railX = with(LocalDensity.current) { RAIL_WIDTH.toPx() / 2f }
    val strokeWidth = with(LocalDensity.current) { Dimens.hairlineWidth.toPx() }
    val dotRadius = with(LocalDensity.current) { 3.dp.toPx() }
    val dotCenterY = with(LocalDensity.current) { 12.dp.toPx() }
    val hairline = colors.hairline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // The dot for this phase…
                drawCircle(color = railTint, radius = dotRadius, center = Offset(railX, dotCenterY))
                // …and the line running down to the next one, so an unreached
                // phase still reads as part of one plan.
                if (!isLast) {
                    drawLine(
                        color = hairline,
                        start = Offset(railX, dotCenterY + dotRadius + 2.dp.toPx()),
                        end = Offset(railX, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
            },
    ) {
        Box(Modifier.width(RAIL_WIDTH))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else Dimens.spacingXs)
                .animateContentSize(transcriptSpring()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .clickable(enabled = total > 0) { open = !open }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = phase.title.ifEmpty { "No phase" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (state == PhaseState.PENDING) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (phase.dynamic) OrchPill(text = "Added", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state == PhaseState.PENDING) {
                    Text(
                        text = if (over) "Not run" else "Pending",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Box(Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                    OrchRatio(finished = phase.counts.finished, total = total)
                    if (total > 0) DisclosureChevron(expanded = open, size = 12.dp)
                }
            }

            // The detail the script declared for this phase — the one-line
            // "what this phase is for" a reader has no other way to learn.
            if (phase.detail.isNotEmpty()) {
                Text(
                    text = phase.detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                )
            }

            if (phase.members.isNotEmpty()) {
                AgentCellStrip(
                    members = phase.members,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            if (open && (shown.isNotEmpty() || toggleable)) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    shown.forEach { member ->
                        MemberLine(member = member, runId = runId)
                    }
                    if (toggleable) {
                        QuietToggle(
                            label = toggleLabel,
                            open = showAll,
                            showChevron = compact,
                            onToggle = { showAll = !showAll },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One member, owning its own disclosure. Expansion state is keyed by the
 * member's tool id so it survives siblings arriving above and below it.
 */
@Composable
private fun MemberLine(member: MemberCall, runId: String) {
    var expanded by rememberSaveable("$runId-${member.tool.id}") { mutableStateOf(false) }
    MemberRow(
        member = member,
        expanded = expanded,
        onToggle = { expanded = !expanded },
    )
}

@Composable
private fun QuietToggle(
    label: String,
    open: Boolean,
    showChevron: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showChevron) DisclosureChevron(expanded = open, size = 11.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

// ---------------------------------------------------------------------------
// The script's log
// ---------------------------------------------------------------------------

/**
 * The `log()` lines the script emitted. They are the only narration a workflow
 * has — the CLI folds its progress traces into the lifecycle call and never
 * emits them as rows — so they are always reachable, but a chatty script must
 * not out-shout the phase tree it is narrating. Once the run is over the tree
 * is the record and the narration is not, so a settled card always folds it
 * away however short it is.
 */
@Composable
private fun LogBlock(
    logs: List<String>,
    dropped: Int,
    compact: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
) {
    // The CLI keeps only the last lines; saying how many went before keeps the
    // tail from passing for the whole log.
    val earlier = if (dropped > 0) "… $dropped earlier ${if (dropped == 1) "line" else "lines"}" else ""
    if (!compact && logs.size <= LOG_INLINE_MAX) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (earlier.isNotEmpty()) LogLine(earlier, faint = true)
            logs.forEach { LogLine(it) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisclosureChevron(expanded = open, size = 11.dp)
            Text(
                text = "Log (${logs.size + dropped})",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!open) {
                // The newest line, so a folded log still says where the script
                // got to.
                Text(
                    text = logs.last(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (open && earlier.isNotEmpty()) LogLine(earlier, faint = true)
        if (open) logs.forEach { LogLine(it) }
    }
}

@Composable
private fun LogLine(line: String, faint: Boolean = false) {
    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (faint) 0.6f else 0.85f),
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Generic disclosure
// ---------------------------------------------------------------------------

@Composable
internal fun Disclosure(
    label: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisclosureChevron(expanded = open, size = 11.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) content()
    }
}

// ---------------------------------------------------------------------------
// Derivations
// ---------------------------------------------------------------------------

/**
 * Ticking elapsed time while the run is live. The tool call's timestamp is set
 * once, when the call first appears, and never updated — so it is a true start
 * time. The clock only runs while the card needs it: a finished run has no end
 * timestamp to subtract from, and would be counting up forever.
 */
@Composable
internal fun rememberElapsed(active: Boolean, timestamp: String): String {
    val since = remember(timestamp) { epochMillisOrNull(timestamp) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, since) {
        if (!active || since == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (!active || since == null) return ""
    val seconds = (now - since) / 1_000
    if (seconds < 0 || seconds > 24 * 3600) return ""
    return elapsedLabel(now - since)
}

// MARK: Previews

@Preview(name = "Workflow live", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 400)
@Composable
private fun WorkflowCardLivePreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            WorkflowCard(run = OrchestrationPreviewData.liveWorkflow)
            WorkflowCard(run = OrchestrationPreviewData.pausedWorkflow)
        }
    }
}

@Preview(name = "Workflow settled", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 400)
@Composable
private fun WorkflowCardSettledPreview() {
    SpettroTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            WorkflowCard(
                run = OrchestrationPreviewData.workflowRun(
                    OrchestrationPreviewData.settledWorkflowRows,
                ),
            )
        }
    }
}
