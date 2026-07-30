package to.eyed.spettro.mobile.coordinator

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Which backend the app is currently pointed at. */
enum class AppMode { Remote, Cli }

private val Context.appPrefsStore by preferencesDataStore(name = "app_prefs")

/** Tiny app-level preferences: the selected mode and the last CLI endpoint. */
class AppPrefs(private val context: Context) {
    private val modeKey = stringPreferencesKey("app_mode")
    private val cliEndpointKey = stringPreferencesKey("cli_endpoint")

    val mode: Flow<AppMode> = context.appPrefsStore.data.map { prefs ->
        if (prefs[modeKey] == "cli") AppMode.Cli else AppMode.Remote
    }

    val cliEndpoint: Flow<String?> = context.appPrefsStore.data.map { it[cliEndpointKey] }

    suspend fun setMode(mode: AppMode) {
        context.appPrefsStore.edit { it[modeKey] = if (mode == AppMode.Cli) "cli" else "remote" }
    }

    suspend fun setCliEndpoint(endpoint: String) {
        context.appPrefsStore.edit { it[cliEndpointKey] = endpoint }
    }
}
