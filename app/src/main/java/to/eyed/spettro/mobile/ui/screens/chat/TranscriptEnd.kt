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

/** What the transcript's follow-the-end rule reads from one layout. */
internal data class FollowFrame(
    val height: Int,
    val atEnd: Boolean,
    /** The last item's height while it is on screen, else -1. */
    val tailSize: Int,
    val total: Int = 0,
    /** How far the last item reaches past the end of the view (0 when it
     *  ends inside it; [Int.MAX_VALUE] while it is off screen). */
    val overflowPx: Int = 0,
    /** A scroll — the reader's or the follow rule's — is moving the list. */
    val scrolling: Boolean = false,
) {
    companion object {
        fun of(info: LazyListLayoutInfo, slackPx: Int, scrolling: Boolean): FollowFrame {
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            val tail = lastVisible?.takeIf { it.index == info.totalItemsCount - 1 }
            val overflow = when {
                info.totalItemsCount == 0 -> 0
                tail == null -> Int.MAX_VALUE
                else -> (tail.offset + tail.size - (info.viewportEndOffset - info.afterContentPadding)).coerceAtLeast(0)
            }
            return FollowFrame(info.viewportSize.height, overflow <= slackPx, tail?.size ?: -1, info.totalItemsCount, overflow, scrolling)
        }
    }
}

/** How long a chat just opened keeps landing at its end as its rows measure. */
internal const val SETTLE_MS = 1_500L

/**
 * Decides, layout by layout, when the layout (not the reader) has moved the
 * transcript's end out of view and it should be brought back.
 *
 * A move that lands while a scroll is in flight is remembered, not dropped:
 * while a reply streams the follow rule is animating nearly all the time,
 * so the prompt dock sliding in mid-animation used to be skipped — and with
 * nothing moving after it, the end (and the ticker saying what it waits for)
 * stayed under the dock. It is brought back once the scroll settles, unless
 * the reader's own finger started that scroll.
 */
internal class EndFollower(private val openedAt: Long) {
    private var last = FollowFrame(height = -1, atEnd = true, tailSize = -1)
    private var pending = false
    private var dragged = false

    /**
     * Whether the reader is following the end. Only the reader changes it:
     * their finger lets go of the end, and coming back to it (by hand or by
     * the Latest message pill) takes it up again. The layout moving the end
     * away — a reply growing above the ticker, a row added — doesn't.
     */
    private var sticky = true

    /** The follow rule may animate to each change in the rows. */
    val following: Boolean
        get() = !dragged && sticky

    /** The reader put a finger on the list: their scroll, not ours. */
    fun onDrag() {
        dragged = true
        pending = false
        sticky = false
    }

    /** Returns whether to scroll to the very end now. */
    fun onFrame(frame: FollowFrame, now: Long): Boolean {
        // The reader's scroll is over once the list comes to rest; where it
        // came to rest says whether they still follow.
        if (last.scrolling && !frame.scrolling && dragged) {
            dragged = false
            sticky = frame.atEnd
        }
        if (!dragged && frame.atEnd) sticky = true
        val shrank = last.height > 0 && frame.height in 1 until last.height
        val tailChanged = last.tailSize > 0 && frame.tailSize > 0 && frame.tailSize != last.tailSize
        // A row added under the last one (a tool call, the ticker) moves the
        // end below the view without anything on screen changing size.
        val grew = sticky && last.total > 0 && frame.total > last.total
        val settling = now - openedAt < SETTLE_MS
        // The slack says who was reading at the end; once the layout moves,
        // the end comes all the way back into view — a ticker half under the
        // prompt dock is still in the slack.
        val moved = last.atEnd && (shrank || tailChanged || settling)
        if (frame.overflowPx > 0 && !dragged && (moved || grew)) pending = true
        if (pending && !frame.scrolling) {
            pending = false
            if (frame.overflowPx > 0 && frame.total > 0) {
                last = frame.copy(atEnd = true, overflowPx = 0)
                return true
            }
        }
        last = if (pending) frame.copy(atEnd = true) else frame
        return false
    }
}
