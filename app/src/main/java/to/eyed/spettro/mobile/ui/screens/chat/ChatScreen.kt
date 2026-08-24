package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpCommand
import to.eyed.spettro.mobile.core.acp.AcpPlanEntry
import to.eyed.spettro.mobile.core.acp.AcpUsage
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.model.TranscriptItem
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
    configSummary: String?,
    onConfigTap: () -> Unit,
    commands: List<AcpCommand>,
    onCommandPick: (AcpCommand) -> Unit,
    onBack: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val colors = LocalSpettroColors.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // The wire delivers a workflow or an Ultra swarm as a flat run of tool
    // calls — a lifecycle call, one call per sub-agent, and every tool each of
    // them runs, interleaved. Rendered literally, a twenty-agent fan-out is a
    // wall of rows that drowns the conversation, so `groupTranscript` rebuilds
    // the shape the run actually had.
    //
    // It is computed here rather than inside the transcript because the live
    // strip needs the same answer, and the fold walks the whole transcript
    // twice: doing it per surface would double that on every streamed chunk.
    val rows = remember(items) { groupTranscript(items) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    // In the tablet split shell the chat sits beside the list,
                    // so there is nowhere to go "back" to.
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "Chat menu",
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Unpin" else "Pin") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.PushPin, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onTogglePinned()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (isArchived) "Unarchive" else "Archive") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isArchived) {
                                            Icons.Outlined.Unarchive
                                        } else {
                                            Icons.Outlined.Archive
                                        },
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onToggleArchived()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Chat", color = colors.diffRemoved) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        tint = colors.diffRemoved,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    confirmingDelete = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.canvas,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
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
            if (isBusy || plan.isNotEmpty() || usage != null || modeName != null) {
                ReadableColumn {
                    ChatStatusStrip(
                        isBusy = isBusy,
                        runStartedAt = runStartedAt,
                        liveTokens = liveTokens,
                        plan = plan,
                        usage = usage,
                        modeName = modeName,
                        modeColorName = modeColorName,
                    )
                }
            }

            if (items.isEmpty()) {
                EmptyTranscript(modifier = Modifier.weight(1f))
            } else {
                Transcript(rows = rows, modifier = Modifier.weight(1f))
            }

            // The live edge of an in-flight run, pinned where the transcript
            // cannot scroll it away. It renders nothing when nothing is
            // orchestrating, which is almost always.
            ReadableColumn {
                OrchestrationLiveStrip(rows = rows)
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
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this chat?") },
            text = { Text("The conversation is removed from the host. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = colors.diffRemoved)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surfaceRaised,
        )
    }
}

/**
 * Desktop-style readable measure: on wide screens the transcript, status
 * strip and composer cap out and recenter instead of stretching edge to
 * edge — the same 820px cap the PC app puts on its transcript column. On
 * phones the cap never engages.
 */
private val ContentMaxWidth = 820.dp

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
 * The wire delivers a workflow or an Ultra swarm as a flat run of tool calls —
 * a lifecycle call, one call per sub-agent, and every tool each of them runs,
 * interleaved. Rendered literally, a twenty-agent fan-out is a wall of rows
 * that drowns the conversation, so [groupTranscript] rebuilds the shape the run
 * actually had and the list renders *rows* rather than items.
 *
 * The fold is memoised on [items] because it walks the whole transcript twice
 * and this recomposes on every streamed chunk; recomputing it per frame is what
 * makes a long session crawl.
 */
@Composable
private fun Transcript(rows: List<TranscriptRow>, modifier: Modifier) {
    // A chat opens at its newest message: seed the list state at the end so
    // the first frame is already at the bottom — no visible jump.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (rows.size - 1).coerceAtLeast(0),
    )

    // Auto-scroll: follow the conversation only while the reader is already
    // near the bottom, so scrolling back through history isn't hijacked.
    // Keyed on the rows themselves rather than their count: a swarm absorbing
    // twenty members leaves the count flat while the run is very much moving,
    // and the view would stop following it.
    LaunchedEffect(rows) {
        if (rows.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= rows.size - 2) {
            listState.animateScrollToItem(rows.size - 1)
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
                TranscriptRowView(row = row)
            }
        }
    }
}

@Composable
private fun EmptyTranscript(modifier: Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        ) {
            AppIconImage(size = 48.dp)
            Text(
                text = "Start the conversation",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
            modeName = "coding",
            modeColorName = "green",
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
