package to.eyed.spettro.mobile.coordinator.headless

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionItem
import to.eyed.spettro.mobile.core.acp.AcpQuestionOption
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.core.headless.HeadlessAskUser

/**
 * Adapters between the Protocol A prompt shapes ([HeadlessAskUser],
 * [HeadlessApproval]) and the ACP models the shared QuestionSheet /
 * PermissionSheet composables render, so headless mode reuses the exact same
 * sheet UI as Protocol B.
 *
 * Protocol A's wire has no option ids — answers are option *labels* keyed by
 * question *header*. The adapters mint positional ids ("q-<i>" / "opt-<j>")
 * on the way out and resolve them back to headers/labels on the way in.
 */

/** Synthetic session id used where ACP models require one. */
const val HEADLESS_ACP_SESSION_ID = "headless"

/** Option ids for the synthesized approval permission request. */
const val HEADLESS_APPROVE_ONCE = "allow-once"
const val HEADLESS_APPROVE_ALWAYS = "allow-always"
const val HEADLESS_APPROVE_DENY = "deny"

/**
 * `_meta` key flagging that a deny may carry a free-text "do this instead"
 * (the [HeadlessConnection.approve] `instead` parameter).
 */
const val HEADLESS_META_ALLOWS_INSTEAD = "spettro.app/allowsInstead"

/** Renders this ask-user form through the AcpQuestionRequest-based sheet. */
fun askUserToAcpQuestion(form: HeadlessAskUser): AcpQuestionRequest =
    AcpQuestionRequest(
        sessionId = HEADLESS_ACP_SESSION_ID,
        context = form.context,
        questions = form.questions.mapIndexed { qIndex, question ->
            AcpQuestionItem(
                id = "q-$qIndex",
                header = question.header.ifEmpty { "Question ${qIndex + 1}" },
                question = question.question,
                options = question.options.mapIndexed { oIndex, option ->
                    AcpQuestionOption(
                        id = "opt-$oIndex",
                        label = option.label,
                        description = option.description,
                        isRecommended = option.isRecommended,
                    )
                },
                multiSelect = question.multiSelect,
                // No options means free text is the only way to answer.
                allowCustomInput = question.allowFreeResponse || question.options.isEmpty(),
            )
        },
        transport = AcpQuestionRequest.Transport.ExtensionCall(form.version),
    )

/**
 * Converts the sheet's answers back to the Protocol A header→answer map for
 * `POST /ask-user` (v2): positional ids from [askUserToAcpQuestion] resolve
 * to the original headers and option labels, multi-select labels comma-join,
 * free text passes through (appended after any selected labels). Questions
 * absent from [answers] — or answered with nothing — are omitted, which the
 * server reports to the model as "skipped".
 */
fun acpAnswersToHeaderMap(
    request: HeadlessAskUser,
    answers: List<AcpQuestionAnswer>,
): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for (answer in answers) {
        val qIndex = answer.questionId.removePrefix("q-").toIntOrNull() ?: continue
        val question = request.questions.getOrNull(qIndex) ?: continue
        val labels = answer.optionIds.mapNotNull { optionId ->
            val oIndex = optionId.removePrefix("opt-").toIntOrNull() ?: return@mapNotNull null
            question.options.getOrNull(oIndex)?.label
        }
        val parts = labels.toMutableList()
        answer.text?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (parts.isEmpty()) continue
        var value = parts.joinToString(", ")
        answer.notes?.takeIf { it.isNotBlank() }?.let { value += " — $it" }
        result[question.header] = value
    }
    return result
}

/**
 * Renders this approval through the AcpPermissionRequest-based sheet:
 * allow-once / allow-always / deny options whose optionIds double as the
 * `POST /approval` decision strings, plus [HEADLESS_META_ALLOWS_INSTEAD] in
 * `_meta` so the sheet can offer a "do this instead" text field on deny.
 */
fun approvalToAcpPermission(approval: HeadlessApproval): AcpPermissionRequest =
    AcpPermissionRequest(
        sessionId = HEADLESS_ACP_SESSION_ID,
        title = approval.command,
        toolKind = "execute",
        rawInput = buildJsonObject {
            put("command", approval.command)
            approval.reason?.let { put("reason", it) }
        },
        options = listOf(
            AcpPermissionRequest.Option(
                optionId = HEADLESS_APPROVE_ONCE,
                name = "Allow once",
                kind = "allow_once",
            ),
            AcpPermissionRequest.Option(
                optionId = HEADLESS_APPROVE_ALWAYS,
                name = "Always allow",
                kind = "allow_always",
            ),
            AcpPermissionRequest.Option(
                optionId = HEADLESS_APPROVE_DENY,
                name = "Deny",
                kind = "reject_once",
            ),
        ),
        meta = buildJsonObject { put(HEADLESS_META_ALLOWS_INSTEAD, true) },
    )
