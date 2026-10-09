package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.core.workflowInputSpans
import to.eyed.spettro.mobile.core.workflowRequested
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
 * The phrase is drawn in the desktop's glow ramp (`--glow-ramp`: violet →
 * magenta → cyan, or a sweep of greys in Monochrome), at weight 600. Every
 * stop passes AA on the composer and on the user bubble, both neutral greys,
 * so a word stays readable whichever hue lands on it.
 *
 * Two surfaces: [activationHighlight], a [VisualTransformation] for the live
 * composer — Compose styles a text field's contents directly, so there is no
 * mirrored copy to keep in lockstep, and the offsets are unchanged so the
 * caret keeps landing where the user put it — and [activationAnnotated] for a
 * sent message, where the phrase stays lit: what armed the turn should still
 * be visible in the turn it armed. (It used to get nothing, because the bubble
 * was white on a saturated accent with no headroom for a highlight; the
 * bubble is neutral now, like the desktop's.)
 *
 * The ramp holds still. The desktop drifts it once every 7s; here a gradient
 * moving through a sentence re-shades each word independently as it passes,
 * which reads as a rendering fault on a phone held at reading distance.
 */

/** The ramp tiles every [RAMP_SPAN_DP], mirrored, so a short phrase still shows several hues. */
private const val RAMP_SPAN_DP = 140f

@Composable
private fun rampStyle(muted: Boolean): SpanStyle {
    val colors = LocalSpettroColors.current
    val span = with(LocalDensity.current) { RAMP_SPAN_DP.dp.toPx() }
    return remember(colors, muted, span) {
        if (muted) {
            SpanStyle(color = colors.textSecondary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        } else {
            SpanStyle(
                brush = Brush.linearGradient(
                    colors = colors.glowRamp,
                    start = Offset.Zero,
                    end = Offset(span, span * 0.18f),
                    tileMode = TileMode.Mirror,
                ),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Styles every activating phrase in the composer's text.
 *
 * @param budgets light "+500k" directives too (they would be honoured).
 * @param muted workflows cannot run right now (Ask first): mark, but quietly.
 */
@Composable
fun activationHighlight(
    budgets: Boolean = false,
    muted: Boolean = false,
): VisualTransformation {
    val style = rampStyle(muted)
    return remember(style, budgets) {
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

/**
 * A sent message with its activating phrase lit. Budget directives light
 * only alongside a phrase that asked for a workflow, as on the desktop.
 */
@Composable
fun activationAnnotated(text: String): AnnotatedString {
    val style = rampStyle(muted = false)
    return remember(text, style) {
        val spans = workflowInputSpans(text, budgets = workflowRequested(text))
        if (spans.isEmpty()) return@remember AnnotatedString(text)
        AnnotatedString.Builder(text).apply {
            for (span in spans) addStyle(style, span.start, span.end)
        }.toAnnotatedString()
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
