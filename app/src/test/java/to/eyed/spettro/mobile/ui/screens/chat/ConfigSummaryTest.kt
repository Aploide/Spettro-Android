package to.eyed.spettro.mobile.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpConfigOption

class ConfigSummaryTest {

    private fun options(permission: String) = ChatPreviewData.configOptionsWith("high", ultra = false, permission = permission)

    @Test
    fun `names the mode, the model and the level, in the desktop's order and words`() {
        assertEquals("Coding · GPT-5 · Restricted", ConfigSummary.of(options("restricted"))?.text)
        assertEquals("Coding · GPT-5 · Don’t ask", ConfigSummary.of(options("yolo"))?.text)
    }

    @Test
    fun `only Don't ask warns, and only its own part`() {
        val unasked = ConfigSummary.of(options("yolo"))!!
        assertEquals("Don’t ask", unasked.warning)
        assertEquals("Don’t ask", unasked.text.substring(unasked.warningRange!!))
        for (level in listOf("ask-first", "restricted")) {
            val summary = ConfigSummary.of(options(level))!!
            assertNull(summary.warning)
            assertNull(summary.warningRange)
        }
    }

    @Test
    fun `a level this app doesn't know keeps the CLI's name and doesn't warn`() {
        val custom = ChatPreviewData.configOptions.map { option ->
            if (option.id != "permission") return@map option
            option.copy(
                kind = AcpConfigOption.Kind.Select(
                    current = "paranoid",
                    groups = listOf(AcpConfigOption.OptionGroup(null, listOf(AcpConfigOption.Opt("Paranoid", "paranoid")))),
                ),
            )
        }
        val summary = ConfigSummary.of(custom)!!
        assertEquals("Coding · GPT-5 · Paranoid", summary.text)
        assertNull(summary.warning)
    }

    @Test
    fun `a chat with none of the three has no chip`() {
        val others = ChatPreviewData.configOptions.filter { it.id !in setOf("mode", "model", "permission") }
        assertNull(ConfigSummary.of(others))
        assertNull(ConfigSummary.of(emptyList()))
    }

    @Test
    fun `a warning that isn't in the text marks nothing`() {
        assertNull(ConfigSummary("Coding · GPT-5", warning = "Don’t ask").warningRange)
    }
}
