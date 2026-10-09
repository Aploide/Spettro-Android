package to.eyed.spettro.mobile.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionCopyTest {

    @Test
    fun `the host's levels get the desktop's names`() {
        assertEquals("Ask first", PermissionCopy.name("ask-first"))
        assertEquals("Restricted", PermissionCopy.name("restricted"))
        assertEquals("Don’t ask", PermissionCopy.name("yolo"))
    }

    @Test
    fun `no level is called YOLO on screen`() {
        for (value in listOf("ask-first", "restricted", "yolo")) {
            assertFalse(PermissionCopy.name(value)!!.contains("YOLO", ignoreCase = true))
            assertFalse(PermissionCopy.description(value)!!.contains("YOLO", ignoreCase = true))
        }
    }

    @Test
    fun `an unknown level keeps the CLI's own words`() {
        assertNull(PermissionCopy.name("paranoid"))
        assertNull(PermissionCopy.description("paranoid"))
    }
}
