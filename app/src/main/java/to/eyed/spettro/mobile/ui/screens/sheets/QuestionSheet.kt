package to.eyed.spettro.mobile.ui.screens.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionItem
import to.eyed.spettro.mobile.core.acp.AcpQuestionOption
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The ask-user form: up to four related questions the agent puts to the user
 * together, each single- or multi-select, each optionally taking the user's
 * own words. Port of `QuestionSheet.swift` + docs/33-question-sheet.md.
 *
 * Nothing is preselected. The recommended option is marked, never chosen for
 * the user. Questions left alone are simply absent from the submitted
 * answers — the CLI reports them to the model as unanswered, never defaulted.
 *
 * Swiping the sheet away calls [onDismiss] only; the form stays pending and
 * the caller decides whether to re-present or decline. [onDecline] refuses
 * the whole form — declining is all-or-nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionSheet(
    request: AcpQuestionRequest,
    onSubmit: (answers: List<AcpQuestionAnswer>) -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        QuestionSheetContent(
            request = request,
            onSubmit = onSubmit,
            onDecline = onDecline,
            modifier = Modifier.fillMaxHeight(0.94f),
        )
    }
}

@Composable
internal fun QuestionSheetContent(
    request: AcpQuestionRequest,
    onSubmit: (answers: List<AcpQuestionAnswer>) -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val questions = request.questions
    val reviewIndex = questions.size
    val hasReview = questions.size > 1

    // The user's working answers, keyed by question id. Plain Serializable
    // maps in saveable state, copied on write: moving between tabs changes an
    // index and nothing else, so a revisited question comes back exactly as
    // it was left — including across process death.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selections by rememberSaveable { mutableStateOf(HashMap<String, ArrayList<String>>()) }
    var customText by rememberSaveable { mutableStateOf(HashMap<String, String>()) }
    var notes by rememberSaveable { mutableStateOf(HashMap<String, String>()) }
    var otherOpen by rememberSaveable { mutableStateOf(HashSet<String>()) }
    // Which option previews are expanded ("questionId:optionId") — pure
    // presentation, not worth surviving process death.
    var expandedPreviews by remember { mutableStateOf(setOf<String>()) }

    fun selectedIds(q: AcpQuestionItem): List<String> = selections[q.id].orEmpty()
    fun custom(q: AcpQuestionItem): String = customText[q.id].orEmpty()
    fun answered(q: AcpQuestionItem): Boolean =
        selectedIds(q).isNotEmpty() || custom(q).isNotBlank()

    val hasAnyAnswer = questions.any { answered(it) }
    val allAnswered = questions.all { answered(it) }

    /**
     * One [AcpQuestionAnswer] per answered question. Selections come out in
     * option order, never the order they were ticked, so the same set of
     * boxes always produces the same answer. Unanswered questions are
     * omitted entirely — never defaulted.
     */
    fun buildAnswers(): List<AcpQuestionAnswer> = questions.mapNotNull { q ->
        val picked = selectedIds(q).toSet()
        val ordered = q.options.map { it.id }.filter { it in picked }
        val text = custom(q).trim()
        if (ordered.isEmpty() && text.isEmpty()) return@mapNotNull null
        val note = notes[q.id]?.trim().orEmpty()
        AcpQuestionAnswer(
            questionId = q.id,
            optionIds = ordered,
            text = text.ifEmpty { null },
            notes = if (q.multiSelect && note.isNotEmpty()) note else null,
        )
    }

    fun choose(q: AcpQuestionItem, option: AcpQuestionOption) {
        val current = ArrayList(selectedIds(q))
        if (q.multiSelect) {
            if (!current.remove(option.id)) current.add(option.id)
            selections = HashMap(selections).apply { put(q.id, current) }
            return
        }
        selections = HashMap(selections).apply { put(q.id, arrayListOf(option.id)) }
        customText = HashMap(customText).apply { remove(q.id) }
        // A form of one single-select question answers itself: there is no
        // second question to move to, so picking an option is the whole
        // interaction. Not while the free-text field is open — someone who
        // opened it has more to say.
        if (questions.size == 1 && q.id !in otherOpen) {
            onSubmit(listOf(AcpQuestionAnswer(questionId = q.id, optionIds = listOf(option.id))))
        }
    }

    fun setCustom(q: AcpQuestionItem, text: String) {
        customText = HashMap(customText).apply { put(q.id, text) }
        // On a single-select question the user's own words replace the pick
        // rather than sitting beside it.
        if (text.isNotBlank() && !q.multiSelect) {
            selections = HashMap(selections).apply { remove(q.id) }
        }
    }

    val isReviewing = hasReview && tab >= reviewIndex
    // The last page's action is to send; every earlier one moves forward.
    val isSubmitting = isReviewing || questions.size == 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, bottom = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
    ) {
        // Header.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QuestionAnswer,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = if (questions.size > 1) {
                        "Spettro has ${questions.size} questions"
                    } else {
                        "Spettro has a question"
                    },
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            if (request.context != null) {
                Text(
                    text = request.context!!,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }

        // Tab strip, only when the form has more than one question.
        if (hasReview) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                questions.forEachIndexed { index, question ->
                    QuestionTabChip(
                        title = question.header,
                        isActive = tab == index,
                        isDone = answered(question),
                        onClick = { tab = index },
                    )
                }
                QuestionTabChip(
                    title = "Review",
                    isActive = isReviewing,
                    isDone = false,
                    isReview = true,
                    onClick = { tab = reviewIndex },
                )
            }
        }

        // The current page.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            if (isReviewing) {
                ReviewPage(
                    questions = questions,
                    summaryOf = { q ->
                        val picked = selectedIds(q).toSet()
                        val parts = q.options.filter { it.id in picked }.map { it.label }.toMutableList()
                        custom(q).trim().takeIf { it.isNotEmpty() }?.let { parts.add("“$it”") }
                        parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    },
                    noteOf = { q -> notes[q.id]?.trim().orEmpty() },
                    allAnswered = allAnswered,
                    onJump = { tab = it },
                )
            } else {
                val question = questions[tab.coerceIn(0, questions.size - 1)]
                QuestionPage(
                    question = question,
                    selected = selectedIds(question).toSet(),
                    customText = custom(question),
                    note = notes[question.id].orEmpty(),
                    otherOpen = question.id in otherOpen || custom(question).isNotEmpty(),
                    expandedPreviews = expandedPreviews,
                    onChoose = { choose(question, it) },
                    onCustomChange = { setCustom(question, it) },
                    onNoteChange = { text ->
                        notes = HashMap(notes).apply { put(question.id, text) }
                    },
                    onToggleOther = {
                        otherOpen = HashSet(otherOpen).apply {
                            if (!add(question.id)) remove(question.id)
                        }
                    },
                    onTogglePreview = { optionId ->
                        val key = "${question.id}:$optionId"
                        expandedPreviews =
                            if (key in expandedPreviews) expandedPreviews - key else expandedPreviews + key
                    },
                )
            }
        }

        // Footer: Decline | Back | Next / Review / Submit.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(
                onClick = onDecline,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.diffRemoved),
            ) { Text("Decline") }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            if (hasReview) {
                OutlinedButton(
                    onClick = { tab = (tab - 1).coerceAtLeast(0) },
                    enabled = tab > 0,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = BorderStroke(Dimens.hairlineWidth, MaterialTheme.colorScheme.outline),
                ) { Text("Back") }
            }

            Button(
                onClick = {
                    if (isSubmitting) onSubmit(buildAnswers()) else tab += 1
                },
                // An empty submission is a decline with extra steps.
                enabled = !isSubmitting || hasAnyAnswer,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) {
                Text(
                    when {
                        isSubmitting -> "Submit"
                        tab == questions.size - 1 -> "Review"
                        else -> "Next"
                    },
                )
            }
        }
    }
}

