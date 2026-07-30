package to.eyed.spettro.mobile.core.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
            dataStore.data.map(::parseCredential).collect { _credential.value = it }
        }
    }

    /** Reads the credential straight from disk and refreshes the cache. */
    suspend fun load(): RemoteCredential? {
        val value = parseCredential(dataStore.data.first())
        _credential.value = value
        return value
    }

    suspend fun save(credential: RemoteCredential) {
        dataStore.edit { prefs ->
            prefs[KEY_HOST_ID] = credential.hostID
            prefs[KEY_HOST_NAME] = credential.hostName
            prefs[KEY_HOST_KIND] = credential.hostKind
            prefs[KEY_DEVICE_KEY] = Base64Url.encode(credential.deviceKey)
            prefs[KEY_PAIRED_AT] = credential.pairedAt
            credential.lastAddress?.let { prefs[KEY_LAST_ADDRESS] = it } ?: prefs.remove(KEY_LAST_ADDRESS)
            credential.lastPort?.let { prefs[KEY_LAST_PORT] = it } ?: prefs.remove(KEY_LAST_PORT)
        }
        _credential.value = credential
    }

    /** Remembers where the host was last reachable, for the next cold start. */
    suspend fun updateEndpoint(address: String?, port: Int?) {
        val current = _credential.value ?: load() ?: return
        save(current.copyWithEndpoint(address ?: current.lastAddress, port ?: current.lastPort))
    }

    /** Drops the pairing. The device id is kept — it identifies the install, not the pairing. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_HOST_ID)
            prefs.remove(KEY_HOST_NAME)
            prefs.remove(KEY_HOST_KIND)
            prefs.remove(KEY_DEVICE_KEY)
            prefs.remove(KEY_PAIRED_AT)
            prefs.remove(KEY_LAST_ADDRESS)
            prefs.remove(KEY_LAST_PORT)
        }
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

    private fun parseCredential(prefs: Preferences): RemoteCredential? {
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
        )
    }

    private companion object {
        val KEY_HOST_ID = stringPreferencesKey("remote.hostID")
        val KEY_HOST_NAME = stringPreferencesKey("remote.hostName")
        val KEY_HOST_KIND = stringPreferencesKey("remote.hostKind")
        val KEY_DEVICE_KEY = stringPreferencesKey("remote.deviceKey")
        val KEY_PAIRED_AT = stringPreferencesKey("remote.pairedAt")
        val KEY_LAST_ADDRESS = stringPreferencesKey("remote.lastAddress")
        val KEY_LAST_PORT = intPreferencesKey("remote.lastPort")
        val KEY_DEVICE_ID = stringPreferencesKey("remote.deviceID")
    }
}
