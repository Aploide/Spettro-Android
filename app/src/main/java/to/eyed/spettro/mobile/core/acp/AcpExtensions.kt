package to.eyed.spettro.mobile.core.acp

import java.util.Locale
import kotlinx.serialization.json.JsonElement

/**
 * The client half of Spettro's `_spettro` ACP extension surface: account /
 * subscription state, provider API-key management, local endpoints, and the
 * model catalog. Mirrors ACPExtensions.swift.
 */

// MARK: - Method names

object AcpExtensionMethod {
    const val ACCOUNT_STATUS = "_spettro/account/status"
    const val ACCOUNT_LOGIN_START = "_spettro/account/login/start"
    const val ACCOUNT_LOGIN_POLL = "_spettro/account/login/poll"
    const val ACCOUNT_LOGIN_CANCEL = "_spettro/account/login/cancel"
    const val ACCOUNT_LOGOUT = "_spettro/account/logout"

    const val PROVIDERS_LIST = "_spettro/providers/list"
    const val PROVIDERS_CONNECT = "_spettro/providers/connect"
    const val PROVIDERS_DISCONNECT = "_spettro/providers/disconnect"
    const val LOCAL_PROBE = "_spettro/providers/local/probe"
    const val LOCAL_ADD = "_spettro/providers/local/add"
    const val LOCAL_REMOVE = "_spettro/providers/local/remove"
    const val MODELS_LIST = "_spettro/models/list"
    const val MODELS_FAVORITE = "_spettro/models/favorite"

    /** Agent -> client: pushed when account state changes. */
    const val ACCOUNT_UPDATE = "_spettro/account/update"

    /** Agent -> client request served by this app: a whole ask-user form. */
    const val QUESTION_ASK = "_spettro/question/ask"
}

// MARK: - Subscription plans

enum class SubscriptionPlan(val label: String) {
    FREE("Free"),
    LITE("Lite"),
    PLUS("Plus"),
    PRO("Pro"),
    MAX("Max"),
    OTHER("Plan");

    companion object {
        /** Null for a blank plan string (signed out). */
        fun from(raw: String?): SubscriptionPlan? {
            val plan = raw?.trim()?.lowercase(Locale.US) ?: return null
            if (plan.isEmpty()) return null
            return when (plan) {
                "free" -> FREE
                "lite" -> LITE
                "plus" -> PLUS
                "pro" -> PRO
                "max" -> MAX
                else -> OTHER
            }
        }
    }
}

// MARK: - Account

/** The user's Spettro Subscription state. Carries no secret material. */
data class AcpAccountStatus(
    val signedIn: Boolean = false,
    val email: String? = null,
    val plan: String? = null,
    val planStatus: String? = null,
    val creditsUsed: Double? = null,
    val creditLimit: Double? = null,
    val remainingCredits: Double? = null,
    val modelCount: Int = 0,
    val pricingUrl: String? = null,
    val login: AcpLoginStatus? = null,
    /** True when the values came from the CLI's on-disk cache (backend unreachable). */
    val stale: Boolean = false,
) {
    /**
     * A signed-in account with no explicit plan is on the free tier — the same
     * rule the TUI's badge applies. Empty when signed out.
     */
    val effectivePlan: String
        get() {
            if (!signedIn) return ""
            val raw = (plan ?: "").trim()
            return if (raw.isEmpty()) "free" else raw.lowercase(Locale.US)
        }

    val subscriptionPlan: SubscriptionPlan? get() = SubscriptionPlan.from(effectivePlan)

    /**
     * Fraction of the monthly credit budget still available, 0..1. Prefers the
     * backend's own `remainingCredits` over `limit - used`: the two can
     * legitimately disagree, and "remaining" is what the user is billed against.
     */
    val remainingFraction: Double?
        get() {
            val limit = creditLimit ?: return null
            if (limit <= 0) return null
            remainingCredits?.let { return (it / limit).coerceIn(0.0, 1.0) }
            val used = creditsUsed ?: return null
            return (1 - used / limit).coerceIn(0.0, 1.0)
        }

    companion object {
        fun parse(element: JsonElement?): AcpAccountStatus {
            val obj = element.asObj ?: return AcpAccountStatus()
            return AcpAccountStatus(
                signedIn = obj["signedIn"].asBool ?: false,
                email = obj["email"].asString,
                plan = obj["plan"].asString,
                planStatus = obj["planStatus"].asString,
                creditsUsed = obj["creditsUsed"].asDouble,
                creditLimit = obj["creditLimit"].asDouble,
                remainingCredits = obj["remainingCredits"].asDouble,
                modelCount = obj["modelCount"].asInt ?: 0,
                pricingUrl = obj["pricingUrl"].asString,
                login = obj["login"]?.let(AcpLoginStatus::parse),
                stale = obj["stale"].asBool ?: false,
            )
        }
    }
}

