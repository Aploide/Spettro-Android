package to.eyed.spettro.mobile.core

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for every wire format in the app (Spettro Remote
 * JSON-RPC, ACP payloads, and the CLI HTTP/SSE plane).
 */
val SpettroJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = true
}
