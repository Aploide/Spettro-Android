package to.eyed.spettro.mobile.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the transcript's end is, and when the Latest message pill shows. */
class TranscriptEndTest {
    @Test
    fun aReplyTallerThanTheViewEndsAtItsBottomNotItsTop() {
        // The finished reply starts 40px into a 2000px view and runs 3100px.
        assertEquals(EndScroll.By(1140), EndScroll.of(total = 12, tailOffset = 40, tailSize = 3100, viewportEnd = 2000))
    }

    @Test
    fun aTailEndingInsideTheViewIsAlreadyTheEnd() {
        assertEquals(EndScroll.None, EndScroll.of(total = 12, tailOffset = 1500, tailSize = 300, viewportEnd = 2000))
        assertEquals(EndScroll.None, EndScroll.of(total = 12, tailOffset = 1700, tailSize = 300, viewportEnd = 2000))
        assertEquals(EndScroll.None, EndScroll.of(total = 0, tailOffset = null, tailSize = null, viewportEnd = 2000))
    }

    @Test
    fun aTailOffScreenIsGoneToFirst() {
        assertEquals(EndScroll.ToItem, EndScroll.of(total = 191, tailOffset = null, tailSize = null, viewportEnd = 2000))
    }

    @Test
    fun thePillShowsOnlyPastTheThreshold() {
        assertFalse(awayFromEnd(EndScroll.None, 168))
        assertFalse(awayFromEnd(EndScroll.By(168), 168))
        assertTrue(awayFromEnd(EndScroll.By(169), 168))
        assertTrue(awayFromEnd(EndScroll.ToItem, 168))
    }
}

/** When the layout, not the reader, has moved the end away, and it comes back. */
class EndFollowerTest {
    private val later = SETTLE_MS + 1

    private fun frame(height: Int, overflow: Int, scrolling: Boolean = false, tail: Int = 120) =
        FollowFrame(height = height, atEnd = overflow <= 48, tailSize = tail, total = 20, overflowPx = overflow, scrolling = scrolling)

    @Test
    fun theDockSlidingInBringsTheEndBack() {
        val f = EndFollower(openedAt = 0)
        assertFalse(f.onFrame(frame(1800, 0), later))
        assertTrue(f.onFrame(frame(1100, 700), later))
    }

    @Test
    fun aDockArrivingMidAnimationIsCaughtUpOnceItSettles() {
        val f = EndFollower(openedAt = 0)
        assertFalse(f.onFrame(frame(1800, 0, scrolling = true), later))
        // The view shrinks while the follow rule's animation runs.
        assertFalse(f.onFrame(frame(1100, 700, scrolling = true), later))
        // The animation ends short of the end: now it comes back.
        assertTrue(f.onFrame(frame(1100, 600, scrolling = false), later))
    }

    @Test
    fun theReadersOwnScrollIsLeftAlone() {
        val f = EndFollower(openedAt = 0)
        assertFalse(f.onFrame(frame(1800, 0), later))
        f.onDrag()
        assertFalse(f.onFrame(frame(1800, 30, scrolling = true, tail = 160), later))
        assertFalse(f.onFrame(frame(1800, 900, scrolling = true, tail = 200), later))
        assertFalse(f.onFrame(frame(1800, 1400, scrolling = false, tail = 240), later))
    }

    @Test
    fun aReaderWhoScrolledAwayStaysWhereTheyAre() {
        val f = EndFollower(openedAt = 0)
        assertFalse(f.onFrame(frame(1800, 2000), later))
        assertFalse(f.onFrame(frame(1100, 2700), later))
    }

    @Test
    fun aReaderBackAtTheEndIsFollowedAgain() {
        val f = EndFollower(openedAt = 0)
        f.onDrag()
        assertFalse(f.onFrame(frame(1800, 0, scrolling = true), later))
        assertFalse(f.onFrame(frame(1800, 0, scrolling = false), later))
        // Their scroll is over; the reply grows past the end.
        assertTrue(f.onFrame(frame(1800, 90, tail = 210), later))
    }
}

class EndFollowerFollowingTest {
    private val later = SETTLE_MS + 1

    private fun frame(overflow: Int, scrolling: Boolean = false, tail: Int = 900, total: Int = 20) =
        FollowFrame(height = 1800, atEnd = overflow <= 48, tailSize = if (overflow == Int.MAX_VALUE) -1 else tail, total = total, overflowPx = overflow, scrolling = scrolling)

    @Test
    fun scrollingUpAStreamingReplyStopsTheFollowing() {
        val f = EndFollower(openedAt = 0)
        f.onFrame(frame(0), later)
        assertTrue(f.following)
        f.onDrag()
        assertFalse(f.following)
        f.onFrame(frame(1000, scrolling = true), later)
        f.onFrame(frame(1000, scrolling = false), later)
        // The reply keeps growing under the reader, who is left reading.
        assertFalse(f.onFrame(frame(1060, tail = 960), later))
        assertFalse(f.following)
    }

    @Test
    fun aRowAddedUnderTheEndIsFollowed() {
        val f = EndFollower(openedAt = 0)
        f.onFrame(frame(0, total = 20), later)
        // A tool call lands below the view: the end is off screen.
        assertTrue(f.onFrame(frame(Int.MAX_VALUE, total = 21), later))
        assertTrue(f.following)
    }
}

class EndFollowerStickyTest {
    private val later = SETTLE_MS + 1

    @Test
    fun aReplyGrowingAboveTheTickerKeepsTheReaderFollowing() {
        val f = EndFollower(openedAt = 0)
        // The ticker is the last row and keeps its size; the reply above it
        // grows and pushes it below the view.
        f.onFrame(FollowFrame(height = 1800, atEnd = true, tailSize = 60, total = 20, overflowPx = 0), later)
        assertFalse(f.onFrame(FollowFrame(height = 1800, atEnd = false, tailSize = 60, total = 20, overflowPx = 240), later))
        assertTrue(f.following)
    }
}