// MARK: - Tab strip

@Composable
private fun QuestionTabChip(
    title: String,
    isActive: Boolean,
    isDone: Boolean,
    onClick: () -> Unit,
    isReview: Boolean = false,
) {
    val colors = LocalSpettroColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isActive) colors.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            Dimens.hairlineWidth,
            if (isActive) colors.accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    isReview -> Icons.AutoMirrored.Filled.Send
                    isDone -> Icons.Filled.CheckCircle
                    else -> Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (isDone) colors.diffAdded else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

// MARK: - One question

@Composable
private fun QuestionPage(
    question: AcpQuestionItem,
    selected: Set<String>,
    customText: String,
    note: String,
    otherOpen: Boolean,
    expandedPreviews: Set<String>,
    onChoose: (AcpQuestionOption) -> Unit,
    onCustomChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onToggleOther: () -> Unit,
    onTogglePreview: (optionId: String) -> Unit,
) {
    val colors = LocalSpettroColors.current
    if (question.question.isNotEmpty()) {
        SelectionContainer {
            Text(
                text = question.question,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
            )
        }
    }
    if (question.multiSelect) {
        Text(
            text = "Pick as many as apply.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        for (option in question.options) {
            OptionCard(
                option = option,
                isSelected = option.id in selected,
                multiSelect = question.multiSelect,
                previewExpanded = "${question.id}:${option.id}" in expandedPreviews,
                onClick = { onChoose(option) },
                onTogglePreview = { onTogglePreview(option.id) },
            )
        }

        if (question.allowCustomInput) {
            if (question.options.isEmpty()) {
                // Free text is the only way to answer — no card to expand.
                OutlinedTextField(
                    value = customText,
                    onValueChange = onCustomChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type your answer") },
                    minLines = 2,
                    maxLines = 4,
                )
            } else {
                OtherCard(
                    text = customText,
                    isOpen = otherOpen,
                    onToggle = onToggleOther,
                    onTextChange = onCustomChange,
                )
            }
        }
    }

    if (question.multiSelect) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes (optional)") },
            placeholder = { Text("Anything the agent should know about this answer") },
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
        )
    }
}

