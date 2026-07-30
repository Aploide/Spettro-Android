package to.eyed.spettro.mobile.ui.screens.sheets

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
 * Preformatted content on a raised surface with a hairline stroke: the
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
        color = colors.surfaceRaised,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(Dimens.spacingSm),
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
    val accent = LocalSpettroColors.current.accent
    Text(
        text = "Recommended",
        modifier = modifier
            .background(accent.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = accent,
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
    )
}

/**
 * A tappable card in the option lists: the [SpettroCard] look (raised fill,
 * hairline stroke, no shadow) that swaps its stroke for the accent while
 * selected.
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
        shape = RoundedCornerShape(Dimens.radiusSm),
        color = colors.surfaceRaised,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.7f))
        } else {
            BorderStroke(Dimens.hairlineWidth, colors.hairline)
        },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}
