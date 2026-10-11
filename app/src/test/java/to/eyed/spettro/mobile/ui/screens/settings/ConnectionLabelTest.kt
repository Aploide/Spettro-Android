package to.eyed.spettro.mobile.ui.screens.settings

import org.junit.Assert.assertEquals
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
    fun `the internet route leaves the tunnel host to the row below`() {
        // Settings shows "Internet address  <host>" right under it.
        assertEquals("Connected over the internet", connectionLabel(RemoteLink.Internet))
    }
}
