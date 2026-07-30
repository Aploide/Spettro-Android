package to.eyed.spettro.mobile.core.acp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// MARK: - Wire keys

object AcpQuestionMeta {
    /** Carries the outbound question payload on a permission request. */
    const val QUESTION = "spettro.app/question"

    /** Carries the structured answer back on the permission response. */
    const val ANSWER = "spettro.app/questionAnswer"

    /**
     * The synthetic option a permission-transport question offers when it also
     * accepts free text. Selecting it means "collect text from me".
     */
    const val CUSTOM_OPTION_ID = "custom"
}

// MARK: - Permission request (agent -> client)

/** A `session/request_permission` put to this client. */
data class AcpPermissionRequest(
    val sessionId: String,
    val title: String,
    val toolKind: String? = null,
    val rawInput: JsonElement? = null,
    val options: List<Option> = emptyList(),
    /**
     * The request's `_meta` bag. Carries `spettro.app/question` when the CLI
     * is putting an agent question through this transport.
     */
    val meta: JsonObject? = null,
) {
    data class Option(
        val optionId: String,
        val name: String,
        /** allow_once | allow_always | reject_once | reject_always. */
        val kind: String,
        /**
         * The answer the agent would pick, flagged when this request is really
         * an `ask-user` question walked over the permission transport.
         */
        val isRecommended: Boolean = false,
        /**
         * The synthetic "type my own answer" option appended to such a
         * question — not one of the agent's answers.
         */
        val isCustomInput: Boolean = false,
    )

    /** True when this request is really a question walked over the permission transport. */
    val isQuestion: Boolean get() = meta?.get(AcpQuestionMeta.QUESTION).asObj != null

    companion object {
        fun parse(params: JsonObject): AcpPermissionRequest? {
            val sessionId = params["sessionId"].asString ?: return null
            val toolCall = params["toolCall"].asObj ?: return null
            val options = (params["options"].asArr ?: emptyList()).mapNotNull { opt ->
                val o = opt.asObj ?: return@mapNotNull null
                val id = o["optionId"].asString ?: return@mapNotNull null
                val name = o["name"].asString ?: return@mapNotNull null
                val optMeta = o["_meta"].asObj
                Option(
                    optionId = id,
                    name = name,
                    kind = o["kind"].asString ?: "",
                    isRecommended = optMeta?.get("spettro.app/isRecommended").asBool ?: false,
                    isCustomInput = optMeta?.get("spettro.app/isCustomInput").asBool ?: false,
                )
            }
            return AcpPermissionRequest(
                sessionId = sessionId,
                title = toolCall["title"].asString ?: "Permission requested",
                toolKind = toolCall["kind"].asString,
                rawInput = toolCall["rawInput"],
                options = options,
                meta = params["_meta"].asObj,
            )
        }
    }
}

// MARK: - Questions

/** One selectable answer to a question. */
data class AcpQuestionOption(
    val id: String,
    val label: String,
    /** The muted line under the label. */
    val description: String? = null,
    /** Preformatted content shown beside the option list. */
    val preview: String? = null,
    /** The answer the agent would pick. Marked, never preselected. */
    val isRecommended: Boolean = false,
) {
    companion object {
        fun parse(element: JsonElement?): AcpQuestionOption? {
            val obj = element.asObj ?: return null
            val id = obj["id"].asString ?: return null
            val label = obj["label"].asString ?: return null
            return AcpQuestionOption(
                id = id,
                label = label,
                description = obj["description"].asString.trimmedOrNull(),
                preview = obj["preview"].asString.trimmedOrNull(),
                isRecommended = obj["isRecommended"].asBool ?: false,
            )
        }
    }
}

