package to.eyed.spettro.mobile.core.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Persists the [RemoteCredential] (one paired host) plus a stable per-install
 * device id, backed by DataStore preferences. The device key is stored
 * base64url-encoded.
 *
 * Losing a socket never touches this store — only [clear] (the user's
 * explicit "forget") does.
 *
 * Create at most one instance per process: DataStore requires a single
 * active instance per file.
 */
class RemoteCredentialStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.applicationContext.preferencesDataStoreFile("spettro_remote")
    }

    private val _credential = MutableStateFlow<RemoteCredential?>(null)

    /** Cached view of the stored credential. Null until [load] or the initial read completes. */
    val credential: StateFlow<RemoteCredential?> = _credential.asStateFlow()

    init {
        scope.launch {
            dataStore.data.map(CredentialPrefs::read).collect { _credential.value = it }
        }
    }

    /** Reads the credential straight from disk and refreshes the cache. */
    suspend fun load(): RemoteCredential? {
        val value = CredentialPrefs.read(dataStore.data.first())
        _credential.value = value
        return value
    }

    suspend fun save(credential: RemoteCredential) {
        dataStore.edit { prefs -> CredentialPrefs.write(prefs, credential) }
        _credential.value = credential
    }

    /** Remembers where the host was last reachable on the LAN, for the next cold start. */
    suspend fun updateEndpoint(address: String?, port: Int?) {
        val current = _credential.value ?: load() ?: return
        save(current.copyWithEndpoint(address ?: current.lastAddress, port ?: current.lastPort))
    }

    /**
     * Remembers (or, for null or blank, forgets) the host's public tunnel
     * hostname. A no-op when it is unchanged, so the host repeating itself in
     * every `host/state` costs no disk write.
     */
    suspend fun updateRemoteHost(host: String?) {
        val current = _credential.value ?: load() ?: return
        val normalized = host?.let(RemotePairing::normalizeRemoteHost)
        if (normalized == current.remoteHost) return
        save(current.copyWithRemoteHost(normalized))
    }

    /** Drops the pairing. The device id is kept — it identifies the install, not the pairing. */
    suspend fun clear() {
        dataStore.edit { prefs -> CredentialPrefs.clear(prefs) }
        _credential.value = null
    }

    /**
     * Stable per-install device id, minted once. Re-pairing the same phone
     * then updates the host's existing record instead of adding a duplicate.
     */
    suspend fun deviceID(): String {
        val existing = dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrEmpty()) return existing
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            // Another writer may have raced us; first mint wins.
            if (prefs[KEY_DEVICE_ID].isNullOrEmpty()) prefs[KEY_DEVICE_ID] = fresh
        }
        return dataStore.data.first()[KEY_DEVICE_ID] ?: fresh
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("remote.deviceID")
    }
}

/**
 * The credential's mapping onto preference keys, kept apart from the
 * DataStore plumbing so it can be checked on the JVM. Every optional field
 * is removed rather than left stale when the credential no longer has it.
 */
internal object CredentialPrefs {
    private val KEY_HOST_ID = stringPreferencesKey("remote.hostID")
    private val KEY_HOST_NAME = stringPreferencesKey("remote.hostName")
    private val KEY_HOST_KIND = stringPreferencesKey("remote.hostKind")
    private val KEY_DEVICE_KEY = stringPreferencesKey("remote.deviceKey")
    private val KEY_PAIRED_AT = stringPreferencesKey("remote.pairedAt")
    private val KEY_LAST_ADDRESS = stringPreferencesKey("remote.lastAddress")
    private val KEY_LAST_PORT = intPreferencesKey("remote.lastPort")
    private val KEY_REMOTE_HOST = stringPreferencesKey("remote.remoteHost")

    fun write(prefs: MutablePreferences, credential: RemoteCredential) {
        prefs[KEY_HOST_ID] = credential.hostID
        prefs[KEY_HOST_NAME] = credential.hostName
        prefs[KEY_HOST_KIND] = credential.hostKind
        prefs[KEY_DEVICE_KEY] = Base64Url.encode(credential.deviceKey)
        prefs[KEY_PAIRED_AT] = credential.pairedAt
        credential.lastAddress?.let { prefs[KEY_LAST_ADDRESS] = it } ?: prefs.remove(KEY_LAST_ADDRESS)
        credential.lastPort?.let { prefs[KEY_LAST_PORT] = it } ?: prefs.remove(KEY_LAST_PORT)
        credential.remoteHost?.let { prefs[KEY_REMOTE_HOST] = it } ?: prefs.remove(KEY_REMOTE_HOST)
    }

    fun read(prefs: Preferences): RemoteCredential? {
        val hostID = prefs[KEY_HOST_ID]?.takeIf { it.isNotEmpty() } ?: return null
        val deviceKey = prefs[KEY_DEVICE_KEY]?.let(Base64Url::decode) ?: return null
        return RemoteCredential(
            hostID = hostID,
            hostName = prefs[KEY_HOST_NAME] ?: "Spettro",
            hostKind = prefs[KEY_HOST_KIND] ?: "app",
            deviceKey = deviceKey,
            pairedAt = prefs[KEY_PAIRED_AT] ?: "",
            lastAddress = prefs[KEY_LAST_ADDRESS],
            lastPort = prefs[KEY_LAST_PORT],
            remoteHost = prefs[KEY_REMOTE_HOST]?.takeIf { it.isNotEmpty() },
        )
    }

    fun clear(prefs: MutablePreferences) {
        prefs.remove(KEY_HOST_ID)
        prefs.remove(KEY_HOST_NAME)
        prefs.remove(KEY_HOST_KIND)
        prefs.remove(KEY_DEVICE_KEY)
        prefs.remove(KEY_PAIRED_AT)
        prefs.remove(KEY_LAST_ADDRESS)
        prefs.remove(KEY_LAST_PORT)
        prefs.remove(KEY_REMOTE_HOST)
    }
}
