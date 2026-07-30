package to.eyed.spettro.mobile.core.headless

/**
 * Parsing helpers for the headless connect form: endpoint strings the user
 * types ("host:port", "http://host:port", bare host) and pastes of the CLI's
 * stdout lines (`SPETTRO_TOKEN=...` / `SPETTRO_PORT=...`).
 */
object HeadlessEndpoint {
    /** Default control-plane port when the user omits one. */
    const val DEFAULT_PORT: Int = 7878

    private val TOKEN_LINE = Regex("""SPETTRO_TOKEN\s*=\s*([0-9a-fA-F]{32})""")
    private val PORT_LINE = Regex("""SPETTRO_PORT\s*=\s*(\d{1,5})""")
    private val BARE_TOKEN = Regex("""^[0-9a-fA-F]{32}$""")

    /**
     * Normalizes user endpoint input to a base URL "http://host:port".
     * Accepts "host:port", "http(s)://host:port", bare host (default port
     * 7878), bracketed IPv6, and tolerates a trailing slash/path. Returns null
     * when the input cannot be an endpoint.
     */
    fun parse(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null

        var scheme = "http"
        when {
            s.startsWith("http://", ignoreCase = true) -> s = s.substring(7)
            s.startsWith("https://", ignoreCase = true) -> {
                scheme = "https"
                s = s.substring(8)
            }
        }
        // Drop any path component and trailing slashes.
        s = s.substringBefore('/').trim()
        if (s.isEmpty()) return null

        // Bracketed IPv6: [::1] or [::1]:7878
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end <= 1) return null
            val host = s.substring(0, end + 1)
            val rest = s.substring(end + 1)
            val port = when {
                rest.isEmpty() -> DEFAULT_PORT
                rest.startsWith(":") -> rest.substring(1).toIntOrNull() ?: return null
                else -> return null
            }
            if (port !in 1..65535) return null
            return "$scheme://$host:$port"
        }

        // Unbracketed IPv6 (multiple colons): treat the whole thing as a host.
        if (s.count { it == ':' } > 1) {
            return "$scheme://[$s]:$DEFAULT_PORT"
        }

        val colon = s.indexOf(':')
        if (colon >= 0) {
            val host = s.substring(0, colon)
            val port = s.substring(colon + 1).toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535) return null
            return "$scheme://$host:$port"
        }
        return "$scheme://$s:$DEFAULT_PORT"
    }

    /** Result of scanning pasted CLI output for credentials. */
    data class TokenPaste(val token: String?, val port: Int?) {
        val isEmpty: Boolean get() = token == null && port == null
    }

    /**
     * Extracts (token, port) from a paste of the CLI's stdout:
     * `SPETTRO_TOKEN=<32 hex>` / `SPETTRO_PORT=<port>` in any order, embedded
     * in arbitrary surrounding text. A paste that is exactly a 32-hex-char
     * string is accepted as a bare token. Fields absent from the paste are null.
     */
    fun parseTokenPaste(text: String): TokenPaste {
        val trimmed = text.trim()
        val token = TOKEN_LINE.find(trimmed)?.groupValues?.get(1)
            ?: BARE_TOKEN.find(trimmed)?.value
        val port = PORT_LINE.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
        return TokenPaste(token = token, port = port)
    }
}
