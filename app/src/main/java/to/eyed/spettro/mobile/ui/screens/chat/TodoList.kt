package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.model.Todos
import to.eyed.spettro.mobile.ui.components.Pill
import to.eyed.spettro.mobile.ui.components.Shimmer
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/** How far the todo tab slides under the composer card (the desktop's −6px). */
internal val TodoTuck = 6.dp

/** The open task list's cap, the desktop's 188px, with its head on top. */
internal val TabMaxHeight = 224.dp
private val TodoHeadHeight = 36.dp

/**
 * The agent's task list, pinned above the composer as a tab that slides
 * under its card — the desktop's TodoList. It used to be a chip in the strip
 * under the top bar, the one place nobody looks while a turn runs; here it
 * answers "what is it doing, and how far along is it" without asking.
 *
 * Open while the agent works, folded to one line ("3 of 7 tasks done · Now:
 * …") once it stops, or while something more pressing waits above it
 * ([folded]: an approval or a question). A tap on the head overrides that
 * until busy or folded flips again.
 */
@Composable
fun TodoList(
    plan: List<AcpPlanEntry>,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    folded: Boolean = false,
    /** The whole tab's cap, head included; a short window gets fewer rows. */
    maxHeight: Dp = TabMaxHeight,
) {
    if (plan.isEmpty()) return
    val colors = LocalSpettroColors.current
    var override by remember(isBusy, folded) { mutableStateOf<Boolean?>(null) }
    val entries = plan.map(Todos::entry)
    val open = override ?: (isBusy && !folded)
    val chevron by animateFloatAsState(
        targetValue = if (open) 0f else 180f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "todoChevron",
    )
    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, topEnd = Dimens.radiusMd)
    val small = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = 0.6f).compositeOver(colors.canvas))
            // Its bottom edge, hairline and all, disappears under the card.
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .padding(bottom = TodoTuck)
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { override = !open }
                .heightIn(min = TodoHeadHeight)
                .padding(horizontal = Dimens.spacingMd, vertical = 7.dp)
                .semantics { contentDescription = if (open) "Hide tasks" else "Show tasks" },
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Checklist, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            Text(
                Todos.summary(entries),
                style = small.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
            )
            val current = if (open) null else Todos.current(entries)
            Box(Modifier.weight(1f)) {
                if (current != null) {
                    Text(current, style = small, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp).rotate(chevron),
            )
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - TodoHeadHeight).coerceIn(0.dp, 188.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(start = Dimens.spacingMd, end = Dimens.spacingMd, bottom = Dimens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                entries.forEach { TodoRow(it, isBusy) }
            }
        }
    }
}

@Composable
private fun TodoRow(entry: Todos.Entry, isBusy: Boolean) {
    val colors = LocalSpettroColors.current
    val style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = 13.dp, height = 20.dp), contentAlignment = Alignment.Center) {
            when (entry.status) {
                Todos.Status.COMPLETED ->
                    Icon(Icons.Outlined.Check, contentDescription = "Done", tint = colors.success, modifier = Modifier.size(13.dp))
                Todos.Status.IN_PROGRESS ->
                    Icon(Icons.Outlined.Contrast, contentDescription = "In progress", tint = colors.accentText, modifier = Modifier.size(13.dp))
                Todos.Status.PENDING ->
                    Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "To do", tint = colors.textTertiary, modifier = Modifier.size(13.dp))
            }
        }
        Box(Modifier.weight(1f)) {
            when (entry.status) {
                // The task under way shimmers only while the agent works: a
                // stopped turn leaves its task "in progress", and a shimmer
                // then would say something runs.
                Todos.Status.IN_PROGRESS -> Shimmer(
                    text = entry.text,
                    style = style.copy(fontWeight = FontWeight.Medium),
                    isActive = isBusy,
                )
                Todos.Status.COMPLETED -> Text(
                    entry.text,
                    style = style.copy(textDecoration = TextDecoration.LineThrough),
                    color = colors.textTertiary,
                )
                Todos.Status.PENDING -> Text(entry.text, style = style, color = colors.textPrimary)
            }
        }
        if (entry.blocked) Pill("Blocked")
    }
}

/**
 * [tab] above [card], tucked [tuck] under it: the card is placed after the
 * tab, so it draws over the tab's last [tuck] of height — the desktop's
 * negative margin.
 */
@Composable
internal fun TuckedUnder(
    tuck: Dp,
    tab: @Composable () -> Unit,
    card: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(tab, card), modifier = modifier) { (tabs, cards), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val tabPlaceables = tabs.map { it.measure(loose) }
        val tabHeight = tabPlaceables.maxOfOrNull { it.height } ?: 0
        val overlap = if (tabHeight > 0) minOf(tuck.roundToPx(), tabHeight) else 0
        val cardPlaceables = cards.map { it.measure(loose) }
        val cardHeight = cardPlaceables.maxOfOrNull { it.height } ?: 0
        val width = (tabPlaceables + cardPlaceables).maxOfOrNull { it.width } ?: constraints.minWidth
        layout(width, tabHeight - overlap + cardHeight) {
            tabPlaceables.forEach { it.place(0, 0) }
            cardPlaceables.forEach { it.place(0, tabHeight - overlap) }
        }
    }
}

@Preview(name = "Todo list", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun TodoListPreview() {
    SpettroTheme(darkTheme = true) {
        TodoList(plan = ChatPreviewData.plan, isBusy = true, modifier = Modifier.padding(16.dp))
    }
}
