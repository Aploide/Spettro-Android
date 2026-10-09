package to.eyed.spettro.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The desktop's humanize table, case by case (src/shared/humanize.ts). */
class HumanizeTest {

    private fun title(message: String, ctx: HumanizeContext = HumanizeContext()) = humanizeError(message, ctx).title

    @Test
    fun `provider rejections are about the key, before anything else`() {
        val human = humanizeError("provider returned 401: invalid api key")
        assertEquals("Your API key was rejected", human.title)
        assertEquals(ErrorActionKind.Connect, human.action?.kind)
        assertTrue(human.known)
    }

    @Test
    fun `each rule in the table`() {
        assertEquals("Add a few words to send with your image", title("prompt has no text content"))
        assertEquals("Spettro’s helper app can’t be started", title("spawn /opt/spettro EACCES"))
        assertEquals("Spettro’s helper app is missing", title("spawn spettro ENOENT"))
        assertEquals("Spettro keeps stopping", title("The engine keeps stopping"))
        assertEquals("That file doesn’t seem to be Spettro", title("It quit before answering"))
        assertEquals("Spettro stopped unexpectedly", title("The Spettro agent exited (code 1)"))
        assertEquals("Spettro’s helper app didn’t answer as expected", title("malformed initialize result"))
        assertEquals(
            "No model is connected",
            title("plan agent: agent call failed: no API endpoint configured for provider \"\""),
        )
        assertEquals("That model isn’t available", title("unknown model gpt-9"))
        assertEquals("The model provider is overloaded", title("529 overloaded_error"))
        assertEquals("You’ve hit the provider’s rate limit", title("429 Too Many Requests"))
        assertEquals("You’re out of credits with this provider", title("insufficient_credits"))
        assertEquals("Spettro couldn’t connect", title("connect ECONNREFUSED 127.0.0.1:443"))
        assertEquals("You appear to be offline", title("getaddrinfo ENOTFOUND api.example.com"))
        assertEquals("That took too long", title("request timed out"))
        assertEquals("Something went wrong inside Spettro", title("Internal error"))
    }

    @Test
    fun `a refused local server says nothing is running there`() {
        val human = humanizeError(
            "ECONNREFUSED",
            HumanizeContext(endpoint = "http://localhost:11434/v1", serverName = "Ollama"),
        )
        assertEquals("Nothing is running at localhost:11434", human.title)
        assertEquals("Is Ollama open? Start it, then try again.", human.detail)
    }

    @Test
    fun `an unknown message becomes its own first line, as a sentence`() {
        val human = humanizeError("the frobnicator jammed\nstack trace follows")
        assertEquals("Something went wrong", human.title)
        assertEquals("The frobnicator jammed.", human.detail)
        assertNull(human.action)
        assertFalse(human.known)
        assertEquals("The frobnicator jammed.", human.sentence)
    }

    @Test
    fun `a long unknown message is cut to fit`() {
        val human = humanizeError("x".repeat(400))
        assertEquals(280, human.detail.length)
        assertTrue(human.detail.endsWith("…"))
    }

    @Test
    fun `routing noise is stripped and kept out of raw`() {
        val human = humanizeError("java.io.IOException: socket hang up")
        assertEquals("socket hang up", human.raw)
        assertEquals("You appear to be offline", human.title)
    }

    @Test
    fun `a known error reads as title then detail`() {
        assertEquals(
            "That took too long. Spettro gave up waiting. Check your connection, then try again.",
            humanizeError("timeout").sentence,
        )
    }

    @Test
    fun `an empty message doesn't point at details there aren't`() {
        for (message in listOf(null, "", "  \n ")) {
            val human = humanizeError(message)
            assertEquals("Something went wrong", human.title)
            assertEquals("Try again. If it keeps happening, check Spettro on your computer.", human.detail)
            assertTrue(human.raw.isBlank())
        }
    }
}
