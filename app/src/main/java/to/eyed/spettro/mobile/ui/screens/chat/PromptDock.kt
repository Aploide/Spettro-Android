package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpQuestionAnswer
import to.eyed.spettro.mobile.core.acp.AcpQuestionRequest
import to.eyed.spettro.mobile.model.Prompts
import to.eyed.spettro.mobile.model.ToolCallItem
import to.eyed.spettro.mobile.model.ToolPresentation
import to.eyed.spettro.mobile.ui.components.BarIconButton
import to.eyed.spettro.mobile.ui.components.ButtonSize
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.screens.sheets.MonoBlock
import to.eyed.spettro.mobile.ui.screens.sheets.PROMPT_ARM_DELAY_MS
import to.eyed.spettro.mobile.ui.screens.sheets.PromptIconTile
import to.eyed.spettro.mobile.ui.screens.sheets.PromptQueuePill
import to.eyed.spettro.mobile.ui.screens.sheets.QuestionSheetContent
import to.eyed.spettro.mobile.ui.screens.sheets.RecommendedTag
import to.eyed.spettro.mobile.ui.screens.sheets.SHELL_LANGS
import to.eyed.spettro.mobile.ui.screens.sheets.consumeTaps
import to.eyed.spettro.mobile.ui.screens.sheets.permissionIcon
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.Elevation
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.floatingShadow

/** A prompt of the open chat's, as its dock shows it. */
sealed class DockPrompt {
    abstract val id: String

    /** An approval; [form] when the permission transport really carries a question. */
    data class Permission(
        override val id: String,
        val request: AcpPermissionRequest,
        val form: AcpQuestionRequest? = null,
    ) : DockPrompt()

    data class Question(override val id: String, val request: AcpQuestionRequest) : DockPrompt()
}

/** What the user did with a docked prompt. */
sealed class DockReply {
    abstract val promptId: String

    /** An approval's option; null only when it offered no way to say no and was denied. */
    data class Option(override val promptId: String, val optionId: String?) : DockReply()

    /** A question's answers; null declines it. */
    data class Answers(override val promptId: String, val answers: List<AcpQuestionAnswer>?) : DockReply()
}

/** The desktop's prompt entrance: rise from 6dp and fade, 200ms. */
private val PromptEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

/** Diff lines an approval card shows before "Show full diff": a glance, not a review. */
private const val PREVIEW_DIFF_LINES = 30

/**
 * What sits on top of the composer while the chat waits on the user — the
 * desktop's PromptDock: the oldest approval, else the oldest question, one
 * at a time with "1 of 3" when more wait, inline rather than a sheet over
 * the chat. Nothing here is dismissed by a swipe: it stays until it is
 * answered, here or anywhere else.
 *
 * After a Deny (not a compaction choice) it offers a field for what Spettro
 * should do instead, sent as the next message — or as guidance the running
 * turn reads at its next step. The offer lapses once the user says anything
 * else, or something new asks.
 *
 * @param maxHeight the card's cap, so a long diff scrolls inside it rather
 *   than pushing the composer off a phone with its keyboard up.
 * @param lastUserMessageId the chat's newest user message, to tell when the
 *   deny offer has been answered some other way.
 */
