package to.eyed.spettro.mobile.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import to.eyed.spettro.mobile.core.B64

/**
 * Android-side image processing for attachments, kept out of the pure model
 * so unit tests never touch android.graphics. Matches the iOS pipeline:
 * downsample to a longest edge of 1568 px, re-encode as JPEG at quality 85.
 */
object ImageProcessing {
    const val MAX_EDGE = 1568
    const val JPEG_QUALITY = 85

    /**
     * Decodes [bytes], downsamples so the longest edge is at most [maxEdge],
     * and re-encodes as JPEG at [quality]. Null when the bytes aren't a
     * decodable image.
     */
    fun downsampleToJpeg(
        bytes: ByteArray,
        maxEdge: Int = MAX_EDGE,
        quality: Int = JPEG_QUALITY,
    ): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Power-of-two pre-scale keeps peak memory down on large photos.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        return if (ok) out.toByteArray() else null
    }

    /**
     * The full pipeline: downsample + JPEG-encode [bytes] and wrap them as an
     * [ImageAttachment] ready for the composer. Null for undecodable input.
     */
    fun attachmentFrom(bytes: ByteArray): ImageAttachment? =
        downsampleToJpeg(bytes)?.let {
            ImageAttachment(base64Data = B64.encodeStd(it), mimeType = "image/jpeg")
        }
}
