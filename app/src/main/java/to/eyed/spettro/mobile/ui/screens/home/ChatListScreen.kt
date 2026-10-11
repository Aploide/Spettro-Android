package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import to.eyed.spettro.mobile.ui.components.SpettroMenu
import to.eyed.spettro.mobile.ui.components.SpettroMenuItem
import to.eyed.spettro.mobile.ui.components.SpettroMenuSeparator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.ui.components.BarIconButton
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.EmptyState
import to.eyed.spettro.mobile.ui.components.Pill
import to.eyed.spettro.mobile.ui.components.PillTone
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every chat the host has, grouped by the project folder it runs in — the
 * phone's version of the desktop's sidebar: quiet type, rows that are text
 * until something about them matters (selected, running into a question),
 * project captions in the tertiary ink. Stateless apart from presentation
 * details (search text, collapsed folders); all data flows in and every
 * mutation flows out through a callback.
 *
 * Rendered twice by AppRoot: full-screen on a phone, and as the split
 * shell's sidebar on a tablet ([inSidebar]), where it takes the desktop's
 * sidebar ground and shows the selected chat.
 *
 * @param chats visible (non-archived) chat summaries from `chats.list`;
 *   archived rows are filtered out defensively if present.
 * @param onPin `(chatID, pinned)`; @param onArchive `(chatID, archived)`.
 * @param needsYou the chats a permission or question is waiting in.
 * @param onRename `(chatID, title)`; null when the computer can't rename
 *   chats (an older host), and Rename… isn't offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatSummary>,
    archivedCount: Int,
    agentReady: Boolean,
    hostName: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onPin: (String, Boolean) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchived: () -> Unit,
    modifier: Modifier = Modifier,
    selectedChatId: String? = null,
    needsYou: Set<String> = emptySet(),
    inSidebar: Boolean = false,
    onRename: ((String, String) -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    val ground = if (inSidebar) colors.sidebar else colors.canvas
    var search by rememberSaveable { mutableStateOf("") }
    // Stored as the set of *collapsed* paths so a newly appearing project
    // starts open — what you want when a chat is created somewhere you
    // weren't already looking.
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    // The chat whose Delete was tapped, waiting on "Delete this chat?".
    var confirmingDelete by remember { mutableStateOf<ChatSummary?>(null) }

    // The chat whose long-press menu is open, and the one being renamed.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ChatSummary?>(null) }

    val groups = SessionGroups.group(chats.filter { !it.isArchived }, search).groups
    val filtered = groups.flatMap { it.chats }
    val now = System.currentTimeMillis()

    Column(modifier.fillMaxSize().background(ground)) {
        ListHeader(
            hostName = hostName,
            agentReady = agentReady,
            compact = inSidebar,
            onOpenSettings = onOpenSettings,
            onNewChat = onNewChat,
        )

        if (!agentReady) {
            AgentDownBanner(hostName)
        }

        SearchField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.spacingSm,
                    end = Dimens.spacingSm,
                    top = Dimens.spacingSm,
                    bottom = Dimens.spacingXl,
                ),
            ) {
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (search.isBlank()) {
                                EmptyState(
                                    title = "No chats yet",
                                    message = "Start one here, or on your computer.",
                                    action = {
                                        SpettroButton("New chat", onClick = onNewChat, style = ButtonStyle.Prominent)
                                    },
                                )
                            } else {
                                EmptyState(title = "No chats match “$search”", message = null)
                            }
                        }
                    }
                }
                groups.forEachIndexed { index, group ->
                    // A search forces every folder open: a match hidden
                    // inside a collapsed section reads as "no results".
                    val isCollapsed = search.isBlank() && group.path in collapsed
                    item(key = "header:${group.path}") {
                        ProjectHeader(
                            name = group.name,
                            count = group.chats.size,
                            collapsed = isCollapsed,
                            // Folded away, a waiting chat still shows.
                            waiting = isCollapsed && group.chats.any { it.id in needsYou },
                            onToggle = {
                                if (search.isBlank()) {
                                    collapsed = if (isCollapsed) {
                                        collapsed - group.path
                                    } else {
                                        collapsed + group.path
                                    }
                                }
                            },
                            modifier = Modifier
                                .animateItem()
                                .padding(top = if (index == 0) 0.dp else Dimens.spacingMd),
                        )
                    }
                    if (!isCollapsed) {
                        items(group.chats, key = { it.id }) { chat ->
                            SwipeRevealRow(
                                isPinned = chat.isPinned,
                                ground = ground,
                                onPinToggle = { onPin(chat.id, !chat.isPinned) },
                                onArchive = { onArchive(chat.id, true) },
                                onDelete = { confirmingDelete = chat },
                                modifier = Modifier.animateItem(),
                            ) {
                                Box {
                                    ChatRow(
                                        chat = chat,
                                        now = now,
                                        selected = chat.id == selectedChatId,
                                        // The open chat's prompt is already in its
                                        // dock beside the list (the desktop's
                                        // `needsYou && !isSelected`).
                                        needsYou = chat.id in needsYou && chat.id != selectedChatId,
                                        onClick = { onOpenChat(chat.id) },
                                        onLongClick = { menuFor = chat.id },
                                        ground = ground,
                                    )
                                    // The desktop's row menu: Rename…, Pin,
                                    // Archive, then Delete… apart.
                                    SpettroMenu(expanded = menuFor == chat.id, onDismissRequest = { menuFor = null }) {
                                        if (onRename != null) {
                                            SpettroMenuItem("Rename…", icon = Icons.Outlined.Edit, onClick = {
                                                menuFor = null
                                                renaming = chat
                                            })
                                        }
                                        SpettroMenuItem(if (chat.isPinned) "Unpin" else "Pin", icon = Icons.Outlined.PushPin, onClick = {
                                            menuFor = null
                                            onPin(chat.id, !chat.isPinned)
                                        })
                                        SpettroMenuItem("Archive", icon = Icons.Outlined.Archive, onClick = {
                                            menuFor = null
                                            onArchive(chat.id, true)
                                        })
                                        SpettroMenuSeparator()
                                        SpettroMenuItem("Delete…", icon = Icons.Outlined.DeleteOutline, destructive = true, onClick = {
                                            menuFor = null
                                            confirmingDelete = chat
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (archivedCount > 0 && search.isBlank()) {
                    item(key = "archived") {
                        ArchivedRow(
                            count = archivedCount,
                            onClick = onOpenArchived,
                            modifier = Modifier.animateItem().padding(top = Dimens.spacingMd),
                        )
                    }
                }
            }
        }
    }

    renaming?.let { chat ->
        if (onRename != null) {
            RenameChatDialog(
                title = chat.title,
                onRename = { title ->
                    renaming = null
                    onRename(chat.id, title)
                },
                onDismiss = { renaming = null },
            )
        }
    }

    confirmingDelete?.let { chat ->
        DeleteChatDialog(
            title = chat.title,
            isBusy = chat.isBusy,
            onConfirm = {
                confirmingDelete = null
                onDelete(chat.id)
            },
            onDismiss = { confirmingDelete = null },
        )
    }
}

/**
 * The list's head: "Chats", the computer it mirrors with a status dot, and
 * the two actions. In the sidebar it is the desktop's compact top row.
 */
@Composable
private fun ListHeader(
    hostName: String?,
    agentReady: Boolean,
    compact: Boolean,
    onOpenSettings: () -> Unit,
    onNewChat: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingXs, top = if (compact) 4.dp else 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Chats",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            if (hostName != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = if (agentReady) colors.success else colors.warning, size = 6.dp)
                    Text(
                        hostName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        BarIconButton(Icons.Outlined.Settings, contentDescription = "Settings", onClick = onOpenSettings)
        BarIconButton(Icons.Outlined.Add, contentDescription = "New chat", onClick = onNewChat, tint = colors.textPrimary)
    }
}

// MARK: - Row

/**
 * One chat: pinned marker, title, then "Needs you", a spinner while running,
 * or the relative time; the preview under it with a quiet message count.
 * At rest the title is in the secondary ink, as in the desktop's sidebar; it
 * comes forward (primary, 500) when selected or waiting on you.
 *
 * @param needsYou a permission or question is waiting in this chat.
 * @param ground the list's background (the sidebar's, in the split shell).
 */
@Composable
fun ChatRow(
    chat: ChatSummary,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    selected: Boolean = false,
    needsYou: Boolean = false,
    onClick: (() -> Unit)? = null,
    ground: Color = LocalSpettroColors.current.canvas,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    val forward = selected || needsYou
    // The selected fill darkens a light sidebar just enough to take the
    // tertiary ink under AA; nudge it back over the row's real ground.
    val rowGround = if (selected) colors.selectedFill.compositeOver(ground) else ground
    val quiet = colors.readable(colors.textTertiary, on = rowGround)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(if (selected) colors.selectedFill else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Chat actions")
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = Dimens.minTouch)
            .padding(horizontal = Dimens.spacingSm, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chat.isPinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(11.dp),
                    tint = quiet,
                )
            }
            Text(
                chat.title.ifBlank { "Untitled chat" },
                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                fontWeight = if (forward) FontWeight.Medium else FontWeight.Normal,
                color = if (forward) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Waiting on the user outranks everything: the chat is stuck
            // until someone answers. Then running beats a timestamp: while a
            // turn is in flight, when the chat last changed is both wrong
            // and the less useful fact.
            if (needsYou) {
                Pill("Needs you", tone = PillTone.Warning, ground = rowGround)
            } else if (chat.isBusy) {
                SpettroSpinner(size = 12.dp)
            } else {
                Text(
                    relativeTimeLabel(chat.updatedAt, now),
                    color = quiet,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontFeatureSettings = TabularNums,
                    ),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            Text(
                chat.preview.ifBlank { "No messages yet" },
                style = MaterialTheme.typography.bodySmall,
                color = quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chat.messageCount > 0) {
                Text(
                    "${chat.messageCount}",
                    color = quiet,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontFeatureSettings = TabularNums,
                    ),
                )
            }
        }
    }
}

// MARK: - Sections

/**
 * A project caption (`.section-header`): the folder's name small and muted
 * in the tertiary ink, its count, and a chevron that turns as it opens
 * (120ms, no bounce). A collapsed group with a chat waiting on you shows an
 * amber dot.
 */
@Composable
private fun ProjectHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    waiting: Boolean = false,
) {
    val colors = LocalSpettroColors.current
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = tween(SpettroMotion.QUICK_MS),
        label = "chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusXs))
            .clickable(onClick = onToggle)
            .height(34.dp)
            .padding(horizontal = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            modifier = Modifier.size(15.dp).rotate(rotation),
            tint = colors.textTertiary,
        )
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = TabularNums),
            color = colors.textTertiary,
        )
        if (waiting) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(colors.warning, CircleShape)
                    .semantics { contentDescription = "A chat here needs you" },
            )
        }
    }
}

