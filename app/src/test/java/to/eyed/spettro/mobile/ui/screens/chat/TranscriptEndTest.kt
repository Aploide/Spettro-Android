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
