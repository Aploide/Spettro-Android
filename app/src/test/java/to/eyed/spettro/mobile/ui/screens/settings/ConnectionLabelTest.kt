package to.eyed.spettro.mobile.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.RemoteLink

class ConnectionLabelTest {

    @Test
    fun `each route says how the phone reaches the computer`() {
        assertEquals("Connected over Wi-Fi", connectionLabel(RemoteLink.WiFi))
        assertEquals("Connected over Ethernet", connectionLabel(RemoteLink.Ethernet))
        assertEquals("Connected over USB", connectionLabel(RemoteLink.Usb))
        assertEquals("Connected on your network", connectionLabel(RemoteLink.LocalNetwork))
    }

    @Test
    fun `no route is the internet`() {
        for (link in RemoteLink.entries) {
            val label = connectionLabel(link)
            assertFalse(label, label.contains("internet", ignoreCase = true))
        }
    }
}
