package to.eyed.spettro.mobile.coordinator.headless

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import to.eyed.spettro.mobile.core.headless.HeadlessEndpoint

/**
 * Persists the last headless endpoint (host + port) so the connect form can
 * prefill on the next launch. The bearer token is deliberately NOT stored:
 * the CLI mints a fresh one every run, so a saved token is stale by design.
 *
 * Create at most one instance per process: DataStore requires a single
 * active instance per file.
 */
class HeadlessStore(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** The remembered endpoint of the last successful connection. */
    data class SavedEndpoint(val host: String, val port: Int) {
        val baseUrl: String get() = "http://$host:$port"
    }

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.applicationContext.preferencesDataStoreFile("spettro_headless")
    }

    private val _endpoint = MutableStateFlow<SavedEndpoint?>(null)

    /** Cached view of the stored endpoint. Null until the initial read (or [load]) completes. */
    val endpoint: StateFlow<SavedEndpoint?> = _endpoint.asStateFlow()

    init {
        scope.launch {
            dataStore.data.map(::parseEndpoint).collect { _endpoint.value = it }
        }
    }

    /** Reads the endpoint straight from disk and refreshes the cache. */
    suspend fun load(): SavedEndpoint? {
        val value = parseEndpoint(dataStore.data.first())
        _endpoint.value = value
        return value
    }

    suspend fun save(host: String, port: Int = HeadlessEndpoint.DEFAULT_PORT) {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || port !in 1..65535) return
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = trimmed
            prefs[KEY_PORT] = port
        }
        _endpoint.value = SavedEndpoint(trimmed, port)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_HOST)
            prefs.remove(KEY_PORT)
        }
        _endpoint.value = null
    }

    private fun parseEndpoint(prefs: Preferences): SavedEndpoint? {
        val host = prefs[KEY_HOST]?.takeIf { it.isNotEmpty() } ?: return null
        return SavedEndpoint(host, prefs[KEY_PORT] ?: HeadlessEndpoint.DEFAULT_PORT)
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("headless.host")
        val KEY_PORT = intPreferencesKey("headless.port")
    }
}
