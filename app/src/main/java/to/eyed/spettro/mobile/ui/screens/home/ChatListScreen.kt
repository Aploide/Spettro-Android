package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.ui.components.AppIconImage
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
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
 * Android port of `MobileChatListView`. Stateless apart from presentation
 * details (search text, collapsed folders, the open menu); all data flows in
 * and every mutation flows out through a callback.
 *
 * @param chats visible (non-archived) chat summaries from `chats.list`;
 *   archived rows are filtered out defensively if present.
 * @param onPin `(chatID, pinned)`; @param onArchive `(chatID, archived)`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
) {
    val colors = LocalSpettroColors.current
    var search by rememberSaveable { mutableStateOf("") }
    // Stored as the set of *collapsed* paths so a newly appearing project
    // starts open — what you want when a chat is created somewhere you
    // weren't already looking.
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val visible = chats.filter { !it.isArchived }
    val filtered = if (search.isBlank()) {
        visible
    } else {
        visible.filter {
            it.title.contains(search, ignoreCase = true) ||
                it.preview.contains(search, ignoreCase = true)
        }
    }
    val groups = groupByProject(filtered)
    val now = System.currentTimeMillis()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.canvas,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Chats", fontWeight = FontWeight.SemiBold) },
                subtitle = {
                    if (hostName != null) {
                        Text(
                            hostName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "New chat")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Chat") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onNewChat()
                                },
                            )
                            if (archivedCount > 0) {
                                DropdownMenuItem(
                                    text = { Text("Archived ($archivedCount)") },
                                    leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenArchived()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.canvas,
                    scrolledContainerColor = colors.canvas,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!agentReady) {
                AgentDownBanner(hostName)
            }

            SearchField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimens.spacingXl),
                ) {
                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (search.isBlank()) {
                                    EmptyState(onNewChat = onNewChat)
                                } else {
                                    Text(
                                        "No chats match “$search”",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    groups.forEach { group ->
                        // A search forces every folder open: a match hidden
                        // inside a collapsed section reads as "no results".
                        val isCollapsed = search.isBlank() && group.path in collapsed
                        item(key = "header:${group.path}") {
                            ProjectHeader(
                                name = group.name,
                                count = group.chats.size,
                                collapsed = isCollapsed,
                                onToggle = {
                                    if (search.isBlank()) {
                                        collapsed = if (isCollapsed) {
                                            collapsed - group.path
                                        } else {
                                            collapsed + group.path
                                        }
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (!isCollapsed) {
                            items(group.chats, key = { it.id }) { chat ->
                                SwipeRevealRow(
                                    isPinned = chat.isPinned,
                                    onPinToggle = { onPin(chat.id, !chat.isPinned) },
                                    onArchive = { onArchive(chat.id, true) },
                                    onDelete = { onDelete(chat.id) },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    ChatRow(
                                        chat = chat,
                                        now = now,
                                        selected = chat.id == selectedChatId,
                                        onClick = { onOpenChat(chat.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Row

/**
 * One chat summary line: pinned marker, title, busy spinner or relative
 * time, then the preview with a subtle message count.
 */
@Composable
fun ChatRow(
    chat: ChatSummary,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    val tertiary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (selected) colors.accent.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        ) {
            if (chat.isPinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(10.dp),
                    tint = colors.accent,
                )
            }
            Text(
                chat.title.ifBlank { "Untitled chat" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Running beats a timestamp: while a turn is in flight, when the
            // chat last changed is both wrong and the less useful fact.
            if (chat.isBusy) {
                SpettroSpinner(size = 12.dp)
            } else {
                Text(
                    relativeTimeLabel(chat.updatedAt, now),
                    fontSize = 11.sp,
                    color = tertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            Text(
                chat.preview.ifBlank { "No messages yet" },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chat.messageCount > 0) {
                Text(
                    "${chat.messageCount}",
                    fontSize = 11.sp,
                    color = tertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
                )
            }
        }
    }
}

// MARK: - Sections

private data class ProjectGroup(
    val path: String,
    val name: String,
    val chats: List<ChatSummary>,
)

/**
 * Buckets chats by folder, mirroring the PC's sidebar. Folder order follows
 * the chats' own recency — the project you're working in floats to the top —
 * and within a folder pinned chats lead, then most recently updated.
 */
private fun groupByProject(chats: List<ChatSummary>): List<ProjectGroup> {
    val byRecency = chats.sortedByDescending { parseIsoMillis(it.updatedAt) ?: 0L }
    val buckets = LinkedHashMap<String, MutableList<ChatSummary>>()
    for (chat in byRecency) {
        buckets.getOrPut(chat.projectPath) { mutableListOf() }.add(chat)
    }
    return buckets.map { (path, group) ->
        ProjectGroup(
            path = path,
            name = path.trimEnd('/').substringAfterLast('/').ifEmpty { "No project" },
            chats = group.sortedWith(
                compareByDescending<ChatSummary> { it.isPinned }
                    .thenByDescending { parseIsoMillis(it.updatedAt) ?: 0L },
            ),
        )
    }
}

@Composable
private fun ProjectHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = secondary,
        )
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
            color = secondary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            modifier = Modifier.size(16.dp).rotate(rotation),
            tint = secondary.copy(alpha = 0.7f),
        )
    }
}

// MARK: - Chrome

/** The PC is attached but its agent isn't up: chats read-only for now. */
@Composable
private fun AgentDownBanner(hostName: String?) {
    val colors = LocalSpettroColors.current
    val amber = colors.modeColor("yellow")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(amber.copy(alpha = 0.12f))
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        StatusDot(color = amber, pulsing = true)
        Text(
            "Agent starting on ${hostName ?: "your PC"}… you can read chats but not send yet.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text("Search chats", fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimens.radiusInputPill),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceRaised,
            unfocusedContainerColor = colors.surfaceRaised,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun EmptyState(onNewChat: () -> Unit) {
    val colors = LocalSpettroColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        AppIconImage(size = 56.dp)
        Text(
            "No chats yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Start one here, or on your PC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onNewChat,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(Dimens.radiusLg),
        ) {
            Text("New Chat", fontWeight = FontWeight.SemiBold)
        }
    }
}

// MARK: - Swipe actions

private val ActionWidth = 76.dp

/**
 * Horizontal-reveal swipe actions: drag right for Pin/Unpin, drag left for
 * Archive + Delete. Hand-rolled on `draggable` + an [Animatable] rather than
 * `SwipeToDismissBox` because the trailing edge carries *two* tappable
 * actions, which dismiss-style swiping can't express.
 */
@Composable
private fun SwipeRevealRow(
    isPinned: Boolean,
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

    fun settle(target: Float) {
        scope.launch {
            offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clipToBounds(),
    ) {
        if (offset.value > 0.5f) {
            SwipeAction(
                label = if (isPinned) "Unpin" else "Pin",
                icon = Icons.Outlined.PushPin,
                background = colors.accent,
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
                    background = Color(0xFF8E8E93),
                    onClick = {
                        onArchive()
                        settle(0f)
                    },
                    modifier = Modifier.fillMaxHeight(),
                )
                SwipeAction(
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    background = colors.diffRemoved,
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
                .background(colors.canvas)
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
                        offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
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
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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

@Preview(name = "Chat list dark", showBackground = true, backgroundColor = 0xFF0E0E0E)
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

@Preview(name = "Agent down — light", showBackground = true, backgroundColor = 0xFFF9F9F7)
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

@Preview(name = "Empty", showBackground = true, backgroundColor = 0xFF0E0E0E)
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
