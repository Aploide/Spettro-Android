package to.eyed.spettro.mobile.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the starter prompts switch to the fresh-start ones. */
class BroadFolderTest {
    @Test
    fun theRootAHomeFolderOrADriveIsBroad() {
        assertTrue(isBroadFolder("/"))
        assertTrue(isBroadFolder("/home/carlo"))
        assertTrue(isBroadFolder("/Users/carlo/"))
        assertTrue(isBroadFolder("C:"))
        assertTrue(isBroadFolder("D:\\"))
    }

    @Test
    fun aProjectIsNot() {
        assertFalse(isBroadFolder("/home/carlo/dev/spettro"))
        assertFalse(isBroadFolder("C:\\dev\\app"))
        assertFalse(isBroadFolder(""))
        assertFalse(isBroadFolder(null))
    }
}
