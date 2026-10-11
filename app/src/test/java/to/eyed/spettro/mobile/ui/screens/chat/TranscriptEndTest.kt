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
