package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.model.MemberCall
import to.eyed.spettro.mobile.model.TranscriptRow
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Dispatches one row of the *folded* transcript.
 *
 * [TranscriptItemView] renders the flat wire: a message, a tool call. This
 * renders what `groupTranscript` made of it — a workflow run, a lone
 * sub-agent, a workflow script that never started — and falls through to
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

        is TranscriptRow.Run -> WorkflowCard(run = row.run, modifier = modifier)

        // A delegation that belongs to no run — the plain `agent` tool. It gets
        // the same member row a workflow member does rather than the old
        // bespoke card, so one sub-agent and twenty read as the same kind of
        // thing.
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
 * A delegation that belongs to no run: the plain `agent` tool call, as the
 * desktop's sub-agent card — the straight rail, then [AgentCardHeader]
 * (status, "Agent · name", how many tools it ran, the task).
 *
 * Opened, it lists the tools the agent ran — before the fold existed those
 * were loose rows in the transcript with no visible owner — then its report,
 * and a failure says why without being opened.
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
    val summary = member.resultText
    val hasBody = member.children.isNotEmpty() || summary.isNotEmpty()
    val reason = member.failureReason
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            // The desktop's sub-agent rail: a straight 2dp line in the agent
            // accent, inset from the card's top and bottom.
            .drawBehind {
                val inset = 10.dp.toPx()
                drawRect(
                    colors.agentAccent,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, inset),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), (size.height - inset * 2).coerceAtLeast(0f)),
                )
            }
            .animateContentSize(transcriptSpring()),
    ) {
        AgentCardHeader(
            agent = member.instance,
            task = member.task,
            expanded = expanded,
            hasBody = hasBody,
            onToggle = onToggle,
            toolCount = member.children.size,
            glyph = { OrchStatusGlyph(status = member.status, size = 12.dp) },
        )
        if (reason.isNotEmpty()) {
            Text(
                text = reason.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 34.dp, end = Dimens.spacingMd, bottom = 8.dp),
            )
        }
        if (expanded && hasBody) {
            HairlineDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                member.children.forEach { ToolCallRow(tool = it) }
                if (summary.isNotEmpty()) {
                    if (member.resultIsJSON) OrchCodeBlock(text = summary) else MarkdownText(text = summary)
                }
            }
        }
    }
}

// MARK: Previews

@Preview(name = "Folded transcript", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 400)
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

@Preview(name = "Failed script", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 400)
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
