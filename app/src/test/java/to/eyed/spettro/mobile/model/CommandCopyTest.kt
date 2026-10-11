package to.eyed.spettro.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import to.eyed.spettro.mobile.core.acp.AcpCommand

/** Ported from the desktop's commandCopy.ts. */
class CommandCopyTest {

    private val commands = listOf(
        AcpCommand("budget", "set token budget per request", "tokens"),
        AcpCommand("clear", "clear the session"),
        AcpCommand("stats", "show stats"),
        AcpCommand("mode", "set mode", "plan|coding|ask"),
        AcpCommand("models", "list or set models", "provider:model [api_key]"),
        AcpCommand("frobnicate", "a command this app doesn't know"),
        AcpCommand("bare"),
    )

    private fun names(list: List<AcpCommand>) = list.map { it.name }

    @Test
    fun theEverydayCommandsComeFirstInTheCliOrder() {
        assertEquals(
            listOf("clear", "mode", "models", "frobnicate", "bare", "budget", "stats"),
            names(CommandCopy.order(commands)),
        )
    }

    @Test
    fun knownCommandsReadInPlainWordsAndOthersKeepTheCliText() {
        assertEquals("Forget this conversation so far", CommandCopy.description(commands[1]))
        assertEquals("a command this app doesn't know", CommandCopy.description(commands[5]))
        assertNull(CommandCopy.description(commands[6]))
    }

    @Test
    fun anEmptySearchListsEverythingOrdered() {
        assertEquals(names(CommandCopy.order(commands)), names(CommandCopy.matching(commands, "  ")))
    }

    @Test
    fun theSearchMatchesANamesStartOrThePlainDescription() {
        assertEquals(listOf("mode", "models"), names(CommandCopy.matching(commands, "mo")))
        assertEquals(listOf("mode", "models"), names(CommandCopy.matching(commands, "/MO")))
        // "conversation" is in /clear's plain words, not its CLI text.
        assertEquals(listOf("clear"), names(CommandCopy.matching(commands, "conversation")))
        // A name matches at its start only.
        assertEquals(emptyList<String>(), names(CommandCopy.matching(commands, "ats")))
        // The developer ones are still found, last.
        assertEquals(listOf("budget"), names(CommandCopy.matching(commands, "bud")))
    }

    @Test
    fun aCommandWithAnInputHintWaitsForArgumentsExceptModels() {
        assertTrue(CommandCopy.takesArguments(commands[3]))
        assertTrue(CommandCopy.takesArguments(commands[0]))
        assertFalse(CommandCopy.takesArguments(commands[1]))
        assertFalse(CommandCopy.takesArguments(commands[4]))
        assertFalse(CommandCopy.takesArguments(AcpCommand("x", "", "  ")))
    }
}