@Composable
private fun OptionCard(
    option: AcpQuestionOption,
    isSelected: Boolean,
    multiSelect: Boolean,
    previewExpanded: Boolean,
    onClick: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    SelectableCard(selected = isSelected, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = when {
                    multiSelect && isSelected -> Icons.Filled.CheckBox
                    multiSelect -> Icons.Outlined.CheckBoxOutlineBlank
                    isSelected -> Icons.Filled.RadioButtonChecked
                    else -> Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (isSelected) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (option.isRecommended) RecommendedTag()
                }
                if (option.description != null) {
                    Text(
                        text = option.description!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    )
                }
                if (option.preview != null && previewExpanded) {
                    MonoBlock(
                        text = option.preview!!,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.spacingSm),
                    )
                }
            }
            if (option.preview != null) {
                Icon(
                    imageVector = if (previewExpanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (previewExpanded) "Hide preview" else "Show preview",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onTogglePreview),
                )
            }
        }
    }
}

/** The "Other" card: expands into the free-text answer field. */
@Composable
private fun OtherCard(
    text: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
) {
    val colors = LocalSpettroColors.current
    SelectableCard(
        selected = text.isNotBlank(),
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = if (text.isNotBlank()) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                Text(
                    text = "Other",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                if (isOpen) {
                    Text(
                        text = "Or answer in your own words",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type your own answer") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                    )
                }
            }
        }
    }
}

// MARK: - Review

@Composable
private fun ReviewPage(
    questions: List<AcpQuestionItem>,
    summaryOf: (AcpQuestionItem) -> String?,
    noteOf: (AcpQuestionItem) -> String,
    allAnswered: Boolean,
    onJump: (index: Int) -> Unit,
) {
    val colors = LocalSpettroColors.current
    val warn = colors.modeColor("yellow")
    Text(
        text = "Review your answers",
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    )
    questions.forEachIndexed { index, question ->
        SelectableCard(
            selected = false,
            onClick = { onJump(index) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = question.header,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    )
                    val summary = summaryOf(question)
                    if (summary != null) {
                        Text(text = summary, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp))
                    } else {
                        Text(
                            text = "Not answered",
                            color = warn,
                            style = TextStyle(fontSize = 13.sp),
                        )
                    }
                    val note = noteOf(question)
                    if (note.isNotEmpty()) {
                        Text(
                            text = "Note: $note",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    if (!allAnswered) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = warn,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Questions you left alone are sent as unanswered — the agent is told " +
                    "nobody answered them, not that you had no preference.",
                color = warn,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// MARK: - Previews

private val previewSingle = AcpQuestionRequest(
    sessionId = "s-1",
    context = "Setting up the persistence layer for the new sync feature.",
    questions = listOf(
        AcpQuestionItem(
            id = "q-0",
            header = "Database",
            question = "Which database should the sync service use?",
            options = listOf(
                AcpQuestionOption(
                    id = "opt-0",
                    label = "SQLite",
                    description = "Embedded, zero configuration, one file on disk.",
                    preview = "dependencies {\n    implementation(\"androidx.sqlite:sqlite:2.4.0\")\n}",
                    isRecommended = true,
                ),
                AcpQuestionOption(
                    id = "opt-1",
                    label = "PostgreSQL",
                    description = "A separate server, but shared between devices.",
                ),
            ),
            allowCustomInput = true,
        ),
    ),
    transport = AcpQuestionRequest.Transport.ExtensionCall(2),
)

private val previewForm = AcpQuestionRequest(
    sessionId = "s-1",
    context = "A few decisions before I refactor the pairing flow.",
    questions = listOf(
        previewSingle.questions[0],
        AcpQuestionItem(
            id = "q-1",
            header = "Cleanups",
            question = "Which cleanups should ride along?",
            options = listOf(
                AcpQuestionOption(id = "opt-0", label = "Delete the legacy QR parser"),
                AcpQuestionOption(id = "opt-1", label = "Inline the single-use helpers"),
                AcpQuestionOption(id = "opt-2", label = "Rename RemotePairing to PairingService"),
            ),
            multiSelect = true,
        ),
        AcpQuestionItem(
            id = "q-2",
            header = "Naming",
            question = "What should the new module be called?",
            allowCustomInput = true,
        ),
    ),
    transport = AcpQuestionRequest.Transport.ExtensionCall(2),
)

@Preview(name = "Single dark", showBackground = true, backgroundColor = 0xFF0E0E0E, heightDp = 640)
@Composable
private fun QuestionSheetPreviewSingle() {
    SpettroTheme(darkTheme = true) {
        QuestionSheetContent(
            request = previewSingle,
            onSubmit = {},
            onDecline = {},
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}

@Preview(name = "Form light", showBackground = true, backgroundColor = 0xFFF9F9F7, heightDp = 720)
@Composable
private fun QuestionSheetPreviewForm() {
    SpettroTheme(darkTheme = false) {
        QuestionSheetContent(
            request = previewForm,
            onSubmit = {},
            onDecline = {},
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}
