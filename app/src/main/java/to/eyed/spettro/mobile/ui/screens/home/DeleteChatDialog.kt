package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.runtime.Composable
import to.eyed.spettro.mobile.ui.components.ConfirmDialog

/**
 * "Delete this chat?" — the one question every way of deleting a chat asks
 * first (the desktop's shell/actions.ts `deleteChat`): the list's swipe
 * action, the archive's trash button and the chat's own menu. A chat still
 * working says that the work stops too. Confirming hands over to
 * MobileModel.deleteChat, which still offers Undo for a few seconds.
 */
@Composable
fun DeleteChatDialog(
    title: String,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = title.ifBlank { "Untitled chat" }
    ConfirmDialog(
        title = "Delete this chat?",
        message = if (isBusy) {
            "Spettro is still working in “$name”. Deleting it stops that work and removes the conversation from Spettro."
        } else {
            "“$name” will be removed from Spettro. Files it changed in your project stay as they are."
        },
        confirmLabel = "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
