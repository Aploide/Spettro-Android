package to.eyed.spettro.mobile.ui.theme

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePrefsTest {

    @Test
    fun `nothing stored reads as System and Lilac`() {
        assertEquals(ThemeChoice(Appearance.System, Accent.Lilac), ThemePrefs.read(mutablePreferencesOf()))
    }

    @Test
    fun `round trips every combination`() {
        for (appearance in Appearance.entries) {
            for (accent in Accent.entries) {
                val prefs = mutablePreferencesOf()
                ThemePrefs.write(prefs, ThemeChoice(appearance, accent))
                assertEquals(ThemeChoice(appearance, accent), ThemePrefs.read(prefs))
            }
        }
    }

    @Test
    fun `an unknown stored value falls back to the default for that setting only`() {
        val prefs = mutablePreferencesOf(
            ThemePrefs.APPEARANCE to "Sepia",
            ThemePrefs.ACCENT to "Mono",
        )
        assertEquals(ThemeChoice(Appearance.System, Accent.Mono), ThemePrefs.read(prefs))
    }
}
