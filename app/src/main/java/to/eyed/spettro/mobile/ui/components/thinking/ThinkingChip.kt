package to.eyed.spettro.mobile.ui.components.thinking

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Deferred
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.model.ThinkingStop
import to.eyed.spettro.mobile.ui.components.SpettroSheet
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroMotion

/**
 * What the thinking chip and its slider need from the chat: its options, the
 * model's capability, and the ways a move goes out.
 */
class ThinkingControl(
    val options: List<AcpConfigOption>,
    /** Whether the model reasons ([Thinking.modelReasons]); false makes the slider inert. */
    val reasons: Boolean?,
    /** Queues a move (ThinkingCommitter); completes true once the queue has drained. */
    val onCommit: (ThinkingStop) -> Deferred<Boolean>,
    val onSwitchToRestricted: () -> Unit,
    val onTurnOffUltra: () -> Unit,
)

/**
 * Harness only: a frozen instant of the slider — a meteor [meteorProgress]
 * of the way through its run from [meteorFrom], or lit Ultra's smoulder
 * [idleTime] seconds in.
 */
data class ThinkingFrame(
    val meteorProgress: Double? = null,
    val meteorFrom: Int? = null,
    val idleTime: Double? = null,
)

/**
 * The current level as a toolbar chip ("High", "Ultra"), opening the slider.
 * Port of the desktop's ThinkingChip, where its ConfigBar puts it, beside
 * the mode: the phone's stand-in for the desktop's up-popover is a sheet.
 * Lit Ultra wears the slider's fire; paused Ultra is muted with a pause mark
 * and a dashed edge. While the slider moves, the chip names where it is
 * going — "Ultra" at once, never the CLI's halfway "High" — and falls back
 * to the options once the slider closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingChip(
    control: ThinkingControl,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
    frame: ThinkingFrame? = null,
) {
    val base = remember(control.options) { Thinking.state(control.options) } ?: return
    val colors = LocalSpettroColors.current
    var open by rememberSaveable { mutableStateOf(startOpen) }
    // The open slider's move in progress: the chip names where it is going.
    var preview by remember { mutableStateOf<ThinkingStop?>(null) }
    val state = preview?.let { Thinking.previewState(base, it) } ?: base
    val lit = state.lit
    val inert = control.reasons == false

    val shape = RoundedCornerShape(Dimens.radiusSm)
    val ink = when {
        lit -> colors.ultraTextFlame
        open && !state.paused && !inert -> colors.textPrimary
        else -> colors.textSecondary
    }
    val background = when {
        lit -> colors.ultraFlame.copy(alpha = if (open) 0.16f else 0.10f)
        open -> colors.hoverFill
        else -> Color.Transparent
    }
    val label = Thinking.chipLabel(state)
    val title = Thinking.chipTitle(state)

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(background)
            .then(
                when {
                    lit -> Modifier.border(Dimens.hairlineWidth, colors.ultraFlame.copy(alpha = 0.3f), shape)
                    state.paused -> Modifier.drawBehind {
                        val stroke = Dimens.hairlineWidth.toPx()
                        drawRoundRect(
                            color = colors.hairline,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = size.copy(width = size.width - stroke, height = size.height - stroke),
                            cornerRadius = CornerRadius(Dimens.radiusSm.toPx()),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                            ),
                        )
                    }
                    else -> Modifier
                },
            )
            .clickable(enabled = enabled) { open = !open }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = title
                onClick(label = "Change how hard the model thinks") {
                    if (enabled) open = !open
                    enabled
                }
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(
            when {
                state.paused -> ChipGlyph.Paused
                lit -> ChipGlyph.Flame
                else -> ChipGlyph.Brain
            },
            tint = ink,
            knockout = colors.canvas,
            size = 13.dp,
        )
        Text(
            text = buildAnnotatedString {
                if (lit) {
                    withStyle(
                        SpanStyle(
                            brush = Brush.horizontalGradient(listOf(colors.ultraTextEmber, colors.ultraTextFlame)),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) { append(label) }
                } else {
                    append(label)
                }
                if (state.paused) {
                    withStyle(SpanStyle(color = colors.textSecondary, fontWeight = FontWeight.Normal)) { append(" · Paused") }
                }
            },
            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Glyph(ChipGlyph.Chevron, tint = ink.copy(alpha = ink.alpha * 0.6f), knockout = colors.canvas, size = 9.dp)
    }

    if (open) {
        SpettroSheet(onDismissRequest = { open = false }) {
            // Full width, so the size animation's clip (when the Paused
            // prompt comes and goes) is the sheet's edge: the Off thumb and
            // lit Ultra's glow hang past the slider's own box.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(SpettroMotion.MODAL_MS))
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            ) {
                ThinkingSlider(
                    options = control.options,
                    reasons = control.reasons,
                    onCommit = control.onCommit,
                    onSwitchToRestricted = control.onSwitchToRestricted,
                    onTurnOffUltra = control.onTurnOffUltra,
                    autoFocus = true,
                    onPreview = { preview = it },
                    meteorProgress = frame?.meteorProgress,
                    meteorFrom = frame?.meteorFrom,
                    idleTime = frame?.idleTime,
                )
            }
        }
    }
}

private enum class ChipGlyph { Brain, Flame, Paused, Chevron }

// The desktop's icons (design/icons.tsx), drawn on its 16-unit grid: strokes
// of 1.5 with round caps and joins, the chevron at 2.4.
private val BrainPaths = listOf(
    "M8 2.5a3 3 0 0 0-3 3 2.5 2.5 0 0 0-1.4 4.4A2.5 2.5 0 0 0 6 13.5c.8 0 1.5-.35 2-.9.5.55 1.2.9 2 .9a2.5 2.5 0 0 0 2.4-3.6A2.5 2.5 0 0 0 11 5.5a3 3 0 0 0-3-3z",
    "M8 3.5v9",
).map { PathParser().parsePathString(it).toPath() }

private val FlamePaths = listOf(
    "M8 1.5c.5 2.4 4.3 4.2 4.3 8.2A4.3 4.3 0 0 1 8 14.5a4.3 4.3 0 0 1-4.3-4.8c.1-1.6.9-2.8 1.8-3.5.1 1.3.6 2.2 1.5 2.6C6.9 6.3 7.2 3.8 8 1.5z",
    "M8 14.5c-1.2 0-2-.9-2-2.1 0-1.4 1.1-2.1 2-3.4.9 1.3 2 2 2 3.4 0 1.2-.8 2.1-2 2.1z",
).map { PathParser().parsePathString(it).toPath() }

private val PauseBars = PathParser().parsePathString("M6.4 5.4v5.2M9.6 5.4v5.2").toPath()

private val ChevronPath = PathParser().parsePathString("m3 6 5 5 5-5").toPath()

@Composable
private fun Glyph(glyph: ChipGlyph, tint: Color, knockout: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.width / 16f
        scale(unit, unit, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (glyph) {
                ChipGlyph.Brain -> BrainPaths.forEach { drawPath(it, tint, style = stroke) }
                ChipGlyph.Flame -> FlamePaths.forEach { drawPath(it, tint, style = stroke) }
                ChipGlyph.Paused -> {
                    drawCircle(tint, radius = 7f, center = Offset(8f, 8f))
                    drawPath(PauseBars, knockout, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
                }
                ChipGlyph.Chevron -> drawPath(
                    ChevronPath,
                    tint,
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
