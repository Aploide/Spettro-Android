package to.eyed.spettro.mobile.core.acp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import to.eyed.spettro.mobile.core.SpettroJson

/**
 * Manual JSON accessors mirroring the Swift `JSONValue` accessors: typed reads
 * that return null on any shape mismatch, so hand-rolled parsing of the ACP
 * discriminated unions stays terse and never throws.
 */

internal val JsonElement?.asObj: JsonObject? get() = this as? JsonObject

internal val JsonElement?.asArr: JsonArray? get() = this as? JsonArray

/** String only for an actual JSON string, matching Swift's `stringValue`. */
internal val JsonElement?.asString: String?
    get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Int for a JSON number that is exactly integral. */
internal val JsonElement?.asInt: Int?
    get() {
        val p = this as? JsonPrimitive ?: return null
        if (p.isString) return null
        p.content.toIntOrNull()?.let { return it }
        val d = p.content.toDoubleOrNull() ?: return null
        val i = d.toInt()
        return if (i.toDouble() == d) i else null
    }

internal val JsonElement?.asDouble: Double?
    get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

internal val JsonElement?.asBool: Boolean?
    get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()

/** Trimmed text, or null when the string is blank — an empty wire field and an absent one are handled the same way. */
internal fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * This value re-encoded as a compact JSON string; the raw string for a string
 * primitive; null otherwise. Mirrors Swift's `JSONValue.encodedJSONString`.
 */
fun JsonElement?.encodedJsonString(): String? = when (this) {
    is JsonObject -> SpettroJson.encodeToString(JsonObject.serializer(), this)
    is JsonPrimitive -> if (isString) content else null
    else -> null
}
