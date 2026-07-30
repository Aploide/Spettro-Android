package to.eyed.spettro.mobile.ui.screens.home

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.remote.ChatSummary
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * Chats put away but not thrown out. Simple rows — tap to open, or
 * unarchive/delete via the trailing icon buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedChatsSheet(
    chats: List<ChatSummary>,
    onOpen: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalSpettroColors.current.surfaceRaised,
    ) {
        ArchivedChatsContent(
            chats = chats,
            onOpen = onOpen,
            onUnarchive = onUnarchive,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ArchivedChatsContent(
    chats: List<ChatSummary>,
    onOpen: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.spacingXl)) {
        Text(
            "Archived",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                bottom = Dimens.spacingMd,
            ),
        )
        HairlineDivider()
        if (chats.isEmpty()) {
            Text(
                "Nothing archived.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.spacingXl).align(Alignment.CenterHorizontally),
            )
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(chats, key = { it.id }) { chat ->
                    ArchivedRow(
                        chat = chat,
                        onOpen = { onOpen(chat.id) },
                        onUnarchive = { onUnarchive(chat.id) },
                        onDelete = { onDelete(chat.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedRow(
    chat: ChatSummary,
    onOpen: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(
                start = Dimens.spacingLg,
                end = Dimens.spacingSm,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingSm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            ) {
                Text(
                    chat.title.ifBlank { "Untitled chat" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    relativeTimeLabel(chat.updatedAt),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
                )
            }
            if (chat.preview.isNotBlank()) {
                Text(
                    chat.preview,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onUnarchive) {
            Icon(
                Icons.Outlined.Unarchive,
                contentDescription = "Unarchive",
                modifier = Modifier.size(18.dp),
                tint = colors.accent,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(18.dp),
                tint = colors.diffRemoved,
            )
        }
    }
}

// MARK: - Previews (content only — the sheet itself is a window)

@Preview(name = "Archived", showBackground = true, backgroundColor = 0xFF1C1C1C)
@Composable
private fun ArchivedChatsPreview() {
    SpettroTheme(darkTheme = true) {
        ArchivedChatsContent(
            chats = previewChats().map { it.copy(isArchived = true) },
            onOpen = {},
            onUnarchive = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Archived — empty", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ArchivedChatsEmptyPreview() {
    SpettroTheme(darkTheme = false) {
        ArchivedChatsContent(chats = emptyList(), onOpen = {}, onUnarchive = {}, onDelete = {})
    }
}
