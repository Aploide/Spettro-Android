package to.eyed.spettro.mobile.core.remote

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialPrefsTest {

    private val key = ByteArray(32) { (it * 7).toByte() }

    /** Where builds that knew about the computer's internet tunnel kept its hostname. */
    private val legacyRemoteHost = stringPreferencesKey("remote.remoteHost")

    private fun credential(
        lastAddress: String? = "192.168.1.24",
        lastPort: Int? = 7879,
    ) = RemoteCredential(
        hostID = "HOST-1",
        hostName = "Carlo's MacBook Pro",
        hostKind = "app",
        deviceKey = key,
        pairedAt = "2026-10-09T10:00:00Z",
        lastAddress = lastAddress,
        lastPort = lastPort,
    )

    @Test
    fun `round trips every field`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        assertEquals(credential(), CredentialPrefs.read(prefs))
    }

    @Test
    fun `rewriting without optional fields removes them`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        CredentialPrefs.write(prefs, credential(lastAddress = null, lastPort = null))
        val read = CredentialPrefs.read(prefs)!!
        assertNull(read.lastAddress)
        assertNull(read.lastPort)
    }

    @Test
    fun `a credential stored with an internet hostname still reads, LAN address and all`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        prefs[legacyRemoteHost] = "spettro.example.com"
        assertTrue(CredentialPrefs.hasLegacy(prefs))
        assertEquals(credential(), CredentialPrefs.read(prefs))
    }

    @Test
    fun `the next write drops the old hostname`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        prefs[legacyRemoteHost] = "spettro.example.com"
        CredentialPrefs.write(prefs, credential().copyWithEndpoint("192.168.1.30", 7879))
        assertFalse(CredentialPrefs.hasLegacy(prefs))
        assertEquals("192.168.1.30", CredentialPrefs.read(prefs)!!.lastAddress)
    }

    @Test
    fun `dropping the old hostname leaves the pairing alone`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        prefs[legacyRemoteHost] = "spettro.example.com"
        CredentialPrefs.dropLegacy(prefs)
        assertFalse(CredentialPrefs.hasLegacy(prefs))
        assertEquals(credential(), CredentialPrefs.read(prefs))
    }

    @Test
    fun `clear leaves nothing to read`() {
        val prefs = mutablePreferencesOf()
        CredentialPrefs.write(prefs, credential())
        prefs[legacyRemoteHost] = "spettro.example.com"
        CredentialPrefs.clear(prefs)
        assertNull(CredentialPrefs.read(prefs))
        assertEquals(0, prefs.asMap().size)
    }
}