/** The way into the archive, at the foot of the list. */
@Composable
private fun ArchivedRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        Icon(Icons.Outlined.Archive, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        Text("Archived", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = TabularNums),
            color = colors.textTertiary,
        )
    }
}

// MARK: - Chrome

/**
 * The computer is attached but Spettro isn't running on it yet: chats are
 * read-only for now. The desktop's no-model bar look: amber at 12% on the
 * raised surface, an amber 35% border.
 */
@Composable
private fun AgentDownBanner(hostName: String?) {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            .background(colors.warning.copy(alpha = 0.12f).compositeOver(colors.surfaceRaised), shape)
            .border(1.dp, colors.warning.copy(alpha = 0.35f), shape)
            .padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        StatusDot(color = colors.warning, pulsing = true)
        Text(
            "Spettro is starting on ${hostName ?: "your computer"}. You can read chats; sending waits until it’s ready.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
        )
    }
}

/** The desktop's sidebar search: a quiet 40dp field on the hover fill, 8dp corners. */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .background(colors.hoverFill, RoundedCornerShape(Dimens.radiusSm))
                    .height(40.dp)
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = colors.textTertiary,
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text("Search chats", style = TextStyle(fontSize = 14.sp), color = colors.textSecondary)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onValueChange("") }
                            .padding(8.dp),
                    )
                }
            }
        },
    )
}

