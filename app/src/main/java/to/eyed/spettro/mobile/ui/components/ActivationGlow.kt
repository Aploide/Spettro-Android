package to.eyed.spettro.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.core.splitOnActivation
import to.eyed.spettro.mobile.core.workflowActivationSpans
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
 * Two surfaces, one look:
 *
 *  * [activationHighlight] — a [VisualTransformation] for the live composer.
 *    Compose can style a text field's contents directly, so unlike the web
 *    there is no mirrored copy to keep in lockstep; the offsets are unchanged
 *    by the transform, so the identity mapping is correct and the cursor keeps
 *    landing where the user put it.
 *  * [ActivationText] — read-only prose, for a message already sent.
 *
 * The shimmer is deliberately absent from the composer. A gradient that sweeps
 * while you type fights the caret for attention and re-shades text under the
 * user's finger; the input gets weight and the accent, and the moving highlight
 * is saved for the sent message, where nothing else is competing.
 */

/** Seconds for the highlight to cross the phrase once. Matches [GlareText]. */
private const val SWEEP_MILLIS = 1800

/**
 * Styles every activating phrase in the composer's text.
 *
 * @param tint the accent the phrase is drawn in; everything else keeps the
 *   field's own colour.
 */
@Composable
fun activationHighlight(
    tint: Color = LocalSpettroColors.current.accent,
): VisualTransformation {
    return remember(tint) {
        VisualTransformation { text ->
            val spans = workflowActivationSpans(text.text)
            if (spans.isEmpty()) {
                return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
            }
            val styled = AnnotatedString.Builder(text.text).apply {
                for (span in spans) {
                    addStyle(
                        SpanStyle(color = tint, fontWeight = FontWeight.SemiBold),
                        span.start,
                        span.end,
                    )
                }
            }.toAnnotatedString()
            // The transform only recolours: every character keeps its index, so
            // the identity mapping is exact and the caret cannot drift.
            TransformedText(styled, OffsetMapping.Identity)
        }
    }
}

/** The matched phrases lit and sweeping; everything else plain prose. */
@Composable
fun ActivationText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    tint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val pieces = remember(text) { splitOnActivation(text) }
    if (pieces.none { it.active }) {
        Text(text = text, modifier = modifier, style = style, color = color)
        return
    }

    val transition = rememberInfiniteTransition(label = "activation")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWEEP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "activationPhase",
    )
    val highlight = lerp(tint, Color.White, 0.85f)

    // One brush across the whole run rather than per piece: the sweep has to
    // read as a single light travelling over the sentence, and a per-span brush
    // would restart it inside every matched phrase.
    val annotated = remember(pieces, phase, tint, color) {
        buildAnnotatedString {
            for (piece in pieces) {
                if (piece.active) {
                    withStyle(
                        SpanStyle(
                            brush = Brush.linearGradient(
                                colors = listOf(tint, highlight, tint),
                                start = Offset(phase * 1200f - 300f, 0f),
                                end = Offset(phase * 1200f + 300f, 0f),
                            ),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(piece.text)
                    }
                } else {
                    // The prose around a match is held slightly back so the lit
                    // phrase reads as lit in a still frame too — on the user's
                    // bubble both are white, and weight alone is a weak signal
                    // once the sweep is off screen.
                    withStyle(SpanStyle(color = color.copy(alpha = 0.82f))) {
                        append(piece.text)
                    }
                }
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style)
}

/** Compose's identity [androidx.compose.ui.text.input.OffsetMapping], named so
 *  the transform above reads as "nothing moved". */
private typealias OffsetMapping = androidx.compose.ui.text.input.OffsetMapping

@Preview(name = "Activation glow", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 360)
@Composable
private fun ActivationGlowPreview() {
    SpettroTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActivationText(
                text = "ultracode — review the changes on this branch",
                color = MaterialTheme.colorScheme.onSurface,
                tint = LocalSpettroColors.current.accent,
            )
            ActivationText(
                text = "use a workflow to port the settings screen",
                color = MaterialTheme.colorScheme.onSurface,
                tint = LocalSpettroColors.current.accent,
            )
            ActivationText(
                text = "our deploy workflow is broken",
                color = MaterialTheme.colorScheme.onSurface,
                tint = LocalSpettroColors.current.accent,
            )
        }
    }
}