@Composable
fun PromptDock(
    prompts: List<DockPrompt>,
    onReply: (DockReply) -> Unit,
    /** Sends "what to do instead"; false when it couldn't go (reconnecting), and the text stays. */
    onDeniedFeedback: (String) -> Boolean,
    maxHeight: Dp,
    lastUserMessageId: String?,
    modifier: Modifier = Modifier,
) {
    // The prompt the user denied, and the newest message when they did.
    var denied by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var deniedPrompt by remember { mutableStateOf<String?>(null) }
    val head = prompts.firstOrNull()
    LaunchedEffect(lastUserMessageId) {
        if (denied != null && denied?.second != lastUserMessageId) denied = null
    }
    LaunchedEffect(head?.id) {
        if (head != null && head.id != deniedPrompt) denied = null
    }

    AnimatedContent(
        targetState = head?.id ?: denied?.let { "denied" },
        transitionSpec = {
            (fadeIn(tween(200, easing = PromptEasing)) + slideInVertically(tween(200, easing = PromptEasing)) { it / 12 })
                .togetherWith(fadeOut(tween(120)))
        },
        modifier = modifier.fillMaxWidth(),
        label = "promptDock",
    ) { target ->
        val prompt = prompts.firstOrNull { it.id == target }
        when {
            prompt != null -> key(prompt.id) {
                PromptCardFrame(maxHeight = maxHeight) {
                    when (prompt) {
                        is DockPrompt.Permission -> if (prompt.form != null) {
                            QuestionCard(
                                request = prompt.form,
                                queue = queueLabel(prompts.size),
                                onAnswer = { onReply(DockReply.Answers(prompt.id, it)) },
                            )
                        } else {
                            PermissionCard(
                                request = prompt.request,
                                queue = queueLabel(prompts.size),
                                onChoose = { optionId, isDeny ->
                                    onReply(DockReply.Option(prompt.id, optionId))
                                    if (isDeny && !prompt.request.isCompact) {
                                        deniedPrompt = prompt.id
                                        denied = prompt.request.title to lastUserMessageId
                                    }
                                },
                            )
                        }
                        is DockPrompt.Question -> QuestionCard(
                            request = prompt.request,
                            queue = queueLabel(prompts.size),
                            onAnswer = { onReply(DockReply.Answers(prompt.id, it)) },
                        )
                    }
                }
            }
            target == "denied" && denied != null -> PromptCardFrame(maxHeight = maxHeight) {
                DeniedFeedback(
                    title = denied?.first.orEmpty(),
                    onSend = {
                        if (onDeniedFeedback(it)) denied = null
                    },
                    onDismiss = { denied = null },
                )
            }
            else -> Box(Modifier)
        }
    }
}

private fun queueLabel(total: Int): String? = if (total > 1) "1 of $total" else null

/**
 * The prompt card (`.prompt-card`): the composer's raised surface and
 * hairline, 16dp corners, one quiet shadow, capped at [maxHeight].
 */
@Composable
private fun PromptCardFrame(maxHeight: Dp, content: @Composable () -> Unit) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd, end = Dimens.spacingMd, top = 2.dp, bottom = 6.dp)
            .floatingShadow(Elevation.Medium, shape)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .heightIn(max = maxHeight),
    ) {
        content()
    }
}

