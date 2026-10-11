package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import to.eyed.spettro.mobile.model.Mentions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import to.eyed.spettro.mobile.core.acp.AcpCommand
import to.eyed.spettro.mobile.model.CommandCopy
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.ui.components.SpettroMenu
import to.eyed.spettro.mobile.ui.components.SpettroMenuItem
import to.eyed.spettro.mobile.ui.components.SpettroSheet
import to.eyed.spettro.mobile.ui.components.SpettroTextField
import to.eyed.spettro.mobile.ui.components.activationHighlight
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingChip
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingControl
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingFrame
import to.eyed.spettro.mobile.ui.theme.ComposerText
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.Elevation
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoBody
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.floatingShadow


/**
 * The composer, as the desktop draws it: a floating card — raised, a
 * hairline, 20dp corners, the composer shadow — with the text on top and a
 * toolbar inside it: a plus menu (photos, slash commands), the thinking
 * chip, the chat's other settings in one quiet chip, and the send button,
 * an ink circle that becomes an ink Stop square mid-turn, with an ink Guide
 * pill beside it once there is something to steer with. (The transcript's
 * fade into it is drawn by ChatScreen, under the live strip rather than
 * over it.)
 *
 * Stateless: text, attachments, and busy state are owned by the caller; only
 * ephemeral chrome (menus, the command sheet) lives here.
 */
