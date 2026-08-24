package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.model.MemberCall
import to.eyed.spettro.mobile.model.OrchRun
import to.eyed.spettro.mobile.model.TranscriptRow
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Dispatches one row of the *folded* transcript.
 *
 * [TranscriptItemView] renders the flat wire: a message, a tool call. This
 * renders what `groupTranscript` made of it — a workflow run, an Ultra swarm, a
 * lone sub-agent, a workflow script that never started — and falls through to
 * [TranscriptItemView] for everything the fold left alone, which is the great
 * majority of a normal conversation.
 *
 * Keeping the two apart matters: the fold is the only thing that knows a run
 * exists, and a renderer that had to re-derive that per row would make a long
 * session quadratic.
 */
@Composable
fun TranscriptRowView(
    row: TranscriptRow,
    modifier: Modifier = Modifier,
) {
    when (row) {
        is TranscriptRow.Item -> TranscriptItemView(item = row.item, modifier = modifier)

        is TranscriptRow.Run -> when (val run = row.run) {
            is OrchRun.Workflow -> WorkflowCard(run = run, modifier = modifier)
            is OrchRun.Swarm -> SwarmCard(run = run, modifier = modifier)
        }

        // A delegation that belongs to no run — the plain `agent` tool. It gets
        // the same member row a swarm member does rather than the old bespoke
        // card, so one sub-agent and twenty read as the same kind of thing.
        is TranscriptRow.Agent -> {
            var expanded by rememberSaveable(row.member.tool.id) { mutableStateOf(false) }
            StandaloneAgentCard(
                member = row.member,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                modifier = modifier,
            )
        }

        is TranscriptRow.Script -> {
            var expanded by rememberSaveable(row.script.tool.id) { mutableStateOf(false) }
            ScriptCallRow(
                script = row.script,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                modifier = modifier,
            )
        }
    }
}

/**
 * A delegation that belongs to no run: the plain `agent` tool call.
 *
 * It keeps the agent-tinted card chrome the app has always given a sub-agent,
 * but its contents are a [MemberRow] — so a lone delegation shows the same
 * status glyph, the same live "what it is doing now" detail, and the same
 * reachable list of the tools it ran as a member of a twenty-agent swarm does.
 * Before the fold existed those nested calls were loose rows in the transcript
 * with no visible owner; now they belong to the agent that made them, and this
 * is the surface that has to show them.
 */
@Composable
private fun StandaloneAgentCard(
    member: MemberCall,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.agentAccent.copy(alpha = if (colors.isDark) 0.07f else 0.05f))
            .border(Dimens.hairlineWidth, colors.agentAccent.copy(alpha = 0.25f), shape)
            .padding(horizontal = Dimens.spacingSm, vertical = 5.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = colors.agentAccent,
                modifier = Modifier.size(13.dp),
            )
            OrchSectionLabel("agent")
        }
        MemberRow(member = member, expanded = expanded, onToggle = onToggle)
    }
}

// MARK: Previews

@Preview(name = "Folded transcript", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 400)
@Composable
private fun FoldedTranscriptPreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            OrchestrationPreviewData.mixedRows.forEach { TranscriptRowView(row = it) }
        }
    }
}

@Preview(name = "Failed script", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 400)
@Composable
private fun FailedScriptPreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            OrchestrationPreviewData.failedScriptRows.forEach { TranscriptRowView(row = it) }
        }
    }
}
