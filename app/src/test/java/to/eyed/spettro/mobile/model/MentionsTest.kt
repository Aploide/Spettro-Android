package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ported from the desktop's mentions.test.ts. */
class MentionsTest {

    @Test
    fun theQueryStartsAtAnAtSignAtTheStartOrAfterWhitespace() {
        assertEquals(Mentions.Query(0, "sav"), Mentions.at("@sav", 4))
        assertEquals(Mentions.Query(8, "src/sa"), Mentions.at("look at @src/sa", 15))
        assertEquals(Mentions.Query(8, ""), Mentions.at("look at @", 9))
    }

    @Test
    fun itIsNotAnEmailAndEndsAtWhitespace() {
        assertNull(Mentions.at("mail me@host", 12))
        assertNull(Mentions.at("@src done", 9))
        assertNull(Mentions.at("no mention", 10))
    }

    @Test
    fun itReadsOnlyUpToTheCaret() {
        assertEquals(Mentions.Query(0, "sav"), Mentions.at("@savebutton", 4))
    }

    @Test
    fun pickingAFileReplacesTheQueryWithThePathAndASpace() {
        val out = Mentions.insert("compare @sav", 8, 12, "src/SaveButton.tsx")
        assertEquals("compare @src/SaveButton.tsx ", out.text)
        assertEquals(out.text.length, out.caret)
    }

    @Test
    fun pickingKeepsTheTextAfterTheCaretWithoutDoublingItsSpace() {
        val out = Mentions.insert("see @sa and more", 4, 7, "a.ts")
        assertEquals("see @a.ts and more", out.text)
        assertEquals("and more", out.text.substring(out.caret))
    }

    private val files = listOf(
        "src/bootstrap/tsconfig.json",
        "src/components/SaveButton.tsx",
        "src/components/SaveButton.test.tsx",
        "src/views/SettingsForm.tsx",
        "README.md",
    )

    @Test
    fun aNameMatchComesFirst() {
        assertEquals("src/components/SaveButton.tsx", Mentions.rank(files, "savebutton")[0])
        assertEquals("src/views/SettingsForm.tsx", Mentions.rank(files, "settings")[0])
    }

    @Test
    fun scatteredLettersMatchInOrderWordStartsFirst() {
        assertEquals("src/components/SaveButton.tsx", Mentions.rank(files, "sbt")[0])
        assertEquals(emptyList<String>(), Mentions.rank(files, "zzz"))
    }

    @Test
    fun aBareAtShowsTheTopOfTheProject() {
        assertEquals(listOf("README.md", "src/views/SettingsForm.tsx"), Mentions.rank(files, "", 2))
    }

    @Test
    fun mentionsAreLiveOnlyWhileTheyAreStillWrittenWhole() {
        assertEquals(listOf("a.ts", "b.ts"), Mentions.live("see @a.ts, then @b.ts", listOf("a.ts", "b.ts", "c.ts")))
        assertEquals(emptyList<String>(), Mentions.live("see @a.tsx", listOf("a.ts")))
        assertEquals(listOf("a.ts"), Mentions.live("see @a.ts.", listOf("a.ts")))
    }

    @Test
    fun aNameThatGrowsWhenLowercasedDoesNotThrow() {
        // "İ" lowercases to two chars, so the lowercased path is longer
        // than the original; scoring must not index past the original.
        assertEquals(true, Mentions.score("İab", "ib") > 0)
        assertEquals(listOf("İab"), Mentions.rank(listOf("İab", "zzz"), "ib"))
    }
}