@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<ImageAttachment>,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onAddImages: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isBusy: Boolean,
    enabled: Boolean,
    configSummary: ConfigSummary?,
    onConfigTap: () -> Unit,
    commands: List<AcpCommand>,
    onCommandPick: (AcpCommand) -> Unit,
    modifier: Modifier = Modifier,
    workflowGate: WorkflowGate = WorkflowGate(),
    onSwitchPermission: (() -> Unit)? = null,
    thinking: ThinkingControl? = null,
    thinkingFrame: ThinkingFrame? = null,
    placeholder: String = CHAT_PLACEHOLDER,
    plan: List<AcpPlanEntry> = emptyList(),
    planFolded: Boolean = false,
    canSend: Boolean = true,
    mentionFiles: (suspend () -> List<String>)? = null,
    onMention: (String) -> Unit = {},
    mentionScope: Any? = null,
    /** Bumped to put the caret in the field and bring the keyboard up (Edit & resend). */
    focusRequest: Int = 0,
    /**
     * The tallest the tab over the card (the task list, the @-mention menu)
     * may grow. A phone on its side has little more height than the
     * composer itself, and an open task list must leave the transcript room.
     */
    tabMaxHeight: Dp = TabMaxHeight,
) {
    val colors = LocalSpettroColors.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    // The field's own value, caret and all, so an "@query" can be found under
    // the caret; the caller owns only the text.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    val mention = if (mentionFiles != null && field.selection.collapsed) {
        Mentions.at(field.text, field.selection.end)
    } else {
        null
    }
    // The folder's files, fetched the first time an "@" asks for them.
    // ([mentionScope] names the folder: another folder, another list.)
    var files by remember(mentionScope) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(mention != null, mentionScope) {
        if (mention != null && files == null && mentionFiles != null) {
            files = runCatching { mentionFiles() }.getOrDefault(emptyList())
        }
    }
    val pick: (String) -> Unit = { path ->
        val query = Mentions.at(field.text, field.selection.end)
        if (query != null) {
            val inserted = Mentions.insert(field.text, query.start, field.selection.end, path)
            field = TextFieldValue(inserted.text, TextRange(inserted.caret))
            onMention(path)
            onTextChange(inserted.text)
        }
    }
    val workflow = remember(text, workflowGate) { workflowDraft(text, workflowGate) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    var commandsOpen by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Dimens.radiusLg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .padding(start = Dimens.spacingMd, end = Dimens.spacingMd, top = 2.dp, bottom = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TuckedUnder(
            tuck = TodoTuck,
            tab = {
                if (mention != null) {
                    MentionMenu(
                        files = files?.let { Mentions.rank(it, mention.query) },
                        query = mention.query,
                        onPick = pick,
                        maxHeight = tabMaxHeight,
                        modifier = Modifier.padding(horizontal = Dimens.spacingSm),
                    )
                } else {
                    TodoList(
                        plan = plan,
                        isBusy = isBusy,
                        folded = planFolded,
                        maxHeight = tabMaxHeight,
                        modifier = Modifier.padding(horizontal = Dimens.spacingMd),
                    )
                }
            },
            card = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focused) {
                        // The desktop's focus ring: 3dp of accent at 10% outside the card.
                        Modifier.drawBehind {
                            val ring = 3.dp.toPx()
                            drawRoundRect(
                                color = colors.accent.copy(alpha = 0.1f),
                                topLeft = Offset(-ring / 2f, -ring / 2f),
                                size = Size(size.width + ring, size.height + ring),
                                cornerRadius = CornerRadius((Dimens.radiusLg + 1.5.dp).toPx()),
                                style = Stroke(width = ring),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .floatingShadow(Elevation.Composer, shape)
                .clip(shape)
                .background(colors.surfaceRaised)
                .border(
                    Dimens.hairlineWidth,
                    if (focused) colors.accent.copy(alpha = 0.4f).compositeOver(colors.surfaceRaised) else colors.hairline,
                    shape,
                ),
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
            }
            ComposerTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != text) onTextChange(it.text)
                },
                enabled = enabled,
                budgets = workflow.budgets,
                muted = workflow.muted,
                interactionSource = interactionSource,
                placeholder = placeholder,
                focusRequester = focusRequester,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(
                        onClick = { plusMenuOpen = true },
                        enabled = enabled,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Attach",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    SpettroMenu(expanded = plusMenuOpen, onDismissRequest = { plusMenuOpen = false }) {
                        SpettroMenuItem(
                            text = "Photos",
                            icon = Icons.Outlined.Photo,
                            onClick = {
                                plusMenuOpen = false
                                onAddImages()
                            },
                        )
                        if (commands.isNotEmpty()) {
                            SpettroMenuItem(
                                text = "Commands",
                                icon = Icons.Outlined.Tag,
                                onClick = {
                                    plusMenuOpen = false
                                    commandsOpen = true
                                },
                            )
                        }
                    }
                }
                // The desktop's ConfigBar order: how hard the model thinks
                // (its own chip, opening the slider), then the rest of the
                // chat's settings in one chip. The settings chip keeps its
                // own width and only shortens when the send buttons need the
                // room.
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (thinking != null) {
                        ThinkingChip(
                            control = thinking,
                            enabled = enabled,
                            startOpen = thinkingFrame != null,
                            frame = thinkingFrame,
                        )
                    }
                    if (configSummary != null) {
                        ConfigChip(
                            summary = configSummary,
                            enabled = enabled,
                            onTap = onConfigTap,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                val hasContent = text.isNotBlank() || attachments.isNotEmpty()
                SendButton(
                    isBusy = isBusy,
                    hasContent = hasContent,
                    canSend = enabled && canSend && hasContent,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
            },
        )
        WorkflowHint(
            draft = workflow,
            onSwitchPermission = onSwitchPermission?.takeIf { workflowGate.canRestrict },
        )
    }

    if (commandsOpen) {
        CommandPalette(
            commands = commands,
            onPick = { command ->
                commandsOpen = false
                onCommandPick(command)
            },
            onDismiss = { commandsOpen = false },
        )
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<ImageAttachment>,
    onRemove: (ImageAttachment) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.spacingMd,
            end = Dimens.spacingMd,
            top = Dimens.spacingMd,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box {
                AttachmentThumbnail(attachment = attachment, size = 64.dp)
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "Remove attachment",
                    // White on a dark disc: it sits on a photo, not on the theme.
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clip(CircleShape)
                        .clickable { onRemove(attachment) },
                )
            }
        }
    }
}

