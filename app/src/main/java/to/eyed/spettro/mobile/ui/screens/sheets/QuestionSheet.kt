package to.eyed.spettro.mobile.ui.screens.sheets

import to.eyed.spettro.mobile.ui.components.SpettroSheet
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.SectionHeader
import to.eyed.spettro.mobile.ui.components.SpettroTextField
import to.eyed.spettro.mobile.ui.components.fadeWhenMoreBelow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionItem
import to.eyed.spettro.mobile.core.acp.AcpQuestionOption
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.model.QuestionForm
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
 * One question at a time ("Question 2 of 3"), as the desktop's
 * QuestionCard draws it, in the prompt dock and in this sheet alike.
 *
 * Swiping the sheet away calls [onDismiss] only; the form stays pending —
 * the caller sets it aside, and never declines it for the user. [onDecline]
 * is Skip all, which refuses the whole form; with anything picked or typed
 * it asks "Discard your answers?" first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionSheet(
    request: AcpQuestionRequest,
    onSubmit: (answers: List<AcpQuestionAnswer>) -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    SpettroSheet(onDismissRequest = onDismiss) {
        QuestionSheetContent(
            request = request,
            onSubmit = onSubmit,
            onDecline = onDecline,
            modifier = Modifier.fillMaxHeight(0.94f),
        )
    }
}

/**
 * A window only a few rows tall — a phone on its side. The question card
 * then gives its options every spare dp: no icon tile, tighter gaps and
 * cards, and on a wide window the options two by two.
 */
@Composable
internal fun isCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP

/** Below this many dp of window height the question card goes compact; a tablet on its side stays above it. */
private const val COMPACT_HEIGHT_DP = 480

/** From this many dp of window width a compact card shows its options in two columns. */
private const val TWO_COLUMN_WIDTH_DP = 600