// MARK: - Swipe actions

private val ActionWidth = 76.dp

/**
 * Horizontal-reveal swipe actions: drag right for Pin/Unpin, drag left for
 * Archive + Delete. Hand-rolled on `draggable` + an [Animatable] rather than
 * `SwipeToDismissBox` because the trailing edge carries *two* tappable
 * actions, which dismiss-style swiping can't express. Settles with a short
 * ease-out, never a spring.
 */
@Composable
private fun SwipeRevealRow(
    isPinned: Boolean,
    ground: Color,
    onPinToggle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val scope = rememberCoroutineScope()
    val actionPx = with(LocalDensity.current) { ActionWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val settleSpec = tween<Float>(SpettroMotion.PROMPT_MS, easing = SpettroMotion.PromptEasing)

    fun settle(target: Float) {
        scope.launch { offset.animateTo(target, settleSpec) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clipToBounds(),
    ) {
        if (offset.value > 0.5f) {
            SwipeAction(
                label = if (isPinned) "Unpin" else "Pin",
                icon = Icons.Outlined.PushPin,
                background = colors.accent,
                content = colors.accentContrast,
                onClick = {
                    onPinToggle()
                    settle(0f)
                },
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
            )
        }
        if (offset.value < -0.5f) {
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                SwipeAction(
                    label = "Archive",
                    icon = Icons.Outlined.Archive,
                    background = colors.textTertiary,
                    content = colors.canvas,
                    onClick = {
                        onArchive()
                        settle(0f)
                    },
                    modifier = Modifier.fillMaxHeight(),
                )
                SwipeAction(
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    background = colors.dangerFill,
                    content = colors.dangerContrast,
                    onClick = {
                        onDelete()
                        settle(0f)
                    },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(ground)
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo(
                                (offset.value + delta).coerceIn(-2f * actionPx, actionPx),
                            )
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val flung = abs(velocity) > 800f
                        val target = when {
                            offset.value > 0f && (offset.value > actionPx / 2f || (flung && velocity > 0f)) ->
                                actionPx
                            offset.value < 0f && (offset.value < -actionPx || (flung && velocity < 0f)) ->
                                -2f * actionPx
                            else -> 0f
                        }
                        offset.animateTo(target, settleSpec)
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    icon: ImageVector,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(ActionWidth)
            .background(background)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = content, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// MARK: - Time labels

/**
 * ISO-8601 → epoch millis without java.time (minSdk 24, no desugaring).
 * Over-long fractional seconds are trimmed to 3 digits first, because
 * `SSS` would otherwise read `.123456` as 123456 ms.
 */
internal fun parseIsoMillis(iso: String): Long? {
    val trimmed = iso.trim()
    if (trimmed.isEmpty()) return null
    val normalized = trimmed.replace(Regex("""\.(\d{3})\d+"""), ".$1")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
    )
    for (pattern in patterns) {
        try {
            val format = SimpleDateFormat(pattern, Locale.US)
            // Only reached by the zone-less patterns; the wire format is UTC.
            if (!pattern.endsWith("XXX")) format.timeZone = TimeZone.getTimeZone("UTC")
            return format.parse(normalized)?.time
        } catch (_: ParseException) {
            // Try the next shape.
        }
    }
    return null
}

/** "now", "2m", "3h", "yesterday", "4d", then "Jan 5" (with year if older). */
internal fun relativeTimeLabel(iso: String, now: Long = System.currentTimeMillis()): String {
    val then = parseIsoMillis(iso) ?: return ""
    val diff = now - then
    if (diff < 60_000L) return "now"
    val minutes = diff / 60_000L
    if (minutes < 60) return "${minutes}m"
    val hours = diff / 3_600_000L
    if (hours < 24) return "${hours}h"
    if (dayStamp(then) == dayStamp(now - 86_400_000L)) return "yesterday"
    val days = diff / 86_400_000L
    if (days < 7) return "${days}d"
    val pattern = if (yearOf(then) == yearOf(now)) "MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(Date(then))
}

private fun dayStamp(millis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return calendar.get(Calendar.YEAR) * 1_000 + calendar.get(Calendar.DAY_OF_YEAR)
}

private fun yearOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

// MARK: - Previews

internal fun previewChats(): List<ChatSummary> = listOf(
    ChatSummary(
        id = "1",
        title = "Fix flaky auth test",
        projectPath = "/Users/carlo/dev/spettro",
        updatedAt = "2026-07-30T09:12:00Z",
        isPinned = true,
        messageCount = 24,
        preview = "The retry loop was masking a race in the token refresh.",
    ),
    ChatSummary(
        id = "2",
        title = "Investigate build failure",
        projectPath = "/Users/carlo/dev/spettro",
        updatedAt = "2026-07-30T11:55:00Z",
        isBusy = true,
        messageCount = 7,
        preview = "Checking whether Textual is still pinned to the old tag…",
    ),
    ChatSummary(
        id = "3",
        title = "Port the pairing screen",
        projectPath = "/Users/carlo/dev/SpettroAndroid",
        updatedAt = "2026-07-29T18:03:00Z",
        messageCount = 61,
        preview = "Done — CameraX + ML Kit scanner, QR-only.",
    ),
    ChatSummary(
        id = "4",
        title = "Untouched chat",
        projectPath = "/Users/carlo/dev/SpettroAndroid",
        updatedAt = "2026-06-02T10:00:00Z",
        messageCount = 0,
        preview = "",
    ),
)

@Preview(name = "Chat list dark", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ChatListPreview() {
    SpettroTheme(darkTheme = true) {
        ChatListScreen(
            chats = previewChats(),
            archivedCount = 2,
            agentReady = true,
            hostName = "Carlo's MacBook Pro",
            isRefreshing = false,
            onRefresh = {},
            onOpenChat = {},
            onNewChat = {},
            onPin = { _, _ -> },
            onArchive = { _, _ -> },
            onDelete = {},
            onOpenSettings = {},
            onOpenArchived = {},
        )
    }
}

@Preview(name = "Agent down — light", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun ChatListAgentDownPreview() {
    SpettroTheme(darkTheme = false) {
        ChatListScreen(
            chats = previewChats(),
            archivedCount = 0,
            agentReady = false,
            hostName = "Carlo's MacBook Pro",
            isRefreshing = false,
            onRefresh = {},
            onOpenChat = {},
            onNewChat = {},
            onPin = { _, _ -> },
            onArchive = { _, _ -> },
            onDelete = {},
            onOpenSettings = {},
            onOpenArchived = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun ChatListEmptyPreview() {
    SpettroTheme(darkTheme = true) {
        ChatListScreen(
            chats = emptyList(),
            archivedCount = 0,
            agentReady = true,
            hostName = null,
            isRefreshing = false,
            onRefresh = {},
            onOpenChat = {},
            onNewChat = {},
            onPin = { _, _ -> },
            onArchive = { _, _ -> },
            onDelete = {},
            onOpenSettings = {},
            onOpenArchived = {},
        )
    }
}
