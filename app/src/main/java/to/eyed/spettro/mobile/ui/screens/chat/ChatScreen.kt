package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.ui.components.thinking.ThinkingControl
import to.eyed.spettro.mobile.ui.components.thinking.ThinkingFrame
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.border
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.components.SpettroTopBar
import to.eyed.spettro.mobile.ui.components.SpettroMenuSeparator
import to.eyed.spettro.mobile.ui.components.SpettroMenuItem
import to.eyed.spettro.mobile.ui.components.SpettroMenu
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.screens.home.DeleteChatDialog
import to.eyed.spettro.mobile.ui.components.ButtonSize
import to.eyed.spettro.mobile.ui.components.BarIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpCommand
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.core.acp.AcpUsage
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.RunPhase
import to.eyed.spettro.mobile.model.DisplayRow
import to.eyed.spettro.mobile.model.ToolGroups
import androidx.compose.runtime.CompositionLocalProvider
import to.eyed.spettro.mobile.model.TranscriptItem
import to.eyed.spettro.mobile.ui.components.RunTicker
import to.eyed.spettro.mobile.model.TranscriptRow
import to.eyed.spettro.mobile.model.groupTranscript
import to.eyed.spettro.mobile.ui.components.AppIconImage
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * One conversation: top bar, status strip, transcript, composer — the
 * Android counterpart of MobileChatView.swift. Fully stateless: every piece
 * of state arrives as a parameter and every interaction leaves through a
 * callback, so the integrator wires it to the session and the transport.
 *
 * @param waitingFor what a prompt the user swiped away is waiting for
 *   ("Spettro is waiting for your approval"), shown above the composer with
 *   a way back to it; null when nothing is set aside.
 * @param awaitingApproval a permission of this chat's is waiting, and
 *   [awaitingAnswer] a question: the run ticker at the transcript's tail
 *   says the turn is waiting on the user.
 * @param workflowGate what the chat's options say about workflows, so the
 *   composer can light a "+500k" budget and say when Ask first holds a
 *   workflow back; [onSwitchPermission] is that hint's way out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    items: List<TranscriptItem>,
    isBusy: Boolean,
    runStartedAt: Long?,
    liveTokens: Long?,
    plan: List<AcpPlanEntry>,
    usage: AcpUsage?,
    modeName: String?,
    modeColorName: String?,
    isPinned: Boolean,
    isArchived: Boolean,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    attachments: List<ImageAttachment>,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onAddImages: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    configSummary: ConfigSummary?,
    onConfigTap: () -> Unit,
    commands: List<AcpCommand>,
    onCommandPick: (AcpCommand) -> Unit,
    onBack: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    waitingFor: String? = null,
    onReviewPrompt: () -> Unit = {},
    workflowGate: WorkflowGate = WorkflowGate(),
    onSwitchPermission: (() -> Unit)? = null,
    thinking: ThinkingControl? = null,
    thinkingFrame: ThinkingFrame? = null,
    awaitingApproval: Boolean = false,
    awaitingAnswer: Boolean = false,
    projectPath: String? = null,
    awaitingToolIds: Set<String> = emptySet(),
) {
    val colors = LocalSpettroColors.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // The wire delivers a workflow as a flat run of tool calls — a lifecycle
    // card, one call per sub-agent, and every tool each of them runs,
    // interleaved. Rendered literally, a twenty-agent fan-out is a
    // wall of rows that drowns the conversation, so `groupTranscript` rebuilds
    // the shape the run actually had.
    //
    // It is computed here rather than inside the transcript because the live
    // strip needs the same answer, and the fold walks the whole transcript
    // twice: doing it per surface would double that on every streamed chunk.
    val rows = remember(items) { groupTranscript(items) }
    // Then runs of finished reads and searches fold to one line ("Read 5
    // files, searched 2 patterns"); the live strip keeps the unfolded rows.
    val display = remember(rows) { ToolGroups.fold(rows) }

    CompositionLocalProvider(
        LocalProjectPath provides projectPath,
        LocalAwaitingTools provides awaitingToolIds,
    ) {
    Scaffold(
        modifier = modifier,
        containerColor = colors.canvas,
        topBar = {
            SpettroTopBar(
                title = title.ifBlank { "Untitled chat" },
                // In the tablet split shell the chat sits beside the list,
                // so there is nowhere to go "back" to.
                onBack = onBack.takeIf { showBack },
                actions = {
                    Box {
                        BarIconButton(
                            icon = Icons.Outlined.MoreVert,
                            contentDescription = "Chat menu",
                            onClick = { menuOpen = true },
                        )
                        SpettroMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            SpettroMenuItem(
                                text = if (isPinned) "Unpin" else "Pin",
                                icon = Icons.Outlined.PushPin,
                                onClick = {
                                    menuOpen = false
                                    onTogglePinned()
                                },
                            )
                            SpettroMenuItem(
                                text = if (isArchived) "Unarchive" else "Archive",
                                icon = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                onClick = {
                                    menuOpen = false
                                    onToggleArchived()
                                },
                            )
                            SpettroMenuSeparator()
                            SpettroMenuItem(
                                text = "Delete…",
                                icon = Icons.Outlined.DeleteOutline,
                                destructive = true,
                                onClick = {
                                    menuOpen = false
                                    confirmingDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (usage != null || modeName != null) {
                ReadableColumn {
                    ChatStatusStrip(
                        usage = usage,
                        modeName = modeName,
                        modeColorName = modeColorName,
                    )
                }
            }

            if (items.isEmpty()) {
                EmptyTranscript(onPrompt = onComposerTextChange, modifier = Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f)) {
                    Transcript(
                        rows = display,
                        modifier = Modifier.fillMaxSize(),
                        ticker = if (isBusy && runStartedAt != null) {
                            {
                                RunTicker(
                                    startedAt = runStartedAt,
                                    tokens = liveTokens,
                                    phase = RunPhase.of(items, awaitingApproval, awaitingAnswer),
                                    color = colors.modeColor(modeColorName ?: modeName),
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                    // The transcript fades out above the composer rather than
                    // meeting a hard edge (the desktop's 28px fade above
                    // .composer-wrap).
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Brush.verticalGradient(listOf(colors.canvas.copy(alpha = 0f), colors.canvas))),
                    )
                }
            }

            // The live edge of an in-flight run, pinned where the transcript
            // cannot scroll it away. It renders nothing when nothing is
            // orchestrating, which is almost always.
            ReadableColumn {
                OrchestrationLiveStrip(rows = rows)
            }

            if (waitingFor != null) {
                ReadableColumn {
                    PromptWaitingBar(text = waitingFor, onReview = onReviewPrompt)
                }
            }

            ReadableColumn {
                ChatComposer(
                    text = composerText,
                    onTextChange = onComposerTextChange,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onAddImages = onAddImages,
                    onSend = onSend,
                    onStop = onStop,
                    isBusy = isBusy,
                    enabled = enabled,
                    configSummary = configSummary,
                    onConfigTap = onConfigTap,
                    commands = commands,
                    onCommandPick = onCommandPick,
                    workflowGate = workflowGate,
                    onSwitchPermission = onSwitchPermission,
                    thinking = thinking,
                    thinkingFrame = thinkingFrame,
                    plan = plan,
                    planFolded = awaitingApproval || awaitingAnswer,
                )
            }
        }
    }

    }

    if (confirmingDelete) {
        DeleteChatDialog(
            title = title,
            isBusy = isBusy,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * Desktop-style readable measure: on wide screens the transcript, status
 * strip and composer cap out and recenter instead of stretching edge to
 * edge — the desktop's 760px reading measure. On phones the cap never
 * engages.
 */
private val ContentMaxWidth = Dimens.measure

@Composable
private fun ReadableColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
            content()
        }
    }
}

/**
 * The conversation, folded.
 *
 * The wire delivers a workflow as a flat run of tool calls — a lifecycle card,
 * one call per sub-agent, and every tool each of them runs, interleaved.
 * Rendered literally, a twenty-agent fan-out is a wall of rows that drowns the
 * conversation, so [groupTranscript] rebuilds the shape the run actually had
 * and the list renders *rows* rather than items.
 *
 * The fold is memoised on [items] because it walks the whole transcript twice
 * and this recomposes on every streamed chunk; recomputing it per frame is what
 * makes a long session crawl.
 */
@Composable
private fun Transcript(
    rows: List<DisplayRow>,
    modifier: Modifier,
    ticker: (@Composable () -> Unit)? = null,
) {
    // The run ticker, while a turn runs, is the list's last item.
    val count = rows.size + if (ticker != null) 1 else 0
    // A chat opens at its newest message: seed the list state at the end so
    // the first frame is already at the bottom — no visible jump.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (count - 1).coerceAtLeast(0),
    )

    // Auto-scroll: follow the conversation only while the reader is already
    // near the bottom, so scrolling back through history isn't hijacked.
    // Keyed on the rows themselves rather than their count: a workflow absorbing
    // twenty members leaves the count flat while the run is very much moving,
    // and the view would stop following it.
    LaunchedEffect(rows, ticker != null) {
        if (count == 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= count - 3) {
            listState.animateScrollToItem(count - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        items(rows, key = { it.id }) { row ->
            ReadableColumn {
                when (row) {
                    is DisplayRow.Row -> TranscriptRowView(row = row.row)
                    is DisplayRow.Tools -> ToolGroupRow(group = row)
                }
            }
        }
        if (ticker != null) {
            item(key = "run-ticker") {
                ReadableColumn { ticker() }
            }
        }
    }
}

/**
 * A chat with nothing in it yet: the desktop's greeting over its starter
 * prompts, each of which only fills the composer.
 */
@Composable
private fun EmptyTranscript(onPrompt: (String) -> Unit, modifier: Modifier) {
    val colors = LocalSpettroColors.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            modifier = Modifier.padding(Dimens.spacingXl),
        ) {
            Text(
                text = "What should we build?",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            StarterPrompts(onPrompt = onPrompt)
        }
    }
}

// MARK: Previews

@Composable
private fun ChatScreenPreviewHost(
    items: List<TranscriptItem>,
    isBusy: Boolean,
    darkTheme: Boolean,
) {
    SpettroTheme(darkTheme = darkTheme) {
        var text by remember { mutableStateOf("") }
        ChatScreen(
            title = "Fix the resume crash",
            items = items,
            isBusy = isBusy,
            runStartedAt = if (isBusy) System.currentTimeMillis() - 61_000 else null,
            liveTokens = if (isBusy) 9_400L else null,
            plan = if (items.isEmpty()) emptyList() else ChatPreviewData.plan,
            usage = if (items.isEmpty()) null else ChatPreviewData.usage,
            modeName = "Coding",
            modeColorName = "coding",
            isPinned = false,
            isArchived = false,
            composerText = text,
            onComposerTextChange = { text = it },
            attachments = emptyList(),
            onRemoveAttachment = {},
            onAddImages = {},
            onSend = { text = "" },
            onStop = {},
            enabled = true,
            configSummary = ChatPreviewData.CONFIG_SUMMARY,
            onConfigTap = {},
            commands = ChatPreviewData.commands,
            onCommandPick = {},
            onBack = {},
            onTogglePinned = {},
            onToggleArchived = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Conversation", widthDp = 380, heightDp = 780)
@Composable
private fun ChatScreenPreview() {
    ChatScreenPreviewHost(
        items = ChatPreviewData.transcript,
        isBusy = false,
        darkTheme = true,
    )
}

@Preview(name = "Streaming, busy", widthDp = 380, heightDp = 780)
@Composable
private fun ChatScreenPreviewBusy() {
    ChatScreenPreviewHost(
        items = ChatPreviewData.streamingTranscript,
        isBusy = true,
        darkTheme = true,
    )
}

@Preview(name = "Empty, light", widthDp = 380, heightDp = 780)
@Composable
private fun ChatScreenPreviewEmpty() {
    ChatScreenPreviewHost(
        items = emptyList(),
        isBusy = false,
        darkTheme = false,
    )
}

/**
 * A prompt of this chat's the user swiped away: it is still waiting —
 * a swipe answers nothing — so it stays in sight, amber like "Needs you",
 * with a way back to it.
 */
@Composable
private fun PromptWaitingBar(text: String, onReview: () -> Unit) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            .clip(shape)
            .background(colors.warning.copy(alpha = 0.12f).compositeOver(colors.surfaceRaised))
            .border(1.dp, colors.warning.copy(alpha = 0.35f), shape)
            .clickable(onClick = onReview)
            .padding(start = Dimens.spacingMd, end = 6.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = colors.warning)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SpettroButton("Review", onClick = onReview, size = ButtonSize.Small)
    }
}
