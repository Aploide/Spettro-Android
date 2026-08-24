package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.model.MemberCall
import to.eyed.spettro.mobile.model.OrchRun
import to.eyed.spettro.mobile.model.OrchStatus
import to.eyed.spettro.mobile.ui.components.GlareText
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * The Ultra fan-out, in the transcript, as one object instead of twenty rows.
 *
 * This is the phone port of the block internal/tui/view_swarm.go argues for,
 * and it exists to fix the same two failures. First, a swarm member used to
 * look exactly like an ordinary sub-agent, so a fan-out of twenty dissolved
 * into a wall of identical purple cards and the conversation around it
 * disappeared. Second, only the running members were ever drawn, so the list
 * *shrank* as the swarm made progress — the moment it mattered most, the card
 * said least, and a finished swarm left no trace of what it had actually done.
 *
 * So: one header owns the whole run (cell strip, meter, counts, isolation), and
 * every member stays accounted for from launch to finish. A member shows what
 * it is doing RIGHT NOW rather than the item it was handed, because in a
 * fan-out the items are near-identical by construction and tell you nothing
 * about progress — [MemberRow] implements that rule for every surface.
 *
 * The desktop renders peers as a responsive grid, which a 426dp phone has no
 * room for. The strip replaces it and does the same job better here: one small
 * square per member in dispatch order, wrapping, with the work Ultra's launch
 * ramp has not reached yet drawn as empty cells. Twenty members are two lines
 * of squares you take in at a glance, and three reds among them are visible
 * without opening anything — which is the whole point, because this card opens
 * collapsed.
 *
 * That collapse is the one thing to be careful about. "A finished swarm showing
 * nothing" is precisely the bug view_swarm.go was written to prevent, so the
 * collapsed header is not a truncation: it carries the strip, the meter, the
 * counts with the failures in red, and — when anything broke — the failed rows
 * themselves. What worked is behind a tap; what broke never is.
 */