/** One question of a form. */
data class AcpQuestionItem(
    val id: String,
    /** The short tab label, e.g. "Database". */
    val header: String,
    val question: String,
    val options: List<AcpQuestionOption> = emptyList(),
    /** More than one option may be chosen. */
    val multiSelect: Boolean = false,
    /** The user may answer in their own words instead of (or beside) an option. */
    val allowCustomInput: Boolean = false,
) {
    companion object {
        fun parse(element: JsonElement?, index: Int): AcpQuestionItem? {
            val obj = element.asObj ?: return null
            val question = obj["question"].asString.trimmedOrNull()
            val options = (obj["options"].asArr ?: emptyList()).mapNotNull(AcpQuestionOption::parse)
            // A question with neither text nor options is nothing anyone can answer.
            if (question == null && options.isEmpty()) return null
            return AcpQuestionItem(
                id = obj["id"].asString.trimmedOrNull() ?: "q-$index",
                header = obj["header"].asString.trimmedOrNull() ?: "Question ${index + 1}",
                question = question ?: "",
                options = options,
                multiSelect = obj["multiSelect"].asBool ?: false,
                // A question with nothing to pick from can only be answered in
                // the user's own words, whatever the flag says.
                allowCustomInput = (obj["allowCustomInput"].asBool ?: false) || options.isEmpty(),
            )
        }
    }
}

/** A question (or whole form) the agent is waiting on an answer for. */
data class AcpQuestionRequest(
    val sessionId: String,
    /** One line of background applying to the whole form. */
    val context: String? = null,
    val questions: List<AcpQuestionItem>,
    val transport: Transport,
) {
    /** How the question reached us, which decides the shape of the reply. */
    sealed class Transport {
        /** `_spettro/question/ask`; version 2+ carries the whole form in `questions[]`. */
        data class ExtensionCall(val version: Int) : Transport()

        /** `session/request_permission` with the payload mirrored into `_meta`. */
        data class Permission(val customOptionId: String?) : Transport()
    }

    companion object {
        /** Parses the `_spettro/question/ask` params. */
        fun parse(params: JsonObject): AcpQuestionRequest? =
            fromPayload(params, Transport.ExtensionCall(params["version"].asInt ?: 1))

        /**
         * The question a `session/request_permission` is really asking, or
         * null for an ordinary permission prompt.
         */
        fun fromPermission(permission: AcpPermissionRequest): AcpQuestionRequest? {
            val payload = permission.meta?.get(AcpQuestionMeta.QUESTION).asObj ?: return null
            val custom = permission.options.firstOrNull { it.isCustomInput }?.optionId
                ?: if (payload["allowCustomInput"].asBool == true) AcpQuestionMeta.CUSTOM_OPTION_ID else null
            return fromPayload(payload, Transport.Permission(custom))
        }

        private fun fromPayload(payload: JsonObject, transport: Transport): AcpQuestionRequest? {
            val questions = questionsFrom(payload)
            if (questions.isEmpty()) return null
            return AcpQuestionRequest(
                sessionId = payload["sessionId"].asString ?: "",
                context = payload["context"].asString.trimmedOrNull(),
                questions = questions,
                transport = transport,
            )
        }

        /**
         * The form's questions, or the single question a version 1 payload
         * spells out in its flat fields.
         */
        private fun questionsFrom(payload: JsonObject): List<AcpQuestionItem> {
            val array = payload["questions"].asArr
            if (array != null && array.isNotEmpty()) {
                val parsed = array.mapIndexedNotNull { index, el -> AcpQuestionItem.parse(el, index) }
                if (parsed.isNotEmpty()) return parsed
            }
            return listOfNotNull(AcpQuestionItem.parse(payload, 0))
        }
    }
}

// MARK: - Answer

/**
 * One question's answer, in the plain synthesized shape the Spettro Remote
 * host reads (`RemoteProtocol.swift`); the host translates it to the
 * kind-tagged ACP reply. A question with no answer is left out of the reply
 * entirely.
 */
data class AcpQuestionAnswer(
    val questionId: String,
    /** Chosen option ids, in the order the question listed them. */
    val optionIds: List<String> = emptyList(),
    /** The user's own words — alone, or alongside a choice. */
    val text: String? = null,
    /** A free-text annotation on the answer, not an answer itself. */
    val notes: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("questionId", questionId)
        put("optionIds", buildJsonArray { optionIds.forEach { add(it) } })
        text?.let { put("text", it) }
        notes?.let { put("notes", it) }
    }
}
