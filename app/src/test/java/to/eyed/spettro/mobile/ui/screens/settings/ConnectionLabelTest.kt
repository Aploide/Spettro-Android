package to.eyed.spettro.mobile.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import to.eyed.spettro.mobile.core.remote.RemoteLink

class ConnectionLabelTest {

    @Test
    fun `each route says how the phone reaches the computer`() {
        assertEquals("Connected over Wi-Fi", connectionLabel(RemoteLink.WiFi, null))
        assertEquals("Connected over Ethernet", connectionLabel(RemoteLink.Ethernet, null))
        assertEquals("Connected over USB", connectionLabel(RemoteLink.Usb, "spettro.example.com"))
        assertEquals("Connected on your network", connectionLabel(RemoteLink.LocalNetwork, null))
    }

    @Test
    fun `the internet route names the tunnel host when it is known`() {
        assertEquals(
            "Connected over the internet · spettro.example.com",
            connectionLabel(RemoteLink.Internet, "spettro.example.com"),
        )
        assertEquals("Connected over the internet", connectionLabel(RemoteLink.Internet, null))
        assertEquals("Connected over the internet", connectionLabel(RemoteLink.Internet, " "))
    }
}