/**
 * "Coding · GPT-5 · Don’t ask" in one toolbar chip (the desktop's
 * `config-chip`): 32dp, 8dp corners, text-secondary, opening the settings
 * sheet. Don’t ask is drawn in the warning ink, as the desktop's settings
 * chip is (`settings-chip--warn`).
 */
@Composable
private fun ConfigChip(
    summary: ConfigSummary,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "Chat settings",
            tint = colors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        val label = remember(summary, colors) {
            buildAnnotatedString {
                append(summary.text)
                summary.warningRange?.let { range ->
                    addStyle(SpanStyle(color = colors.warning), range.first, range.last + 1)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ComposerTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    budgets: Boolean,
    muted: Boolean,
    interactionSource: MutableInteractionSource,
    placeholder: String,
    focusRequester: FocusRequester,
) {
    val colors = LocalSpettroColors.current
    val text = value.text
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        maxLines = 8,
        textStyle = ComposerText.copy(color = colors.textPrimary),
        // "ultracode", or "use a workflow to …", silently arms multi-agent
        // orchestration for the next turn. Lighting the phrase as it is typed
        // is the only sign the user gets that the turn changed shape, and it is
        // driven by the same matcher the CLI uses to decide. A "+500k" budget
        // lights beside it when it would be honoured; under Ask first both are
        // muted, since no workflow will run.
        visualTransformation = activationHighlight(budgets = budgets, muted = muted),
        cursorBrush = SolidColor(colors.accent),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = if (enabled) placeholder else "Waiting for your computer…",
                        style = ComposerText,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** The composer's hint in an open chat (the desktop's, word for word). */
const val CHAT_PLACEHOLDER = "Ask Spettro to build, fix, or explain…"

/** The hint while the link to the computer is coming back: the field stays usable, Send waits. */
const val RECONNECTING_PLACEHOLDER = "Spettro is reconnecting — you can keep typing…"

/** The hint while Spettro starts on the computer. */
const val STARTING_PLACEHOLDER = "Starting Spettro…"

/**
 * The hint on the new-session view, which names the folder right under the
 * field — so the hint says what to type rather than where.
 */
const val NEW_SESSION_PLACEHOLDER = "Describe a task, or ask about your code…"

/** The size of the composer's round buttons: the desktop's 32px, grown for a thumb. */
private val SendSize = 36.dp

@Composable
private fun SendButton(
    isBusy: Boolean,
    hasContent: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    if (isBusy) {
        // Mid-turn there are two things to do: stop the turn, or guide it —
        // a message sent now steers the running agent at its next step. The
        // Guide pill only shows once there is something to send, and then
        // Stop steps back to an outline so the two don't compete. While the
        // link comes back Guide stays, dimmed: the steer is waiting to go.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(SendSize)
                    .clip(CircleShape)
                    .then(
                        if (hasContent) {
                            Modifier.border(Dimens.hairlineWidth, colors.hairline, CircleShape)
                        } else {
                            Modifier.background(colors.ink)
                        },
                    )
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(11.dp)
                        .background(if (hasContent) colors.ink else colors.onInk, RoundedCornerShape(2.dp))
                        .semantics { contentDescription = "Stop" },
                )
            }
            if (hasContent) {
                Row(
                    modifier = Modifier
                        .height(SendSize)
                        .alpha(if (canSend) 1f else 0.25f)
                        .clip(CircleShape)
                        .background(colors.ink)
                        .clickable(enabled = canSend, onClick = onSend)
                        .padding(start = 10.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = null,
                        tint = colors.onInk,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "Guide",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onInk,
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(SendSize)
                .alpha(if (canSend) 1f else 0.25f)
                .clip(CircleShape)
                .background(colors.ink)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "Send",
                tint = colors.onInk,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * The @-mention menu (the desktop's MentionMenu): the project's files that
 * match what follows the "@", floating above the composer card. A row is the
 * file's name first and its folder after, muted — people remember files by
 * name. [files] is null while the folder is still being listed.
 */
@Composable
private fun MentionMenu(
    files: List<String>?,
    query: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = TabMaxHeight,
) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(topStart = Dimens.radiusMd, topEnd = Dimens.radiusMd)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dimens.hairlineWidth, colors.hairline, shape)
            .padding(bottom = TodoTuck)
            .heightIn(max = maxHeight.coerceAtMost(220.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        if (files.isNullOrEmpty()) {
            Text(
                text = when {
                    files == null -> "Looking through the project…"
                    query.isEmpty() -> "No files in this folder"
                    else -> "No file matches “$query”"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
            )
        } else {
            files.forEach { path ->
                val slash = path.lastIndexOf('/')
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(path) }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = Dimens.spacingMd),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    Text(
                        path.substring(slash + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    if (slash > 0) {
                        Text(
                            path.substring(0, slash),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// MARK: Command palette

/**
 * The slash-command picker: a searchable bottom sheet listing every command
 * the agent advertises, in plain words, the developer ones last
 * ([CommandCopy]). The search matches a name's start or the description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    commands: List<AcpCommand>,
    onPick: (AcpCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    SpettroSheet(onDismissRequest = onDismiss) {
        CommandPaletteContent(commands = commands, onPick = onPick)
    }
}

@Composable
internal fun CommandPaletteContent(
    commands: List<AcpCommand>,
    onPick: (AcpCommand) -> Unit,
) {
    val colors = LocalSpettroColors.current
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(commands, search) { CommandCopy.matching(commands, search) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SpettroTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Find a command",
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spacingLg),
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimens.spacingLg,
                vertical = Dimens.spacingSm,
            ),
        ) {
            items(filtered, key = { it.name }) { command ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .clickable { onPick(command) }
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "/${command.name}",
                        style = MonoBody.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                    )
                    val description = CommandCopy.description(command)
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

// MARK: Previews

@Preview(name = "Composer", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun ChatComposerPreview() {
    SpettroTheme(darkTheme = true) {
        var text by remember { mutableStateOf("") }
        ChatComposer(
            text = text,
            onTextChange = { text = it },
            attachments = emptyList(),
            onRemoveAttachment = {},
            onAddImages = {},
            onSend = { text = "" },
            onStop = {},
            isBusy = false,
            enabled = true,
            configSummary = ChatPreviewData.CONFIG_SUMMARY,
            onConfigTap = {},
            commands = ChatPreviewData.commands,
            onCommandPick = {},
            thinking = ThinkingControl(
                options = ChatPreviewData.configOptions,
                reasons = null,
                onCommit = { CompletableDeferred(true) },
                onSwitchToRestricted = {},
                onTurnOffUltra = {},
            ),
        )
    }
}

@Preview(name = "Busy — stop", showBackground = true, backgroundColor = 0xFFFAFAFA, widthDp = 380)
@Composable
private fun ChatComposerPreviewBusy() {
    SpettroTheme(darkTheme = false) {
        ChatComposer(
            text = "",
            onTextChange = {},
            attachments = emptyList(),
            onRemoveAttachment = {},
            onAddImages = {},
            onSend = {},
            onStop = {},
            isBusy = true,
            enabled = true,
            configSummary = ChatPreviewData.CONFIG_SUMMARY,
            onConfigTap = {},
            commands = emptyList(),
            onCommandPick = {},
        )
    }
}

@Preview(name = "Disconnected", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 380)
@Composable
private fun ChatComposerPreviewDisabled() {
    SpettroTheme(darkTheme = true) {
        ChatComposer(
            text = "",
            onTextChange = {},
            attachments = emptyList(),
            onRemoveAttachment = {},
            onAddImages = {},
            onSend = {},
            onStop = {},
            isBusy = false,
            enabled = false,
            configSummary = null,
            onConfigTap = {},
            commands = emptyList(),
            onCommandPick = {},
        )
    }
}

@Preview(name = "Command palette", showBackground = true, backgroundColor = 0xFF2C2C2E, widthDp = 380)
@Composable
private fun CommandPalettePreview() {
    SpettroTheme(darkTheme = true) {
        CommandPaletteContent(commands = ChatPreviewData.commands, onPick = {})
    }
}
