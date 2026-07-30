package to.eyed.spettro.mobile.ui.screens.chat

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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpCommand
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoBody
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The input bar (port of MobileComposerView.swift): an attachment thumbnail
 * strip, a one-line config summary that opens the settings sheet, and the
 * input row — a plus menu (photos, slash commands), a pill text field, and a
 * send button that becomes Stop mid-turn.
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
    configSummary: String?,
    onConfigTap: () -> Unit,
    commands: List<AcpCommand>,
    onCommandPick: (AcpCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    var plusMenuOpen by remember { mutableStateOf(false) }
    var commandsOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceRaised)) {
        HairlineDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            if (attachments.isNotEmpty()) {
                AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
            }
            if (configSummary != null) {
                ConfigSummaryRow(
                    summary = configSummary,
                    enabled = enabled,
                    onTap = onConfigTap,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box {
                    IconButton(
                        onClick = { plusMenuOpen = true },
                        enabled = enabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddCircle,
                            contentDescription = "Attach",
                            tint = if (enabled) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = plusMenuOpen,
                        onDismissRequest = { plusMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photos") },
                            leadingIcon = { Icon(Icons.Outlined.Photo, contentDescription = null) },
                            onClick = {
                                plusMenuOpen = false
                                onAddImages()
                            },
                        )
                        if (commands.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Commands") },
                                leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
                                onClick = {
                                    plusMenuOpen = false
                                    commandsOpen = true
                                },
                            )
                        }
                    }
                }

                ComposerTextField(
                    text = text,
                    onTextChange = onTextChange,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )

                SendButton(
                    isBusy = isBusy,
                    canSend = enabled && (text.isNotBlank() || attachments.isNotEmpty()),
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            Box {
                AttachmentThumbnail(attachment = attachment, size = 64.dp)
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onPrimary,
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

/** "Coding · GPT-5 · YOLO" — one tappable line standing in for the chips. */
@Composable
private fun ConfigSummaryRow(
    summary: String,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val tint = if (enabled) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = Dimens.spacingLg, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "Chat settings",
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.UnfoldMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun ComposerTextField(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Dimens.radiusInputPill)
    val borderColor = if (focused) colors.accent.copy(alpha = 0.5f) else colors.hairline

    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        enabled = enabled,
        maxLines = 6,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
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
            Box(
                modifier = Modifier.padding(
                    horizontal = Dimens.spacingMd,
                    vertical = Dimens.spacingSm + 1.dp,
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = if (enabled) "Message Spettro…" else "Not connected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun SendButton(
    isBusy: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    if (isBusy) {
        // Mid-turn the useful action is stopping, not queueing another
        // message — so the same button becomes Stop.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.diffRemoved)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = "Stop",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (canSend) colors.accent else colors.accent.copy(alpha = 0.35f))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "Send",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// MARK: Command palette

/**
 * The slash-command picker: a searchable bottom sheet listing every command
 * the agent advertises. Filtering is a case-insensitive contains on the name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    commands: List<AcpCommand>,
    onPick: (AcpCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalSpettroColors.current.surfaceRaised,
    ) {
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
    val filtered = if (search.isBlank()) {
        commands
    } else {
        commands.filter { it.name.contains(search.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search field.
        val shape = RoundedCornerShape(Dimens.radiusInputPill)
        BasicTextField(
            value = search,
            onValueChange = { search = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg)
                .clip(shape)
                .background(colors.canvas)
                .border(Dimens.hairlineWidth, colors.hairline, shape),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.padding(
                        horizontal = Dimens.spacingMd,
                        vertical = Dimens.spacingSm,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (search.isEmpty()) {
                            Text(
                                text = "Find a command",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    }
                }
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (command.description.isNotEmpty()) {
                        Text(
                            text = command.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// MARK: Previews

@Preview(name = "Composer", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
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
        )
    }
}

@Preview(name = "Busy — stop", showBackground = true, backgroundColor = 0xFFF9F9F7, widthDp = 380)
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

@Preview(name = "Disconnected", showBackground = true, backgroundColor = 0xFF0E0E0E, widthDp = 380)
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

@Preview(name = "Command palette", showBackground = true, backgroundColor = 0xFF1C1C1C, widthDp = 380)
@Composable
private fun CommandPalettePreview() {
    SpettroTheme(darkTheme = true) {
        CommandPaletteContent(commands = ChatPreviewData.commands, onPick = {})
    }
}
