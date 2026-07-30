package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.core.acp.AcpConfigOption
import to.eyed.spettro.mobile.model.ConfigValue
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.components.SectionHeader
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Mode, model, permission, thinking level, and any boolean toggles the agent
 * advertises — as a grouped bottom sheet.
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalSpettroColors.current.canvas,
    ) {
        ChatConfigSheetContent(
            options = options,
            pendingValues = pendingValues,
            onSetString = onSetString,
            onSetBool = onSetBool,
        )
    }
}

/** Category display order: mode, model, permission, thinking, ultra, rest. */
private fun categoryRank(option: AcpConfigOption): Int =
    when (option.category ?: option.id) {
        "mode" -> 0
        "model" -> 1
        "permission" -> 2
        "thinking", "thought_level" -> 3
        "ultra" -> 4
        else -> 5
    }

private fun categoryIcon(option: AcpConfigOption): ImageVector =
    when (option.category ?: option.id) {
        "mode" -> Icons.Outlined.Tune
        "model" -> Icons.Outlined.Memory
        "permission" -> Icons.Outlined.Shield
        "thinking", "thought_level" -> Icons.Outlined.Psychology
        "ultra" -> Icons.Outlined.Bolt
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
    val ordered = options.sortedBy(::categoryRank)
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
    Column {
        SectionLabel(option)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice.value == current,
                    onClick = { onSelect(choice.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.accent.copy(alpha = 0.16f),
                        activeContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = colors.accent,
                        inactiveContainerColor = colors.surfaceRaised,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = colors.hairline,
                    ),
                    icon = {},
                ) {
                    Text(
                        text = choice.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
        // Segments have no room for descriptions; explain the active choice below.
        val active = choices.firstOrNull { it.value == current }
        (active?.description ?: option.description)?.takeIf { it.isNotEmpty() }?.let { text ->
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
                    text = labelFor(kind, current) ?: current ?: "Choose…",
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingXl,
            ),
        ) {
            kind.groups.forEachIndexed { groupIndex, group ->
                group.name?.takeIf { it.isNotEmpty() }?.let { name ->
                    item(key = "group-$groupIndex") {
                        Text(
                            text = name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(
                                start = Dimens.spacingXs,
                                top = if (groupIndex == 0) 0.dp else Dimens.spacingLg,
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

/** Middling flat lists (e.g. thinking levels) stay as check rows in a card. */
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
                Switch(
                    checked = current,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
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

// MARK: Previews

@Preview(name = "Config sheet dark", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380, heightDp = 760)
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

@Preview(name = "Config sheet light", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 380, heightDp = 760)
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