/**
 * An approval, inline (the desktop's PermissionCard): what will happen in a
 * sentence, the command or the change itself, and the same buttons in the
 * same order every time — Allow once, Always allow, Deny. Inert for its
 * first 600ms, and answered once however fast it is tapped.
 *
 * [onChoose] gets the option and whether it was the denial.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermissionCard(
    request: AcpPermissionRequest,
    queue: String?,
    onChoose: (optionId: String?, isDeny: Boolean) -> Unit,
) {
    val colors = LocalSpettroColors.current
    var armed by remember { mutableStateOf(false) }
    var answered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROMPT_ARM_DELAY_MS)
        armed = true
    }
    val options = Prompts.orderedOptions(request)
    val body = Prompts.permissionBody(request)
    val headline = Prompts.permissionHeadline(request)
    val diffPaths = request.diffs.map { it.path }.toSet()
    val locations = request.locations.filter { it.path !in diffPaths }
    val projectPath = LocalProjectPath.current

    Column(
        modifier = Modifier.padding(start = Dimens.spacingMd, end = Dimens.spacingMd, top = Dimens.spacingMd, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PromptIconTile(icon = permissionIcon(request), info = request.isCompact)
            Text(
                text = headline,
                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (queue != null) PromptQueuePill(queue)
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            if (request.isCompact) {
                Text(Prompts.compactDetail(request), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                if (Prompts.showsTitle(request)) {
                    Text(request.title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                for (block in body) {
                    when (block) {
                        is Prompts.BodyBlock.Code -> MonoBlock(
                            text = if (block.lang in SHELL_LANGS) "$ ${block.text}" else block.text,
                            fontSize = 12.5.sp,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is Prompts.BodyBlock.Note -> Text(
                            block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                request.diffs.forEach { diff ->
                    DiffView(
                        diff = ToolCallItem.ToolDiff(diff.path, diff.oldText, diff.newText),
                        maxLines = PREVIEW_DIFF_LINES,
                        moreLabel = "Show full diff",
                    )
                }
                locations.forEach { location ->
                    Text(
                        text = ToolPresentation.shortPath(location.path, projectPath) + (location.line?.let { ":$it" } ?: ""),
                        style = to.eyed.spettro.mobile.ui.theme.MonoSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (armed) 1f else 0.6f)
                .then(if (armed) Modifier else Modifier.consumeTaps()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        ) {
            options.forEachIndexed { index, choice ->
                SpettroButton(
                    text = choice.label,
                    onClick = {
                        if (!armed || answered) return@SpettroButton
                        answered = true
                        onChoose(choice.option.optionId, choice.role == Prompts.OptionRole.REJECT)
                    },
                    style = if (index == 0 && choice.role != Prompts.OptionRole.REJECT) ButtonStyle.Ink else ButtonStyle.Neutral,
                    size = ButtonSize.Regular,
                    shape = RoundedCornerShape(Dimens.radiusSm),
                )
            }
            // Nothing to say no with: the explicit way out dismisses, which
            // the CLI takes as a no. Never a swipe.
            if (options.none { it.role == Prompts.OptionRole.REJECT }) {
                SpettroButton(
                    text = "Deny",
                    onClick = {
                        if (!armed || answered) return@SpettroButton
                        answered = true
                        onChoose(null, true)
                    },
                    size = ButtonSize.Regular,
                    shape = RoundedCornerShape(Dimens.radiusSm),
                )
            }
        }
        // What "Always allow" covers, and the agent's pick, under the row
        // rather than under one button, so the buttons keep one line.
        val notes = options.filter { it.option.isRecommended || it.caption != null }
        notes.forEach { choice ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp),
            ) {
                if (choice.option.isRecommended) RecommendedTag()
                Text(
                    text = choice.caption ?: choice.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A question, inline: the question form in the card, with Skip as Decline. */
@Composable
private fun QuestionCard(
    request: AcpQuestionRequest,
    queue: String?,
    onAnswer: (List<AcpQuestionAnswer>?) -> Unit,
) {
    var answered by remember { mutableStateOf(false) }
    QuestionSheetContent(
        request = request,
        onSubmit = {
            if (!answered) {
                answered = true
                onAnswer(it)
            }
        },
        onDecline = {
            if (!answered) {
                answered = true
                onAnswer(null)
            }
        },
        contentPadding = PaddingValues(start = Dimens.spacingMd, end = Dimens.spacingMd, top = Dimens.spacingMd, bottom = 10.dp),
        queue = queue,
    )
}

/**
 * "Denied — tell Spettro what to do instead." Optional: closing it leaves
 * the agent to carry on from the denial alone.
 */
@Composable
private fun DeniedFeedback(title: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpettroColors.current
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val send = {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) onSend(trimmed)
    }
    Column(
        modifier = Modifier.padding(start = Dimens.spacingMd, end = 6.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp).background(colors.hoverFill, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PanTool, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
            }
            Text(
                text = buildAnnotatedString {
                    append("Denied")
                    if (title.isNotEmpty()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = colors.textSecondary)) { append(" — $title") }
                    }
                },
                style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BarIconButton(Icons.Outlined.Close, contentDescription = "Dismiss", onClick = onDismiss)
        }
        Row(
            modifier = Modifier.padding(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(Dimens.radiusSm)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .clip(shape)
                    .background(colors.canvas)
                    .border(Dimens.hairlineWidth, colors.hairline, shape)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "Tell Spettro what to do instead (optional)",
                                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            SpettroButton(
                text = "Send",
                onClick = send,
                enabled = text.isNotBlank(),
                style = ButtonStyle.Ink,
                shape = RoundedCornerShape(Dimens.radiusSm),
            )
        }
    }
}