/** The state of a device-flow login. */
data class AcpLoginStatus(
    val loginId: String? = null,
    val rawStatus: String = "idle",
    val browserUrl: String? = null,
    val error: String? = null,
) {
    enum class State { IDLE, STARTING, PENDING, COMPLETE, EXPIRED, CANCELLED, ERROR, UNKNOWN }

    val state: State
        get() = when (rawStatus) {
            "idle" -> State.IDLE
            "starting" -> State.STARTING
            "pending" -> State.PENDING
            "complete" -> State.COMPLETE
            "expired" -> State.EXPIRED
            "cancelled" -> State.CANCELLED
            "error" -> State.ERROR
            else -> State.UNKNOWN
        }

    companion object {
        fun parse(element: JsonElement?): AcpLoginStatus {
            val obj = element.asObj ?: return AcpLoginStatus()
            return AcpLoginStatus(
                loginId = obj["loginId"].asString,
                rawStatus = obj["status"].asString ?: "idle",
                browserUrl = obj["browserUrl"].asString,
                error = obj["error"].asString,
            )
        }
    }
}

// MARK: - Providers

/** One provider in the connect list. */
data class AcpProviderEntry(
    val id: String,
    val name: String,
    val envKey: String? = null,
    val connected: Boolean = false,
    val suggested: Boolean = false,
    val modelCount: Int = 0,
) {
    companion object {
        fun parse(element: JsonElement?): AcpProviderEntry? {
            val obj = element.asObj ?: return null
            return AcpProviderEntry(
                id = obj["id"].asString ?: return null,
                name = obj["name"].asString ?: return null,
                envKey = obj["envKey"].asString,
                connected = obj["connected"].asBool ?: false,
                suggested = obj["suggested"].asBool ?: false,
                modelCount = obj["modelCount"].asInt ?: 0,
            )
        }
    }
}

/** One connected OpenAI-compatible local server. */
data class AcpLocalEndpoint(
    val endpoint: String,
    val name: String,
    val hasKey: Boolean = false,
    val modelCount: Int = 0,
) {
    /** "localhost:1234" — the endpoint without its scheme, as the TUI labels it. */
    val shortHost: String
        get() = endpoint.removePrefix("https://").removePrefix("http://")

    companion object {
        fun parse(element: JsonElement?): AcpLocalEndpoint? {
            val obj = element.asObj ?: return null
            val endpoint = obj["endpoint"].asString ?: return null
            return AcpLocalEndpoint(
                endpoint = endpoint,
                name = obj["name"].asString ?: endpoint,
                hasKey = obj["hasKey"].asBool ?: false,
                modelCount = obj["modelCount"].asInt ?: 0,
            )
        }
    }
}

data class AcpProvidersList(
    val providers: List<AcpProviderEntry> = emptyList(),
    val local: List<AcpLocalEndpoint> = emptyList(),
    val subscription: AcpProviderEntry = AcpProviderEntry(id = "spettro", name = "Spettro"),
) {
    val connectedCount: Int
        get() = providers.count { it.connected } + local.size + (if (subscription.connected) 1 else 0)

    /** True when nothing at all is configured — route into onboarding. */
    val isEmpty: Boolean get() = connectedCount == 0

    companion object {
        fun parse(element: JsonElement?): AcpProvidersList {
            val obj = element.asObj ?: return AcpProvidersList()
            return AcpProvidersList(
                providers = (obj["providers"].asArr ?: emptyList()).mapNotNull(AcpProviderEntry::parse),
                local = (obj["local"].asArr ?: emptyList()).mapNotNull(AcpLocalEndpoint::parse),
                subscription = AcpProviderEntry.parse(obj["subscription"])
                    ?: AcpProviderEntry(id = "spettro", name = "Spettro"),
            )
        }
    }
}

