package to.eyed.spettro.mobile.core.acp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The content variants we send to the agent in a prompt (and read back off the
 * wire). Text and images are all this app currently produces.
 *
 * Mirrors `ACPContentBlock` in ACPProtocol.swift.
 */
sealed class AcpContentBlock {

    data class Text(val text: String) : AcpContentBlock()

    /** [data] is standard (padded) base64 of the image bytes. */
    data class Image(val data: String, val mimeType: String) : AcpContentBlock()

    /** The ACP wire encoding for outbound prompt blocks. */
    fun toJson(): JsonObject = when (this) {
        is Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        is Image -> buildJsonObject {
            put("type", "image")
            put("data", data)
            put("mimeType", mimeType)
        }
    }

    companion object {
        /** Parses one wire content block; null for an unsupported type. */
        fun parse(element: JsonElement?): AcpContentBlock? {
            val obj = element.asObj ?: return null
            return when (obj["type"].asString) {
                "text" -> obj["text"].asString?.let(::Text)
                "image" -> {
                    val data = obj["data"].asString ?: return null
                    val mime = obj["mimeType"].asString ?: return null
                    Image(data, mime)
                }
                else -> null
            }
        }
    }
}
