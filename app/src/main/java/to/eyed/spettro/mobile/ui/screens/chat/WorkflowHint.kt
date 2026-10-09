package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.core.budgetDirectivesLive
import to.eyed.spettro.mobile.core.compactTokens
import to.eyed.spettro.mobile.core.parseBudgetDirective
import to.eyed.spettro.mobile.core.workflowRequested
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The composer's word on a message that asks for workflows. Port of the
 * desktop's `WorkflowHint` and the gate in `Composer.tsx`.
 *
 * Under the "Ask first" permission level the CLI refuses every workflow
 * (internal/agent/workflow.go), so a phrase lit like any other would promise a
 * run that cannot happen: the phrase is still marked — it is still what the
 * user asked for — but quietly, and the line under the field says why and
 * offers the way out. A "+500k" directive lights only when it would be
 * honoured, and the line says what budget it sets.
 */

/**
 * What the chat's options say about workflows: whether Ultra is on, whether
 * "Ask first" is holding every workflow back, and whether the permission
 * option offers the level that lifts it.
 */
data class WorkflowGate(
    val ultraOn: Boolean = false,
    val askFirst: Boolean = false,
    val canRestrict: Boolean = false,
)

/** Reads [WorkflowGate] off a chat's config options. */
fun workflowGate(options: List<AcpConfigOption>): WorkflowGate {
    val ultra = options.firstOrNull { it.id == "ultra" }?.kind as? AcpConfigOption.Kind.Bool
    val permission = options.firstOrNull { it.id == "permission" }?.kind as? AcpConfigOption.Kind.Select
    return WorkflowGate(
        ultraOn = ultra?.current == true,
        askFirst = permission?.current == ASK_FIRST,
        canRestrict = permission?.groups?.any { group -> group.options.any { it.value == RESTRICTED } } == true,
    )
}

/** The permission values the gate turns on (internal/acp config_options.go). */
internal const val ASK_FIRST = "ask-first"
internal const val RESTRICTED = "restricted"

/** How the composer lights a draft, and the line it puts under it. */
data class WorkflowDraft(
    /** "+500k" directives light: they would be honoured. */
    val budgets: Boolean,
    /** Workflows cannot run right now (Ask first): mark, but quietly. */
    val muted: Boolean,
    /** The draft asks for workflows that Ask first will refuse. */
    val pausedByAskFirst: Boolean,
    /** The budget the draft's directive sets, when it would be honoured. */
    val budgetTokens: Long?,
)

/**
 * The composer's reading of [draft] under [gate]. Ultra suspended by Ask
 * first honours no directive, so the budget is then looked for directly: a
 * "+500k" aimed at the standing Ultra must still say workflows are paused
 * rather than stay silent.
 */
fun workflowDraft(draft: String, gate: WorkflowGate): WorkflowDraft {
    val budgets = budgetDirectivesLive(draft, gate.ultraOn && !gate.askFirst)
    val paused = gate.askFirst &&
        (workflowRequested(draft) || (gate.ultraOn && parseBudgetDirective(draft) != null))
    return WorkflowDraft(
        budgets = budgets,
        muted = gate.askFirst,
        pausedByAskFirst = paused,
        budgetTokens = if (budgets) parseBudgetDirective(draft) else null,
    )
}

/**
 * The line under the composer that explains a lit phrase, when there is
 * something to explain: workflows are off under Ask first (with the one-tap
 * way out, when the permission option offers it), or a budget directive will
 * cap the run's tokens. Renders nothing otherwise. Small and quiet: it
 * annotates the field, it is not a banner.
 */
@Composable
fun WorkflowHint(
    draft: WorkflowDraft,
    onSwitchPermission: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 22.dp)
        .padding(horizontal = Dimens.spacingLg)
        .semantics { liveRegion = LiveRegionMode.Polite }
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)

    if (draft.pausedByAskFirst) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PauseCircle,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "Workflows are paused under Ask first — switch permission to run them",
                style = textStyle,
                color = secondary,
                modifier = Modifier.weight(1f),
            )
            if (onSwitchPermission != null) {
                Text(
                    text = "Switch to Restricted",
                    style = textStyle.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(Dimens.hairlineWidth, colors.hairline, CircleShape)
                        .background(colors.surfaceRaised)
                        .clickable(onClick = onSwitchPermission)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        return
    }

    val budget = draft.budgetTokens ?: return
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkflowMark(tint = secondary.copy(alpha = 0.7f), size = 12.dp)
        Text(
            text = buildAnnotatedString {
                append("Workflows in this message share a budget of ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) {
                    append("${compactTokens(budget)} tokens")
                }
            },
            style = textStyle,
            color = secondary,
        )
    }
}

// MARK: Previews

@Preview(name = "Workflow hints", showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 380)
@Composable
private fun WorkflowHintPreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(vertical = Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            WorkflowHint(
                draft = workflowDraft("ultracode +500k: review", WorkflowGate()),
                onSwitchPermission = null,
            )
            WorkflowHint(
                draft = workflowDraft("ultracode: review", WorkflowGate(askFirst = true, canRestrict = true)),
                onSwitchPermission = {},
            )
        }
    }
}
