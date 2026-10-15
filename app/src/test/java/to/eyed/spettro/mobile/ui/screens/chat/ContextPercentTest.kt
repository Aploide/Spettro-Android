package to.eyed.spettro.mobile.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/** The context meter reads the same figure the desktop's header does. */
class ContextPercentTest {
    @Test
    fun roundsAsTheDesktopDoes() {
        assertEquals("21%", contextPercentLabel(41_000, 200_000))
        assertEquals("20%", contextPercentLabel(40_800, 200_000))
        assertEquals("100%", contextPercentLabel(200_000, 200_000))
    }

    @Test
    fun anythingInTheContextIsNeverZero() {
        assertEquals("<1%", contextPercentLabel(150, 200_000))
        assertEquals("0%", contextPercentLabel(0, 200_000))
        assertEquals("0%", contextPercentLabel(10, 0))
    }
}
