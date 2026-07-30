package to.eyed.spettro.mobile.ui.screens.chat

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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
 * advertises — as a grouped bottom sheet (port of ChatConfigSheet.swift).
 *
 * Selecting calls straight back; the host applies the change asynchronously.
 * [pendingValues] lets the caller overlay optimistic values (choices made but
 * not yet confirmed by the agent) so the checkmark follows the tap.
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

@Composable
internal fun ChatConfigSheetContent(
    options: List<AcpConfigOption>,
    pendingValues: Map<String, ConfigValue>?,
    onSetString: (String, String) -> Unit,
    onSetBool: (String, Boolean) -> Unit,
) {
    val ordered = options.sortedBy(::categoryRank)
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
                is AcpConfigOption.Kind.Select -> SelectSection(
                    option = option,
                    kind = kind,
                    pending = pendingValues?.get(option.id),
                    onSelect = { onSetString(option.id, it) },
                )
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

@Composable
private fun SelectSection(
    option: AcpConfigOption,
    kind: AcpConfigOption.Kind.Select,
    pending: ConfigValue?,
    onSelect: (String) -> Unit,
) {
    val colors = LocalSpettroColors.current
    val current = (pending as? ConfigValue.Str)?.value ?: kind.current
    Column {
        SectionLabel(option)
        SpettroCard(modifier = Modifier.fillMaxWidth()) {
            kind.groups.forEachIndexed { groupIndex, group ->
                // Model lists arrive grouped by provider; without the label
                // two identically named models are indistinguishable.
                group.name?.takeIf { it.isNotEmpty() }?.let { name ->
                    Text(
                        text = name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(
                            start = Dimens.spacingMd,
                            end = Dimens.spacingMd,
                            top = if (groupIndex == 0) Dimens.spacingSm else Dimens.spacingMd,
                            bottom = Dimens.spacingXs,
                        ),
                    )
                }
                group.options.forEachIndexed { index, choice ->
                    if (index > 0 || (groupIndex > 0 && group.name == null)) {
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
        }
        option.description?.takeIf { it.isNotEmpty() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Dimens.spacingMd,
                    top = Dimens.spacingXs,
                ),
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
                modifier = Modifier.padding(
                    start = Dimens.spacingMd,
                    top = Dimens.spacingXs,
                ),
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