data class AcpConnectResult(
    val connected: Boolean = false,
    val modelCount: Int = 0,
    val activeModel: String? = null,
) {
    companion object {
        fun parse(element: JsonElement?): AcpConnectResult {
            val obj = element.asObj ?: return AcpConnectResult()
            return AcpConnectResult(
                connected = obj["connected"].asBool ?: false,
                modelCount = obj["modelCount"].asInt ?: 0,
                activeModel = obj["activeModel"].asString,
            )
        }
    }
}

data class AcpLocalProbeResult(
    val endpoint: String,
    val name: String,
    val models: List<AcpModelEntry> = emptyList(),
) {
    companion object {
        fun parse(element: JsonElement?): AcpLocalProbeResult? {
            val obj = element.asObj ?: return null
            val endpoint = obj["endpoint"].asString ?: return null
            return AcpLocalProbeResult(
                endpoint = endpoint,
                name = obj["name"].asString ?: endpoint,
                models = (obj["models"].asArr ?: emptyList()).mapNotNull(AcpModelEntry::parse),
            )
        }
    }
}

// MARK: - Models

data class AcpModelEntry(
    val provider: String,
    val providerName: String,
    val name: String,
    val displayName: String,
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val toolCall: Boolean = false,
    val context: Int = 0,
    val local: Boolean = false,
    val favorite: Boolean = false,
    val active: Boolean = false,
) {
    val id: String get() = "$provider:$name"

    /** "128k" / "1.0M" — the context window in the compact form the TUI uses. */
    val contextLabel: String?
        get() {
            if (context <= 0) return null
            if (context >= 1_000_000) return String.format(Locale.US, "%.1fM", context / 1_000_000.0)
            if (context >= 1_000) return "${context / 1_000}k"
            return "$context"
        }

    companion object {
        fun parse(element: JsonElement?): AcpModelEntry? {
            val obj = element.asObj ?: return null
            val provider = obj["provider"].asString ?: return null
            val name = obj["name"].asString ?: return null
            return AcpModelEntry(
                provider = provider,
                providerName = obj["providerName"].asString ?: provider,
                name = name,
                displayName = obj["displayName"].asString ?: name,
                vision = obj["vision"].asBool ?: false,
                reasoning = obj["reasoning"].asBool ?: false,
                toolCall = obj["toolCall"].asBool ?: false,
                context = obj["context"].asInt ?: 0,
                local = obj["local"].asBool ?: false,
                favorite = obj["favorite"].asBool ?: false,
                active = obj["active"].asBool ?: false,
            )
        }
    }
}

data class AcpModelsList(
    val models: List<AcpModelEntry> = emptyList(),
    val activeProvider: String? = null,
    val activeModel: String? = null,
) {
    data class ProviderGroup(val provider: String, val models: List<AcpModelEntry>)

    /**
     * Models grouped by provider in first-seen order, favorites first within
     * each group, then by display name — the order the model picker renders.
     */
    val grouped: List<ProviderGroup>
        get() {
            val order = mutableListOf<String>()
            val byProvider = mutableMapOf<String, MutableList<AcpModelEntry>>()
            for (model in models) {
                if (model.providerName !in byProvider) order.add(model.providerName)
                byProvider.getOrPut(model.providerName) { mutableListOf() }.add(model)
            }
            return order.map { providerName ->
                val sorted = (byProvider[providerName] ?: emptyList()).sortedWith(
                    compareByDescending<AcpModelEntry> { it.favorite }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                )
                ProviderGroup(providerName, sorted)
            }
        }

    companion object {
        fun parse(element: JsonElement?): AcpModelsList {
            val obj = element.asObj ?: return AcpModelsList()
            return AcpModelsList(
                models = (obj["models"].asArr ?: emptyList()).mapNotNull(AcpModelEntry::parse),
                activeProvider = obj["activeProvider"].asString,
                activeModel = obj["activeModel"].asString,
            )
        }
    }
}
