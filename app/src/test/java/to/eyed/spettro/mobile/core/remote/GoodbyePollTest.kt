package to.eyed.spettro.mobile.core.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a host that turned sharing off is checked on until it's back. */
class GoodbyePollTest {

    private fun cred(lastAddress: String? = null, remoteHost: String? = null) = RemoteCredential(
        hostID = "h",
        hostName = "endermite",
        hostKind = "app",
        deviceKey = ByteArray(32),
        pairedAt = "2026-10-11T00:00:00Z",
        lastAddress = lastAddress,
        lastPort = lastAddress?.let { 7879 },
        remoteHost = remoteHost,
    )

    @Test
    fun aLanAddressIsCheckedTooForNetworksThatFilterBonjour() {
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(lastAddress = "192.168.1.21"), announcedRemoteHost = ""))
    }

    @Test
    fun aTunnelIsChecked() {
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(remoteHost = "spettro.example.com"), announcedRemoteHost = null))
        assertTrue(RemoteClient.keepsCheckingAfterGoodbye(cred(), announcedRemoteHost = "spettro.example.com"))
    }

    @Test
    fun nowhereToCheckWaitsForBonjour() {
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(cred(), announcedRemoteHost = ""))
        assertFalse(RemoteClient.keepsCheckingAfterGoodbye(null, announcedRemoteHost = null))
    }
}
