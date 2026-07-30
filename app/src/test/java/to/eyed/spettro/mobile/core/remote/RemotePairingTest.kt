package to.eyed.spettro.mobile.core.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Base64UrlTest {

    @Test
    fun `round trips arbitrary byte lengths`() {
        for (length in 0..66) {
            val bytes = ByteArray(length) { ((it * 37 + length) and 0xFF).toByte() }
            val encoded = Base64Url.encode(bytes)
            val expected = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            assertEquals("encode length $length", expected, encoded)
            assertArrayEquals("decode length $length", bytes, Base64Url.decode(encoded))
        }
    }

    @Test
    fun `never emits padding or unsafe characters`() {
        val encoded = Base64Url.encode(ByteArray(32) { 0xFB.toByte() })
        assertFalse(encoded.contains('='))
        assertFalse(encoded.contains('+'))
        assertFalse(encoded.contains('/'))
    }

    @Test
    fun `decode tolerates padding and rejects garbage`() {
        assertArrayEquals(byteArrayOf(1, 2, 3), Base64Url.decode("AQID"))
        assertArrayEquals(byteArrayOf(1, 2), Base64Url.decode("AQI="))
        assertNull(Base64Url.decode("not base64!"))
        assertNull(Base64Url.decode("A")) // impossible leftover length
    }
}

class RemoteCryptoProofTest {

    @Test
    fun `proof is base64url HMAC-SHA256 over challenge then utf8 hostID`() {
        val key = ByteArray(32) { it.toByte() }
        val challenge = ByteArray(32) { (255 - it).toByte() }
        val hostID = "9C0FF9E1-2E1D-4A5B-8F0C-01234ABCDE99"

        // Independent computation: single doFinal over the concatenated
        // message, encoded with the JDK's base64url. This pins both the
        // concatenation order (challenge || hostID) and the encoding.
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val expectedBytes = mac.doFinal(challenge + hostID.toByteArray(Charsets.UTF_8))
        val expected = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(expectedBytes)

        assertEquals(expected, RemoteCrypto.proof(key, challenge, hostID))
    }

    @Test
    fun `proof changes with host id`() {
        val key = ByteArray(32) { 7 }
        val challenge = ByteArray(32) { 9 }
        val a = RemoteCrypto.proof(key, challenge, "host-a")
        val b = RemoteCrypto.proof(key, challenge, "host-b")
        assertFalse(a == b)
    }

    @Test
    fun `known proof vector`() {
        // key = 32 zero bytes, challenge = 32 zero bytes, hostID = "host".
        val key = ByteArray(32)
        val challenge = ByteArray(32)
        val proof = RemoteCrypto.proof(key, challenge, "host")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(challenge)
        mac.update("host".toByteArray(Charsets.UTF_8))
        assertEquals(
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal()),
            proof,
        )
        // The MAC is 32 bytes -> 43 base64url chars, unpadded.
        assertEquals(43, proof.length)
    }
}

class PairPayloadParseTest {

    private val secret = ByteArray(32) { (it + 1).toByte() }
    private val secretB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret)

    @Test
    fun `parses a full URL`() {
        val url = "spettro-pair://pair?v=1&h=HOST-123&n=Carlo%27s%20MacBook%20Pro&k=app" +
            "&s=$secretB64&a=192.168.1.24&p=7879"
        val payload = RemotePairing.parse(url)
        assertNotNull(payload)
        payload!!
        assertEquals(1, payload.protocolVersion)
        assertEquals("HOST-123", payload.hostID)
        assertEquals("Carlo's MacBook Pro", payload.hostName)
        assertEquals("app", payload.hostKind)
        assertArrayEquals(secret, payload.secret)
        assertEquals("192.168.1.24", payload.address)
        assertEquals(7879, payload.port)
    }

    @Test
    fun `parses a bare query string`() {
        val payload = RemotePairing.parse("v=1&h=HOST-123&n=Mac&k=tui&s=$secretB64")
        assertNotNull(payload)
        payload!!
        assertEquals("HOST-123", payload.hostID)
        assertEquals("tui", payload.hostKind)
        assertArrayEquals(secret, payload.secret)
        assertNull(payload.address)
        assertNull(payload.port)
    }

    @Test
    fun `defaults optional fields`() {
        val payload = RemotePairing.parse("h=HOST-123&s=$secretB64")
        assertNotNull(payload)
        payload!!
        assertEquals(RemoteProtocolInfo.VERSION, payload.protocolVersion)
        assertEquals("Spettro", payload.hostName)
        assertEquals("app", payload.hostKind)
    }

    @Test
    fun `rejects a secret that is not exactly 32 bytes`() {
        val short = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(31) { 1 })
        val long = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(33) { 1 })
        assertNull(RemotePairing.parse("h=HOST-123&s=$short"))
        assertNull(RemotePairing.parse("h=HOST-123&s=$long"))
        assertNull(RemotePairing.parse("h=HOST-123&s=%%%"))
        assertNull(RemotePairing.parse("h=HOST-123"))
    }

    @Test
    fun `rejects a missing or empty host id`() {
        assertNull(RemotePairing.parse("v=1&s=$secretB64"))
        assertNull(RemotePairing.parse("v=1&h=&s=$secretB64"))
    }

    @Test
    fun `rejects other schemes and empty input`() {
        assertNull(RemotePairing.parse("https://example.com/pair?h=HOST&s=$secretB64"))
        assertNull(RemotePairing.parse(""))
        assertNull(RemotePairing.parse("   "))
    }

    @Test
    fun `trims surrounding whitespace`() {
        val payload = RemotePairing.parse("  spettro-pair://pair?h=HOST-123&s=$secretB64\n")
        assertNotNull(payload)
        assertEquals("HOST-123", payload!!.hostID)
    }

    @Test
    fun `secret survives an encode-decode round trip`() {
        val payload = RemotePairing.parse("h=X&s=${Base64Url.encode(secret)}")
        assertNotNull(payload)
        assertTrue(payload!!.secret.contentEquals(secret))
    }
}
