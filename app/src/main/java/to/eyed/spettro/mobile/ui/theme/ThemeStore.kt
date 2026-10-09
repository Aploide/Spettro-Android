package to.eyed.spettro.mobile.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The phone's own look: what `SpettroTheme` is given. */
data class ThemeChoice(
    val appearance: Appearance = Appearance.System,
    val accent: Accent = Accent.Lilac,
)

/**
 * Reading and writing a [ThemeChoice] in preferences, apart from DataStore so
 * it can be tested on a plain [MutablePreferences]. Values are stored by enum
 * name; an unknown or missing one reads as the default, so a value written by
 * a newer build never breaks an older one.
 */
internal object ThemePrefs {
    val APPEARANCE = stringPreferencesKey("appearance")
    val ACCENT = stringPreferencesKey("accent")

    fun read(prefs: Preferences): ThemeChoice = ThemeChoice(
        appearance = prefs[APPEARANCE].toEnum(Appearance.System),
        accent = prefs[ACCENT].toEnum(Accent.Lilac),
    )

    fun write(prefs: MutablePreferences, choice: ThemeChoice) {
        prefs[APPEARANCE] = choice.appearance.name
        prefs[ACCENT] = choice.accent.name
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default
}

/**
 * Persists the appearance and accent settings. They belong to the phone: the
 * desktop has the same two settings but does not relay them, and a phone
 * paired with a Monochrome computer may still want Lilac.
 *
 * Create at most one instance per process: DataStore requires a single
 * active instance per file.
 */
class ThemeStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.applicationContext.preferencesDataStoreFile("spettro_theme")
    }

    private val _choice = MutableStateFlow(ThemeChoice())

    /** The current choice; the defaults until the first read lands (a few ms after launch). */
    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    init {
        scope.launch {
            dataStore.data.map(ThemePrefs::read).collect { _choice.value = it }
        }
    }

    fun setAppearance(appearance: Appearance) = update(_choice.value.copy(appearance = appearance))

    fun setAccent(accent: Accent) = update(_choice.value.copy(accent = accent))

    private fun update(choice: ThemeChoice) {
        // Shown at once; the disk catches up.
        _choice.value = choice
        scope.launch { dataStore.edit { ThemePrefs.write(it, choice) } }
    }
}
