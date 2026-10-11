package to.eyed.spettro.mobile.core.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [RemoteCredentialStore.load] against a real DataStore file on the JVM. */
class RemoteCredentialStoreTest {

    private val dir: File = Files.createTempDirectory("spettro-credential").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        File(dir, "spettro_remote.preferences_pb")
    }

    /** Where builds that knew about the computer's internet tunnel kept its hostname. */
    private val legacyRemoteHost = stringPreferencesKey("remote.remoteHost")

    private val credential = RemoteCredential(
        hostID = "HOST-1",
        hostName = "endermite",
        hostKind = "app",
        deviceKey = ByteArray(32) { it.toByte() },
        pairedAt = "2026-10-09T10:00:00Z",
        lastAddress = "192.168.1.21",
        lastPort = 7879,
    )

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `load keeps a pairing stored with an internet hostname and drops the hostname`() = runBlocking {
        dataStore.edit { prefs ->
            CredentialPrefs.write(prefs, credential)
            prefs[legacyRemoteHost] = "spettro.example.com"
        }
        val store = RemoteCredentialStore(dataStore, scope)

        assertEquals(credential, store.load())
        assertEquals(credential, store.credential.value)
        val onDisk = dataStore.data.first()
        assertFalse(legacyRemoteHost in onDisk)
        assertEquals(credential, CredentialPrefs.read(onDisk))
    }

    @Test
    fun `load with nothing stored reads nothing and writes nothing`() = runBlocking {
        val store = RemoteCredentialStore(dataStore, scope)

        assertNull(store.load())
        assertEquals(0, dataStore.data.first().asMap().size)
    }
}
