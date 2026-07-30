package to.eyed.spettro.mobile.core

import android.util.Base64

/**
 * Base64 helpers matching the Spettro Remote conventions:
 * - challenges, proofs, device keys, QR pairing secrets: base64url, no padding
 * - image attachments: standard base64 with padding
 */
object B64 {
    fun encodeUrl(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun decodeUrl(text: String): ByteArray? = try {
        Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        null
    }

    fun encodeStd(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeStd(text: String): ByteArray? = try {
        Base64.decode(text, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        null
    }
}