@Composable
internal fun QuestionSheetContent(
    request: AcpQuestionRequest,
    onSubmit: (answers: List<AcpQuestionAnswer>) -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = Dimens.spacingLg,
        end = Dimens.spacingLg,
        bottom = Dimens.spacingLg,
    ),
    queue: String? = null,
) {
    val colors = LocalSpettroColors.current
    val questions = request.questions
    val count = questions.size
    val compact = isCompactHeight()
    val twoColumns = compact && LocalConfiguration.current.screenWidthDp >= TWO_COLUMN_WIDTH_DP
    val gap = if (compact) Dimens.spacingSm else Dimens.spacingLg

    // The user's working answers, keyed by question id. Plain Serializable
    // maps in saveable state, copied on write: moving between tabs changes an
    // index and nothing else, so a revisited question comes back exactly as
    // it was left — including across process death.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selections by rememberSaveable { mutableStateOf(HashMap<String, ArrayList<String>>()) }
    var customText by rememberSaveable { mutableStateOf(HashMap<String, String>()) }
    var notes by rememberSaveable { mutableStateOf(HashMap<String, String>()) }
    var otherOpen by rememberSaveable { mutableStateOf(HashSet<String>()) }
    // Skip all with answers in it swaps the footer for "Discard your answers?".
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Which option previews are expanded ("questionId:optionId") — pure
    // presentation, not worth surviving process death.
    var expandedPreviews by remember { mutableStateOf(setOf<String>()) }
    // The desktop's arm delay, as on the approval sheet: for the first
    // 600ms the options and buttons are dimmed and inert, so a tap meant for
    // whatever was on screen a moment ago can't answer the agent — a lone
    // single-select question answers itself on the first pick.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROMPT_ARM_DELAY_MS)
        armed = true
    }
    val armedModifier = if (armed) Modifier else Modifier.alpha(0.6f).consumeTaps()

    fun selectedIds(q: AcpQuestionItem): List<String> = selections[q.id].orEmpty()
    fun custom(q: AcpQuestionItem): String = customText[q.id].orEmpty()
    fun answered(q: AcpQuestionItem): Boolean =
        selectedIds(q).isNotEmpty() || custom(q).isNotBlank()

    val hasAnyAnswer = questions.any { answered(it) }
    val drafts = questions.map { q ->
        QuestionForm.Draft(selectedIds(q), custom(q), notes[q.id].orEmpty())
    }

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
        if (!armed) return
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

    val step = tab.coerceIn(0, (count - 1).coerceAtLeast(0))
    // The last question's action is to send; every earlier one moves forward.
    val isLast = step >= count - 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        // The header and the page scroll together above the pinned footer:
        // on a phone on its side the card is little taller than the footer,
        // and a fixed header left the options no room at all.
        val pageScroll = rememberScrollState()
        // Each question opens at its top. Scrolled down to reach one
        // question's options, the next would otherwise open there too, its
        // step and its question above the viewport with nothing to say so.
        LaunchedEffect(step) { pageScroll.scrollTo(0) }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fadeWhenMoreBelow(pageScroll)
                .verticalScroll(pageScroll),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // Header.
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!compact) PromptIconTile(icon = Icons.Outlined.QuestionAnswer, info = true)
                    Text(
                        text = if (questions.size > 1) {
                            "Spettro has ${questions.size} questions"
                        } else {
                            "Spettro has a question"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 1) {
                        Text(
                            text = "Question ${step + 1} of $count",
                            color = colors.textTertiary,
                            style = TextStyle(fontSize = 12.sp),
                        )
                    }
                    if (queue != null) PromptQueuePill(queue)
                }
                if (request.context != null && step == 0) {
                    Text(
                        text = request.context!!,
                        color = colors.textSecondary,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    )
                }
            }

            // The current page.
            Column(
                modifier = Modifier.then(armedModifier),
                verticalArrangement = Arrangement.spacedBy(if (compact) Dimens.spacingSm else Dimens.spacingMd),
            ) {
                run {
                    val question = questions[step]
                    if (question.header.isNotBlank()) {
                        Text(
                            text = question.header,
                            color = colors.textTertiary,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    QuestionPage(
                        question = question,
                        selected = selectedIds(question).toSet(),
                        customText = custom(question),
                        note = notes[question.id].orEmpty(),
                        otherOpen = question.id in otherOpen || custom(question).isNotEmpty(),
                        expandedPreviews = expandedPreviews,
                        compact = compact,
                        twoColumns = twoColumns,
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
        }

        // Footer: Skip all | Back | Next / Send answer(s) — or, after Skip
        // all with answers in it, the desktop's inline confirmation.
        if (confirmDiscard) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().then(armedModifier),
            ) {
                Text(
                    text = "Discard your answers?",
                    color = colors.textPrimary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                SpettroButton("Keep editing", onClick = { confirmDiscard = false }, shape = RoundedCornerShape(Dimens.radiusSm))
                SpettroButton(
                    "Discard",
                    onClick = onDecline,
                    style = ButtonStyle.Destructive,
                    shape = RoundedCornerShape(Dimens.radiusSm),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.fillMaxWidth().then(armedModifier),
            ) {
                QuestionForm.skippedHint(isLast, drafts)?.let { hint ->
                    Text(
                        text = hint,
                        color = colors.textTertiary,
                        style = TextStyle(fontSize = 12.sp),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SpettroButton(
                        "Skip all",
                        onClick = { if (QuestionForm.dirty(drafts)) confirmDiscard = true else onDecline() },
                        style = ButtonStyle.Plain,
                        shape = RoundedCornerShape(Dimens.radiusSm),
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

                    if (step > 0) {
                        SpettroButton(
                            "Back",
                            onClick = { tab = step - 1 },
                            shape = RoundedCornerShape(Dimens.radiusSm),
                        )
                    }

                    SpettroButton(
                        text = when {
                            !isLast -> "Next"
                            count > 1 -> "Send answers"
                            else -> "Send answer"
                        },
                        onClick = { if (isLast) onSubmit(buildAnswers()) else tab = step + 1 },
                        // An empty submission is a skip with extra steps.
                        enabled = !isLast || hasAnyAnswer,
                        style = ButtonStyle.Ink,
                        shape = RoundedCornerShape(Dimens.radiusSm),
                    )
                }
            }
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
    compact: Boolean,
    twoColumns: Boolean,
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
            text = "Choose all that apply.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        @Composable
        fun card(option: AcpQuestionOption, modifier: Modifier) = OptionCard(
            option = option,
            isSelected = option.id in selected,
            multiSelect = question.multiSelect,
            previewExpanded = "${question.id}:${option.id}" in expandedPreviews,
            compact = compact,
            onClick = { onChoose(option) },
            onTogglePreview = { onTogglePreview(option.id) },
            modifier = modifier,
        )
        if (twoColumns) {
            // Side by side, each pair as tall as the taller of the two.
            for (pair in question.options.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                ) {
                    for (option in pair) card(option, Modifier.weight(1f).fillMaxHeight())
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            for (option in question.options) card(option, Modifier.fillMaxWidth())
        }

        if (question.allowCustomInput) {
            if (question.options.isEmpty()) {
                // Free text is the only way to answer — no card to expand.
                SpettroTextField(
                    value = customText,
                    onValueChange = onCustomChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Your answer…",
                    singleLine = false,
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Notes (optional)")
            SpettroTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Anything the agent should know about this answer",
                singleLine = false,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun OptionCard(
    option: AcpQuestionOption,
    isSelected: Boolean,
    multiSelect: Boolean,
    previewExpanded: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    SelectableCard(selected = isSelected, onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimens.spacingMd,
                vertical = if (compact) Dimens.spacingSm else Dimens.spacingMd,
            ),
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
                tint = if (isSelected) colors.accent else colors.textTertiary,
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
                        color = colors.textSecondary,
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
                    tint = colors.textSecondary,
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
                tint = if (text.isNotBlank()) colors.accent else colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                Text(
                    text = "Other…",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                if (isOpen) {
                    Text(
                        text = "Or answer in your own words",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SpettroTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Your answer…",
                        singleLine = false,
                        maxLines = 4,
                    )
                }
            }
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

@Preview(name = "Single dark", showBackground = true, backgroundColor = 0xFF2C2C2E, heightDp = 640)
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

@Preview(name = "Form light", showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 720)
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
