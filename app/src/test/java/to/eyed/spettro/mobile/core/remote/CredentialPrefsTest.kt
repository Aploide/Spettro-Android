package to.eyed.spettro.mobile.core.remote

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialPrefsTest {

    private val key = ByteArray(32) { (it * 7).toByte() }

    private fun credential(
        lastAddress: String? = "192.168.1.24",
        lastPort: Int? = 7879,
        remoteHost: String? = "spettro.example.com",
    ) = RemoteCredential(
        hostID = "HOST-1",
        hostName = "Carlo's MacBook Pro",
        hostKind = "app",
        deviceKey = key,
        pairedAt = "2026-10-09T10:00:00Z",
        lastAddress = lastAddress,
        lastPort = lastPort,
        remoteHost = remoteHost,
    )

    @Test
    fun `round trips every field, the tunnel hostname included`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        assertEquals(credential(), CredentialPrefs.read(prefs))
    }

    @Test
    fun `rewriting without optional fields removes them`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        CredentialPrefs.write(prefs, credential(lastAddress = null, lastPort = null, remoteHost = null))
        val read = CredentialPrefs.read(prefs)!!
        assertNull(read.lastAddress)
        assertNull(read.lastPort)
        assertNull(read.remoteHost)
    }

    @Test
    fun `a credential stored before tunnels reads with no hostname`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential(remoteHost = null))
        assertEquals(credential(remoteHost = null), CredentialPrefs.read(prefs))
    }

    @Test
    fun `copies keep the other route`() {
        val base = credential()
        assertEquals("spettro.example.com", base.copyWithEndpoint("10.0.0.2", 7000).remoteHost)
        val cleared = base.copyWithRemoteHost(null)
        assertNull(cleared.remoteHost)
        assertEquals("192.168.1.24", cleared.lastAddress)
        assertEquals(7879, cleared.lastPort)
    }

    @Test
    fun `clear leaves nothing to read`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        CredentialPrefs.clear(prefs)
        assertNull(CredentialPrefs.read(prefs))
        assertEquals(0, prefs.asMap().size)
    }
}
