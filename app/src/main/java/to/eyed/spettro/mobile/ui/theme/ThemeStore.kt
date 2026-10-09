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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        write(prefs, choice.appearance)
        write(prefs, choice.accent)
    }

    /** Only the appearance key: a write never touches the setting it isn't about. */
    fun write(prefs: MutablePreferences, appearance: Appearance) {
        prefs[APPEARANCE] = appearance.name
    }

    fun write(prefs: MutablePreferences, accent: Accent) {
        prefs[ACCENT] = accent.name
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

    // Read before the first frame, not after it: a phone set to Dark or
    // Monochrome would otherwise draw its first frames System and Lilac and
    // then switch. The file is two short strings; a damaged one reads as
    // the defaults.
    private val _choice = MutableStateFlow(
        runBlocking { runCatching { ThemePrefs.read(dataStore.data.first()) }.getOrDefault(ThemeChoice()) },
    )

    /**
     * The current choice. This store is the only writer, so after the first
     * read it is the truth and the disk follows it — it never reads back, so a
     * write landing late can't flip the screen away from the last tap.
     */
    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    /** Writes, one at a time and in the order they were asked for. */
    private val writes = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) runCatching { dataStore.edit { write(it) } }
        }
    }

    fun setAppearance(appearance: Appearance) {
        // Shown at once; the disk catches up.
        _choice.update { it.copy(appearance = appearance) }
        writes.trySend { ThemePrefs.write(it, appearance) }
    }

    fun setAccent(accent: Accent) {
        _choice.update { it.copy(accent = accent) }
        writes.trySend { ThemePrefs.write(it, accent) }
    }
}
