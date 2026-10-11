package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState

/**
 * How to bring the transcript's end into view from one layout.
 *
 * The end is the *bottom* of the last item, not its top: a finished reply is
 * often taller than the screen, and scrolling to its top when the turn ends
 * (the run ticker leaving makes the reply the last item) put the reader back
 * at the reply's first line, a screenful above where they were reading.
 */
internal sealed interface EndScroll {
    /** The end is already in view. */
    data object None : EndScroll

    /** The last item is on screen: scroll down by this many pixels. */
    data class By(val px: Int) : EndScroll

    /** The last item is off screen: go to it first, then to its bottom. */
    data object ToItem : EndScroll

    companion object {
        /**
         * [tailOffset]/[tailSize] are the last item's, or null while it is
         * off screen; [viewportEnd] is where the view's content area ends.
         */
        fun of(total: Int, tailOffset: Int?, tailSize: Int?, viewportEnd: Int): EndScroll = when {
            total == 0 -> None
            tailOffset == null || tailSize == null -> ToItem
            else -> (tailOffset + tailSize - viewportEnd).let { if (it > 0) By(it) else None }
        }

        fun of(info: LazyListLayoutInfo): EndScroll {
            val tail = info.visibleItemsInfo.lastOrNull()?.takeIf { it.index == info.totalItemsCount - 1 }
            return of(info.totalItemsCount, tail?.offset, tail?.size, info.viewportEndOffset - info.afterContentPadding)
        }
    }
}

/**
 * The reader is "away from the end" — and gets the Latest message pill —
 * once the end is more than [thresholdPx] below the view (the desktop's
 * 64px PIN_THRESHOLD).
 */
internal fun awayFromEnd(scroll: EndScroll, thresholdPx: Int): Boolean = when (scroll) {
    EndScroll.None -> false
    is EndScroll.By -> scroll.px > thresholdPx
    EndScroll.ToItem -> true
}

/** Animates to the very end of the transcript (see [EndScroll]). */
internal suspend fun LazyListState.animateToEnd() {
    if (EndScroll.of(layoutInfo) == EndScroll.ToItem) {
        animateScrollToItem(layoutInfo.totalItemsCount - 1)
    }
    // A tail taller than the view lands with its top at the top; carry on
    // to its bottom. (The list clamps at the end of the content.)
    val rest = EndScroll.of(layoutInfo)
    if (rest is EndScroll.By) animateScrollBy(rest.px.toFloat())
}