@Composable
fun SwarmCard(
    run: OrchRun.Swarm,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val running = run.status == OrchStatus.RUNNING
    val settled = !running
    var expanded by rememberSaveable(run.tool.id) { mutableStateOf(false) }
    var showDone by rememberSaveable(run.tool.id) { mutableStateOf(false) }

    // Two colours, deliberately. `accent` is ink — it has to be readable at
    // 12sp — while the card's fill and border are a wash of the *raw* brand
    // hue: a background is not text, darkening it to clear a text ratio only
    // makes the card muddy without helping anyone read anything.
    val accent = colors.agentInk
    val wash = colors.agentAccent
    val shape = RoundedCornerShape(Dimens.radiusMd)

    val members = orderMembers(run.members)
    val failed = members.filter { it.status == OrchStatus.FAILED }
    // Once the run is over the successes are twenty near-identical lines saying
    // the same thing; they fold into one "N done" the reader can open.
    val quiet = if (settled) members.count { it.status == OrchStatus.DONE } else 0
    val visible = if (quiet > 0 && !showDone) {
        members.filter { it.status != OrchStatus.DONE }
    } else {
        members
    }
    val empty = run.members.isEmpty() && run.pending.isEmpty()
    val note = if (empty) run.tool.output.trim() else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(wash.copy(alpha = if (colors.isDark) 0.07f else 0.05f))
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
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
                GlareText(
                    text = "Ultra swarm",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = accent,
                    isActive = running,
                )
                if (run.subagentType.isNotEmpty()) {
                    Text(
                        text = "· ${run.subagentType}",
                        style = MaterialTheme.typography.labelMedium,
                        color = memberTint(run.subagentType),
                        maxLines = 1,
                    )
                }
                Box(Modifier.weight(1f))
                OrchStatusGlyph(status = run.status, size = 13.dp)
                DisclosureChevron(expanded = expanded, size = 13.dp)
            }

            // Isolation is invisible in the output but changes what the swarm
            // may do to your checkout, so it gets a badge rather than a word
            // buried in the description.
            if (run.isolation == "worktree") {
                OrchPill(
                    text = "worktree isolation",
                    tint = colors.accentInk,
                    icon = Icons.Outlined.CallSplit,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OrchProgressMeter(counts = run.counts, width = 72.dp)
                OrchRatio(finished = run.counts.finished, total = run.counts.total)
                OrchCountsLabel(counts = run.counts, modifier = Modifier.weight(1f))
                if (run.pending.isNotEmpty()) {
                    Text(
                        text = "${run.pending.size} queued",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFeatureSettings = TabularNums,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }

            // The strip lives in the header rather than the body: it is the
            // one thing that has to survive the card being closed.
            if (members.isNotEmpty() || run.pending.isNotEmpty()) {
                AgentCellStrip(members = members, pending = run.pending.size)
            }
        }

        // ---- failures, always ---------------------------------------------
        // A collapsed card still owes the reader whatever broke.
        if (!expanded && failed.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.spacingMd,
                        end = Dimens.spacingMd,
                        bottom = Dimens.spacingSm,
                    ),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                failed.take(FAILED_PEEK).forEach { member ->
                    MemberLine(member = member, runId = run.tool.id)
                }
                if (failed.size > FAILED_PEEK) {
                    Text(
                        text = "… ${failed.size - FAILED_PEEK} more failed",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.dangerInk.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
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
                if (note.isNotEmpty()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                }

                if (visible.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        visible.forEach { member ->
                            MemberLine(member = member, runId = run.tool.id)
                        }
                    }
                }

                if (quiet > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                            .clickable { showDone = !showDone }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DisclosureChevron(expanded = showDone, size = 11.dp)
                        Text(
                            text = if (showDone) {
                                "hide the members that succeeded"
                            } else {
                                "$quiet done"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }

                if (run.pending.isNotEmpty()) {
                    QueuedItems(items = run.pending)
                }
            }
        }
    }
}

/** How many failures a closed card spells out before it starts counting them. */
private const val FAILED_PEEK = 3

/** How many queued items are listed before the rest become a count. */
private const val QUEUED_PEEK = 5

@Composable
private fun MemberLine(member: MemberCall, runId: String) {
    var expanded by rememberSaveable("$runId-${member.tool.id}") { mutableStateOf(false) }
    MemberRow(
        member = member,
        expanded = expanded,
        onToggle = { expanded = !expanded },
    )
}

/**
 * The items Ultra has accepted but not yet dispatched. Ultra ramps its launches
 * (five at once, then one every 700ms), so a twenty-item swarm spends its first
 * seconds with most members not yet born. They are already counted in the
 * meter's denominator and drawn as empty cells in the strip; this is the same
 * work spelled out, for a reader who wants to know *what* is still queued.
 */
@Composable
private fun QueuedItems(items: List<String>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        OrchSectionLabel("queued", modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        items.take(QUEUED_PEEK).forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(muted.copy(alpha = 0.35f)),
                )
                Text(
                    text = item.replace("\n", " "),
                    style = MonoSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = muted.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (items.size > QUEUED_PEEK) {
            Text(
                text = "… ${items.size - QUEUED_PEEK} more queued",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = muted.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

/**
 * Running members first, dispatch order kept inside each group — the strip's
 * form of prioritiseRunning() in view_swarm.go. What is still moving is the
 * only part of a swarm you can act on; the finished half is a record. Once
 * nothing is moving, failures lead for the same reason: they are the rows the
 * reader came back for. `sortedBy` is stable, so launch order survives inside
 * every group for free.
 */
private fun orderMembers(members: List<MemberCall>): List<MemberCall> =
    members.sortedBy { member ->
        when (member.status) {
            OrchStatus.RUNNING -> 0
            OrchStatus.FAILED -> 1
            OrchStatus.DONE -> 2
        }
    }

// MARK: Previews

@Preview(name = "Swarm live", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 400)
@Composable
private fun SwarmCardLivePreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            SwarmCard(run = OrchestrationPreviewData.liveSwarm)
        }
    }
}

@Preview(name = "Swarm settled", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 400)
@Composable
private fun SwarmCardSettledPreview() {
    SpettroTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            SwarmCard(
                run = OrchestrationPreviewData.swarmRun(
                    OrchestrationPreviewData.settledSwarmRows,
                ),
            )
        }
    }
}
