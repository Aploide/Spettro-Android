package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownDelimitersTest {

    /** The paragraph's text as the transcript renders it (the renderer's own annotator, with the hook). */
    private fun render(markdown: String): String {
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
        val settings = DefaultAnnotatorSettings(
            linkTextSpanStyle = TextLinkStyles(),
            codeSpanStyle = SpanStyle(),
            annotator = markdownAnnotator(annotate = keepEmphasisCharacters),
        )
        val paragraph = tree.children.single()
        return buildAnnotatedString { buildMarkdownAnnotatedString(markdown, paragraph, settings) }.text
    }

    @Test
    fun underscoresInInlineCodeStayUnderscores() {
        assertEquals(
            "a  if __name__ == \"__main__\"  guard",
            render("a `if __name__ == \"__main__\"` guard"),
        )
    }

    @Test
    fun snakeCaseInProseKeepsItsUnderscores() {
        assertEquals("call load_user_data first", render("call load_user_data first"))
    }

    @Test
    fun aLoneAsteriskStaysAnAsterisk() {
        assertEquals("2 * 3", render("2 * 3"))
    }

    @Test
    fun realEmphasisStillLosesItsDelimiters() {
        assertEquals("some bold and italic and under", render("some **bold** and *italic* and __under__"))
    }
}
