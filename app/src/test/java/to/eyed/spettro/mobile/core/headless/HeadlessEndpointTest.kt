package to.eyed.spettro.mobile.core.headless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlessEndpointTest {

    @Test
    fun parse_hostPort() {
        assertEquals("http://192.168.1.20:7878", HeadlessEndpoint.parse("192.168.1.20:7878"))
        assertEquals("http://mac.local:8080", HeadlessEndpoint.parse("mac.local:8080"))
    }

    @Test
    fun parse_fullUrl() {
        assertEquals("http://192.168.1.20:7878", HeadlessEndpoint.parse("http://192.168.1.20:7878"))
        assertEquals("http://mac.local:9000", HeadlessEndpoint.parse("http://mac.local:9000/"))
        assertEquals("https://mac.local:7878", HeadlessEndpoint.parse("https://mac.local"))
    }

    @Test
    fun parse_bareHost_defaultsPort() {
        assertEquals("http://192.168.1.20:7878", HeadlessEndpoint.parse("192.168.1.20"))
        assertEquals("http://mac.local:7878", HeadlessEndpoint.parse("  mac.local  "))
    }

    @Test
    fun parse_ipv6() {
        assertEquals("http://[::1]:7878", HeadlessEndpoint.parse("[::1]"))
        assertEquals("http://[fe80::1]:9000", HeadlessEndpoint.parse("[fe80::1]:9000"))
        assertEquals("http://[fe80::1]:7878", HeadlessEndpoint.parse("fe80::1"))
    }

    @Test
    fun parse_invalid() {
        assertNull(HeadlessEndpoint.parse(""))
        assertNull(HeadlessEndpoint.parse("   "))
        assertNull(HeadlessEndpoint.parse("host:99999"))
        assertNull(HeadlessEndpoint.parse("host:abc"))
        assertNull(HeadlessEndpoint.parse(":7878"))
        assertNull(HeadlessEndpoint.parse("http://"))
    }

    @Test
    fun tokenPaste_cliStdoutLines() {
        val paste = HeadlessEndpoint.parseTokenPaste(
            "SPETTRO_TOKEN=0123456789abcdef0123456789ABCDEF\nSPETTRO_PORT=7879\n"
        )
        assertEquals("0123456789abcdef0123456789ABCDEF", paste.token)
        assertEquals(7879, paste.port)
    }

    @Test
    fun tokenPaste_embeddedInOtherOutput() {
        val paste = HeadlessEndpoint.parseTokenPaste(
            """
            remote control enabled
            SPETTRO_TOKEN = deadbeefdeadbeefdeadbeefdeadbeef
            some other line
            SPETTRO_PORT = 8081
            """.trimIndent()
        )
        assertEquals("deadbeefdeadbeefdeadbeefdeadbeef", paste.token)
        assertEquals(8081, paste.port)
    }

    @Test
    fun tokenPaste_bareToken() {
        val paste = HeadlessEndpoint.parseTokenPaste("  deadbeefdeadbeefdeadbeefdeadbeef ")
        assertEquals("deadbeefdeadbeefdeadbeefdeadbeef", paste.token)
        assertNull(paste.port)
    }

    @Test
    fun tokenPaste_nothingFound() {
        val paste = HeadlessEndpoint.parseTokenPaste("hello world")
        assertTrue(paste.isEmpty)
        // Wrong length is not a token.
        assertNull(HeadlessEndpoint.parseTokenPaste("deadbeef").token)
    }
}
