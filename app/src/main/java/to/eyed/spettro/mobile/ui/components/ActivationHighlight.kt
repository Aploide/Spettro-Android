package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.core.workflowInputSpans
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Lighting up the phrase that changes what the next turn can do.
 *
 * "ultracode" — or "use a workflow to …" — silently arms multi-agent
 * orchestration, and a phrase that looks like every other phrase gives no sign
 * of that. The TUI answers this with an animated shimmer over the exact words
 * that matched (internal/tui/glow.go) and the desktop with the same idea in
 * CSS; this is the Compose version, driven by the same matcher the CLI uses so
 * the highlight can never promise a mode the run will not enter.
 *
 * A "+500k" budget directive lights the same way, but only when it would be
 * honoured — when workflows are on for the message (`budgetDirectivesLive`) —
 * so the highlight never promises a budget nobody enforces. Under the "Ask
 * first" permission level workflows do not run at all, so the phrase is still
 * marked — it is still what the user asked for — but muted: the field's quiet
 * ink, underlined, never the accent; the composer says why underneath
 * (`WorkflowHint`).
 *
 * One surface, deliberately: [activationHighlight], a [VisualTransformation]
 * for the live composer. Compose can style a text field's contents directly,
 * so unlike the web there is no mirrored copy to keep in lockstep; the offsets
 * are unchanged by the transform, so the identity mapping is exact and the
 * caret keeps landing where the user put it.
 *
 * A sent message gets nothing. Two reasons, and the second is the one that
 * settles it. The highlight earns its place in the input because the phrase is
 * still yours to change; in scrollback the run itself is the evidence and a
 * mark would be decoration. And it cannot be done well there anyway: the user's
 * bubble is white on a saturated accent, so there is no headroom above the
 * text, and every "highlight" available — a brightness sweep especially — can
 * only push those words *darker* than the prose around them. That does not
 * read as emphasis, it reads as damage. Marking by adding a ground (a
 * highlighter capsule) or a rule (an underline) would both work; doing nothing
 * works better.
 *
 * There is no animation anywhere in here. A gradient sweeping through a
 * sentence re-shades each word independently as it passes, so at any instant
 * one half of a matched phrase is brighter than the other — which looks like a
 * rendering fault rather than a highlight, and looks like one forever, because
 * nothing in scrollback ever stops moving.
 */

/**
 * Styles every activating phrase in the composer's text.
 *
 * @param tint the accent the phrase is drawn in; everything else keeps the
 *   field's own colour.
 * @param budgets light "+500k" directives too (they would be honoured).
 * @param muted workflows cannot run right now (Ask first): mark, but quietly.
 */
@Composable
fun activationHighlight(
    tint: Color = LocalSpettroColors.current.accent,
    budgets: Boolean = false,
    muted: Boolean = false,
): VisualTransformation {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(tint, budgets, muted, quiet) {
        val style = if (muted) {
            SpanStyle(color = quiet, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        } else {
            SpanStyle(color = tint, fontWeight = FontWeight.SemiBold)
        }
        VisualTransformation { text ->
            val spans = workflowInputSpans(text.text, budgets)
            if (spans.isEmpty()) {
                return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
            }
            val styled = AnnotatedString.Builder(text.text).apply {
                for (span in spans) addStyle(style, span.start, span.end)
            }.toAnnotatedString()
            // The transform only recolours: every character keeps its index, so
            // the identity mapping is exact and the caret cannot drift.
            TransformedText(styled, OffsetMapping.Identity)
        }
    }
}

/** Compose's identity [androidx.compose.ui.text.input.OffsetMapping], named so
 *  the transform above reads as "nothing moved". */
private typealias OffsetMapping = androidx.compose.ui.text.input.OffsetMapping

@Preview(name = "Activation highlight", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 360)
@Composable
private fun ActivationHighlightPreview() {
    SpettroTheme(darkTheme = true) {
        val transform = activationHighlight(budgets = true)
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // What the composer draws: the matched phrase in the accent, the
            // rest in the field's own colour. The third line must stay quiet.
            listOf(
                "ultracode +500k — review the changes on this branch",
                "I'm testing the workflow tool, so use workflows",
                "our deploy workflow is broken — check .github/workflows",
            ).forEach { line ->
                Text(
                    text = transform.filter(AnnotatedString(line)).text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
