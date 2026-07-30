package to.eyed.spettro.mobile.core.remote

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pairing a phone to a host once, and proving it forever after.
 *
 * The QR code is an *invite*, not a credential: it carries a short-lived
 * 32-byte pairing secret. The client proves it holds the secret, the host
 * mints a durable 32-byte device key, and every later connection proves the
 * device key instead. Proofs are HMAC-SHA256 over a per-connection challenge.
 */

// MARK: - Base64URL

/**
 * base64url without padding, implemented in pure Kotlin so the pairing and
 * proof logic is unit-testable on the JVM (android.util.Base64 is not) and
 * runtime-safe on every API level (java.util.Base64 needs API 26; minSdk is 24).
 *
 * Decoding tolerates trailing `=` padding and the standard `+`/`/` alphabet.
 */
object Base64Url {
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= bytes.size) {
            val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[(n ushr 18) and 63])
            sb.append(ALPHABET[(n ushr 12) and 63])
            sb.append(ALPHABET[(n ushr 6) and 63])
            sb.append(ALPHABET[n and 63])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = (bytes[i].toInt() and 0xFF) shl 16
                sb.append(ALPHABET[(n ushr 18) and 63])
                sb.append(ALPHABET[(n ushr 12) and 63])
            }
            2 -> {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8)
                sb.append(ALPHABET[(n ushr 18) and 63])
                sb.append(ALPHABET[(n ushr 12) and 63])
                sb.append(ALPHABET[(n ushr 6) and 63])
            }
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray? {
        val s = text.trimEnd('=')
        if (s.length % 4 == 1) return null
        val out = ByteArray(s.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var o = 0
        for (c in s) {
            val v = sixBits(c) ?: return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[o++] = ((buffer ushr bits) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun sixBits(c: Char): Int? = when (c) {
        in 'A'..'Z' -> c - 'A'
        in 'a'..'z' -> c - 'a' + 26
        in '0'..'9' -> c - '0' + 52
        '-', '+' -> 62
        '_', '/' -> 63
        else -> null
    }
}

// MARK: - Crypto

object RemoteCrypto {
    /** Key length for both the pairing secret and the durable device key. */
    const val KEY_LENGTH = 32

    /**
     * The proof a client sends: `base64url(HMAC-SHA256(key, challenge || utf8(hostID)))`.
     *
     * Binding the host id into the MAC means a proof minted for one host
     * cannot be relayed to another that issued the same challenge.
     */
    fun proof(key: ByteArray, challenge: ByteArray, hostID: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(challenge)
        mac.update(hostID.toByteArray(Charsets.UTF_8))
        return Base64Url.encode(mac.doFinal())
    }
}

// MARK: - QR payload

/**
 * What the QR code encodes:
 * `spettro-pair://pair?v=1&h=<hostID>&n=<hostName>&k=<kind>&s=<secret>&a=<addr>&p=<port>`
 */
class PairPayload(
    val protocolVersion: Int,
    val hostID: String,
    val hostName: String,
    val hostKind: String,
    /** The one-shot pairing secret, exactly 32 bytes. */
    val secret: ByteArray,
    /** Optional address hint for networks that filter mDNS. */
    val address: String?,
    val port: Int?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairPayload) return false
        return protocolVersion == other.protocolVersion &&
            hostID == other.hostID &&
            hostName == other.hostName &&
            hostKind == other.hostKind &&
            secret.contentEquals(other.secret) &&
            address == other.address &&
            port == other.port
    }

    override fun hashCode(): Int = hostID.hashCode() * 31 + secret.contentHashCode()

    override fun toString(): String =
        "PairPayload(hostID=$hostID, hostName=$hostName, kind=$hostKind, address=$address, port=$port)"
}

object RemotePairing {
    const val SCHEME = "spettro-pair"

    /**
     * Parses a scanned or pasted pairing string: either a full
     * `spettro-pair://pair?...` URL or (from a hand-typed fallback) just the
     * bare query string, which gets the prefix restored.
     *
     * Returns null when the payload is unusable: missing host id, or a
     * secret that does not decode to exactly 32 bytes.
     */
    fun parse(text: String): PairPayload? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val query: String = if (trimmed.contains("://")) {
            if (!trimmed.startsWith("$SCHEME://", ignoreCase = true)) return null
            val q = trimmed.substringAfter('?', missingDelimiterValue = "")
            if (q.isEmpty()) return null
            q
        } else {
            trimmed
        }

        val params = parseQuery(query)
        val hostID = params["h"]?.takeIf { it.isNotEmpty() } ?: return null
        val secret = params["s"]?.let(Base64Url::decode) ?: return null
        if (secret.size != RemoteCrypto.KEY_LENGTH) return null

        return PairPayload(
            protocolVersion = params["v"]?.toIntOrNull() ?: RemoteProtocolInfo.VERSION,
            hostID = hostID,
            hostName = params["n"] ?: "Spettro",
            hostKind = params["k"] ?: "app",
            secret = secret,
            address = params["a"]?.takeIf { it.isNotEmpty() },
            port = params["p"]?.toIntOrNull(),
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (piece in query.split('&')) {
            if (piece.isEmpty()) continue
            val idx = piece.indexOf('=')
            val key: String
            val value: String
            if (idx < 0) {
                key = urlDecode(piece)
                value = ""
            } else {
                key = urlDecode(piece.substring(0, idx))
                value = urlDecode(piece.substring(idx + 1))
            }
            if (key !in out) out[key] = value
        }
        return out
    }

    private fun urlDecode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }
}

// MARK: - Client-side credential

/** What a phone stores after pairing: which host, and the key that proves it. */
class RemoteCredential(
    val hostID: String,
    val hostName: String,
    val hostKind: String,
    /** The durable 32-byte device key returned by a successful pair. */
    val deviceKey: ByteArray,
    /** ISO-8601. */
    val pairedAt: String,
    /** Last address the host was reachable at — a hint while discovery runs. */
    val lastAddress: String? = null,
    val lastPort: Int? = null,
) {
    fun copyWithEndpoint(address: String?, port: Int?): RemoteCredential =
        RemoteCredential(hostID, hostName, hostKind, deviceKey, pairedAt, address, port)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteCredential) return false
        return hostID == other.hostID &&
            hostName == other.hostName &&
            hostKind == other.hostKind &&
            deviceKey.contentEquals(other.deviceKey) &&
            pairedAt == other.pairedAt &&
            lastAddress == other.lastAddress &&
            lastPort == other.lastPort
    }

    override fun hashCode(): Int = hostID.hashCode() * 31 + deviceKey.contentHashCode()

    override fun toString(): String =
        "RemoteCredential(hostID=$hostID, hostName=$hostName, kind=$hostKind, lastAddress=$lastAddress, lastPort=$lastPort)"
}
