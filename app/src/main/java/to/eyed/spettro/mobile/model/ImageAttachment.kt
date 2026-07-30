package to.eyed.spettro.mobile.model

import androidx.compose.runtime.Immutable

import java.util.UUID
import to.eyed.spettro.mobile.core.acp.AcpContentBlock

/**
 * One image attached to a prompt, held as standard (padded) base64 so the
 * model layer stays JVM-testable — no Android types here. Downsampling and
 * JPEG encoding live in ImageAttachmentAndroid.kt.
 */
@Immutable
data class ImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    /** Standard (padded) base64 of the encoded image bytes. */
    val base64Data: String,
    val mimeType: String = "image/jpeg",
) {
    /** The ACP prompt block for this attachment. */
    fun toContentBlock(): AcpContentBlock = AcpContentBlock.Image(base64Data, mimeType)
}
