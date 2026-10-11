package to.eyed.spettro.mobile.ui.screens.chat

import to.eyed.spettro.mobile.ui.components.SpettroSwitch
import to.eyed.spettro.mobile.ui.components.SpettroSheet
import to.eyed.spettro.mobile.ui.components.SegmentedControl
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.model.ConfigValue
import to.eyed.spettro.mobile.model.Thinking
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.components.SectionHeader
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Mode, model, permission, and whatever else the agent advertises — as a
 * grouped bottom sheet. Thinking and Ultra are not here: they are one dial,
 * the composer's thinking chip and its slider (the desktop's ELSEWHERE), and
 * a second switch for Ultra is how the two used to disagree.
 *
 * Presentation is chosen per option: compact flat selects (mode, permission)
 * render as a horizontal segmented toggle; big or grouped lists (model)
 * render as a navigation row that opens a full picker page inside the sheet;
 * everything else falls back to check rows.
 *
 * Selecting calls straight back; the host applies the change asynchronously.
 * [pendingValues] lets the caller overlay optimistic values (choices made but
 * not yet confirmed by the agent) so the selection follows the tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConfigSheet(
    options: List<AcpConfigOption>,
    pendingValues: Map<String, ConfigValue>?,
    onSetString: (configId: String, value: String) -> Unit,
    onSetBool: (configId: String, value: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SpettroSheet(onDismissRequest = onDismiss) {
        ChatConfigSheetContent(
            options = options,
            pendingValues = pendingValues,
            onSetString = onSetString,
            onSetBool = onSetBool,
        )
    }
}

/** Category display order: mode, model, permission, workflow size, rest. */
private fun categoryRank(option: AcpConfigOption): Int =
    when (option.category ?: option.id) {
        "mode" -> 0
        "model" -> 1
        "permission" -> 2
        "workflow_size" -> 3
        else -> 4
    }

/** Options with a control of their own elsewhere: the thinking chip, whose slider also holds Ultra. */
private val ELSEWHERE = setOf(Thinking.THINKING_ID, Thinking.ULTRA_ID)

private fun categoryIcon(option: AcpConfigOption): ImageVector =
    when (option.category ?: option.id) {
        "mode" -> Icons.Outlined.Tune
        "model" -> Icons.Outlined.Memory
        "permission" -> Icons.Outlined.Shield
        else -> Icons.Outlined.Settings
    }

private enum class SelectStyle { Segmented, Drill, Rows }

/**
 * Segmented needs few, flat, short choices; a grouped or long list (models)
 * reads better as its own page.
 */
private fun selectStyle(kind: AcpConfigOption.Kind.Select): SelectStyle {
    val grouped = kind.groups.any { !it.name.isNullOrEmpty() }
    val count = kind.groups.sumOf { it.options.size }
    return when {
        grouped || count > 6 -> SelectStyle.Drill
        count in 2..4 -> SelectStyle.Segmented
        else -> SelectStyle.Rows
    }
}

@Composable
internal fun ChatConfigSheetContent(
    options: List<AcpConfigOption>,
    pendingValues: Map<String, ConfigValue>?,
    onSetString: (String, String) -> Unit,
    onSetBool: (String, Boolean) -> Unit,
) {
    val ordered = options.filterNot { it.id in ELSEWHERE }.sortedBy(::categoryRank)
    var drillId by rememberSaveable { mutableStateOf<String?>(null) }
    val drill = ordered.firstOrNull { it.id == drillId }

    AnimatedContent(
        targetState = drill,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "config-drill",
    ) { page ->
        val pageKind = page?.kind as? AcpConfigOption.Kind.Select
        if (page != null && pageKind != null) {
            DrillPage(
                option = page,
                kind = pageKind,
                pending = pendingValues?.get(page.id),
                onSelect = { value ->
                    onSetString(page.id, value)
                    drillId = null
                },
                onBack = { drillId = null },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingLg,
                    bottom = Dimens.spacingXl,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            ) {
                items(ordered, key = { it.id }) { option ->
                    when (val kind = option.kind) {
                        is AcpConfigOption.Kind.Select -> when (selectStyle(kind)) {
                            SelectStyle.Segmented -> SegmentedSection(
                                option = option,
                                kind = kind,
                                pending = pendingValues?.get(option.id),
                                onSelect = { onSetString(option.id, it) },
                            )
                            SelectStyle.Drill -> DrillEntrySection(
                                option = option,
                                kind = kind,
                                pending = pendingValues?.get(option.id),
                                onOpen = { drillId = option.id },
                            )
                            SelectStyle.Rows -> RowsSection(
                                option = option,
                                kind = kind,
                                pending = pendingValues?.get(option.id),
                                onSelect = { onSetString(option.id, it) },
                            )
                        }
                        is AcpConfigOption.Kind.Bool -> BoolSection(
                            option = option,
                            kind = kind,
                            pending = pendingValues?.get(option.id),
                            onToggle = { onSetBool(option.id, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(option: AcpConfigOption) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = Dimens.spacingSm),
    ) {
        Icon(
            imageVector = categoryIcon(option),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        SectionHeader(text = option.name)
    }
}

private fun currentValue(kind: AcpConfigOption.Kind.Select, pending: ConfigValue?): String? =
    (pending as? ConfigValue.Str)?.value ?: kind.current

private fun labelFor(kind: AcpConfigOption.Kind.Select, value: String?): String? =
    kind.groups.asSequence()
        .flatMap { it.options.asSequence() }
        .firstOrNull { it.value == value }
        ?.name

/**
 * The permission levels in the desktop's words (SessionSettingsPopover):
 * a short name a first-time user can act on — never the CLI's "YOLO" — and
 * what each level means in practice, ahead of the CLI's own description. A
 * level this app doesn't know keeps the CLI's name and description.
 */
internal object PermissionCopy {
    private val NAMES = mapOf(
        "ask-first" to "Ask first",
        "restricted" to "Restricted",
        "yolo" to "Don’t ask",
    )

    private val DESCRIPTIONS = mapOf(
        "ask-first" to "Spettro asks you before it changes a file or runs anything — safest, slowest.",
        "restricted" to "Spettro works in your project on its own and asks before anything risky.",
        "yolo" to "Spettro does everything without asking — fastest, least safe.",
    )

    fun name(value: String?): String? = NAMES[value]

    fun description(value: String?): String? = DESCRIPTIONS[value]
}

/**
 * What each mode does, in the desktop's words (ConfigBar `MODE_COPY`): the
 * CLI describes its modes by the agent that runs them, which is accurate and
 * means nothing to the person picking one. A mode this app doesn't know
 * keeps the CLI's own text.
 */
internal object ModeCopy {
    private val DESCRIPTIONS = mapOf(
        "plan" to "Think it through and propose a plan before changing anything",
        "coding" to "Make the changes for you",
        "ask" to "Answer questions without changing any files",
    )

    fun description(value: String?): String? = DESCRIPTIONS[value]
}

/** A compact flat choice — the N-way horizontal toggle (mode, permission). */
@Composable
private fun SegmentedSection(
    option: AcpConfigOption,
    kind: AcpConfigOption.Kind.Select,
    pending: ConfigValue?,
    onSelect: (String) -> Unit,
) {
    val colors = LocalSpettroColors.current
    val current = currentValue(kind, pending)
    val choices = kind.groups.flatMap { it.options }
    val isPermission = option.id == "permission"
    Column {
        SectionLabel(option)
        // The desktop's segmented control: a tinted track with the active
        // choice raised on it.
        SegmentedControl(
            options = choices,
            selected = choices.firstOrNull { it.value == current } ?: choices.first(),
            onSelect = { onSelect(it.value) },
            label = { choice -> PermissionCopy.name(choice.value)?.takeIf { isPermission } ?: choice.name },
            modifier = Modifier.fillMaxWidth(),
        )
        // Segments have no room for descriptions; explain the active choice below.
        val active = choices.firstOrNull { it.value == current }
        val described = when (option.id) {
            "permission" -> PermissionCopy.description(active?.value)
            "mode" -> ModeCopy.description(active?.value)
            else -> null
        }
        (described ?: active?.description ?: option.description)?.takeIf { it.isNotEmpty() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.spacingXs, top = Dimens.spacingSm),
            )
        }
    }
}

/** The navigation row for big pickers: current value + chevron → full page. */
@Composable
private fun DrillEntrySection(
    option: AcpConfigOption,
    kind: AcpConfigOption.Kind.Select,
    pending: ConfigValue?,
    onOpen: () -> Unit,
) {
    val current = currentValue(kind, pending)
    Column {
        SectionLabel(option)
        SpettroCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = Dimens.spacingMd, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (option.id == "model") {
                        ConfigSummary.modelName(kind, current) ?: ConfigSummary.NO_MODEL
                    } else {
                        labelFor(kind, current) ?: current ?: "Choose…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        option.description?.takeIf { it.isNotEmpty() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.spacingMd, top = Dimens.spacingXs),
            )
        }
    }
}

/** The full picker page a [DrillEntrySection] opens (e.g. every model). */
@Composable
private fun DrillPage(
    option: AcpConfigOption,
    kind: AcpConfigOption.Kind.Select,
    pending: ConfigValue?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val current = currentValue(kind, pending)
    var query by rememberSaveable(option.id) { mutableStateOf("") }
    // A group whose *name* matches keeps all its options (searching "openai"
    // should list that provider's models); otherwise options match on their
    // own name, description, or wire value.
    val visibleGroups = remember(kind, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            kind.groups.mapIndexed { index, group -> index to group }
        } else {
            kind.groups.mapIndexedNotNull { index, group ->
                if (group.name?.contains(needle, ignoreCase = true) == true) {
                    return@mapIndexedNotNull index to group
                }
                val matches = group.options.filter { choice ->
                    choice.name.contains(needle, ignoreCase = true) ||
                        choice.value.contains(needle, ignoreCase = true) ||
                        choice.description?.contains(needle, ignoreCase = true) == true
                }
                if (matches.isEmpty()) null else index to group.copy(options = matches)
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = option.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DrillSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search ${option.name.lowercase()}s…",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs),
        )
        if (visibleGroups.isEmpty()) {
            Text(
                text = "Nothing matches “${query.trim()}”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Dimens.spacingLg,
                    vertical = Dimens.spacingXl,
                ),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingXl,
            ),
        ) {
            visibleGroups.forEachIndexed { visibleIndex, (groupIndex, group) ->
                group.name?.takeIf { it.isNotEmpty() }?.let { name ->
                    item(key = "group-$groupIndex") {
                        Text(
                            text = name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(
                                start = Dimens.spacingXs,
                                top = if (visibleIndex == 0) 0.dp else Dimens.spacingLg,
                                bottom = Dimens.spacingXs,
                            ),
                        )
                    }
                }
                items(group.options, key = { "$groupIndex-${it.value}" }) { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice.value) }
                            .padding(horizontal = Dimens.spacingXs, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = choice.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            choice.description?.takeIf { it.isNotEmpty() }?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (choice.value == current) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HairlineDivider()
                }
            }
        }
    }
}

/**
 * The picker page's search bar: the same expressive pill as the composer —
 * raised surface, hairline stroke warming to the accent on focus, accent
 * cursor — with a magnifier lead and a clear button that appears with the
 * first character.
 */
@Composable
private fun DrillSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.accent.copy(alpha = 0.5f) else colors.hairline,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "searchBorder",
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(colors.accent),
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(Dimens.hairlineWidth, borderColor, shape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(horizontal = Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (focused) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

/** Middling flat lists stay as check rows in a card. */
@Composable
private fun RowsSection(
    option: AcpConfigOption,
    kind: AcpConfigOption.Kind.Select,
    pending: ConfigValue?,
    onSelect: (String) -> Unit,
) {
    val colors = LocalSpettroColors.current
    val current = currentValue(kind, pending)
    Column {
        SectionLabel(option)
        SpettroCard(modifier = Modifier.fillMaxWidth()) {
            kind.groups.flatMap { it.options }.forEachIndexed { index, choice ->
                if (index > 0) {
                    HairlineDivider(modifier = Modifier.padding(horizontal = Dimens.spacingMd))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(choice.value) }
                        .padding(horizontal = Dimens.spacingMd, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = choice.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        choice.description?.takeIf { it.isNotEmpty() }?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (choice.value == current) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        option.description?.takeIf { it.isNotEmpty() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.spacingMd, top = Dimens.spacingXs),
            )
        }
    }
}

@Composable
private fun BoolSection(
    option: AcpConfigOption,
    kind: AcpConfigOption.Kind.Bool,
    pending: ConfigValue?,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalSpettroColors.current
    val current = (pending as? ConfigValue.Bool)?.value ?: kind.current
    Column {
        SectionLabel(option)
        SpettroCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SpettroSwitch(checked = current, onCheckedChange = onToggle)
            }
        }
        option.description?.takeIf { it.isNotEmpty() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.spacingMd, top = Dimens.spacingXs),
            )
        }
    }
}

// MARK: Previews

@Preview(name = "Config sheet dark", showBackground = true, backgroundColor = 0xFF2C2C2E, widthDp = 380, heightDp = 760)
@Composable
private fun ChatConfigSheetPreviewDark() {
    SpettroTheme(darkTheme = true) {
        ChatConfigSheetContent(
            options = ChatPreviewData.configOptions,
            pendingValues = null,
            onSetString = { _, _ -> },
            onSetBool = { _, _ -> },
        )
    }
}

@Preview(name = "Config sheet light", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 380, heightDp = 760)
@Composable
private fun ChatConfigSheetPreviewLight() {
    SpettroTheme(darkTheme = false) {
        ChatConfigSheetContent(
            options = ChatPreviewData.configOptions,
            pendingValues = mapOf("mode" to ConfigValue.Str("plan")),
            onSetString = { _, _ -> },
            onSetBool = { _, _ -> },
        )
    }
}
