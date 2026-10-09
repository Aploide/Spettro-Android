package to.eyed.spettro.mobile.ui.screens.sheets

import androidx.compose.ui.Alignment
import to.eyed.spettro.mobile.ui.components.PillTone
import to.eyed.spettro.mobile.ui.components.Pill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors

/**
 * Small shared pieces for the approval and question sheets — the app's
 * highest-frequency surfaces, so they share one visual vocabulary.
 */

/**
 * Preformatted content on the sunken surface with a hairline stroke: the
 * command a permission request wants to run, an option's preview. No wrap —
 * long lines scroll horizontally, exactly like the TUI renders them.
 */
@Composable
internal fun MonoBlock(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LocalSpettroColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.radiusSm),
        color = colors.surfaceSunken,
        contentColor = colors.textPrimary,
        border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.4,
                    ),
                    softWrap = false,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The "Recommended" marker on the option the agent would pick. A tag, never a
 * preselection — an answer the agent chose and reported as a human's would be
 * a lie about what was said.
 */
@Composable
internal fun RecommendedTag(modifier: Modifier = Modifier) {
    Pill("Recommended", modifier = modifier, tone = PillTone.Success)
}

/** How long a prompt's answers stay inert after it appears (the desktop's ARM_DELAY_MS). */
internal const val PROMPT_ARM_DELAY_MS = 600L

/**
 * The prompt card's header tile: 24dp, 7dp corners, the icon in amber on
 * amber at 14% — something is waiting on you. [info] is the accent variant.
 */
@Composable
internal fun PromptIconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, info: Boolean = false) {
    val colors = LocalSpettroColors.current
    val tint = if (info) colors.accentText else colors.warning
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .size(24.dp)
            .background(tint.copy(alpha = if (info) 0.12f else 0.14f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Swallows taps, for an answer that isn't armed yet. */
internal fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * A tappable option in the lists (the desktop's prompt option): a hairline
 * on the raised surface with 10dp corners; selected, the border turns accent
 * at 55% over an accent 7% wash.
 */
@Composable
internal fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.radiusOption),
        color = if (selected) colors.accent.copy(alpha = 0.07f).compositeOver(colors.surfaceRaised) else colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = if (selected) {
            BorderStroke(1.dp, colors.accent.copy(alpha = 0.55f))
        } else {
            BorderStroke(Dimens.hairlineWidth, colors.hairline)
        },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}
