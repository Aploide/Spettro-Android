package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Dimens
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.SpettroDialog
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors

/** The longest name a chat takes (the desktop's InlineRename maxLength). */
internal const val CHAT_TITLE_MAX = 120

/**
 * Renaming a chat: a field holding the current name, Save and Cancel — the
 * phone's version of the desktop's InlineRename. A blank name, or the same
 * one, saves nothing; the computer refuses blank titles too.
 */
@Composable
fun RenameChatDialog(
    title: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    // The current name, fully selected, so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(title, TextRange(0, title.length))) }
    val value = field.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val next = value.trim()
    val canSave = next.isNotEmpty() && next != title.trim()
    val save = {
        if (canSave) onRename(next) else onDismiss()
    }
    SpettroDialog(onDismiss = onDismiss) {
        Text("Rename chat", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        val shape = RoundedCornerShape(Dimens.radiusSm)
        BasicTextField(
            value = field,
            onValueChange = { field = if (it.text.length > CHAT_TITLE_MAX) it.copy(text = it.text.take(CHAT_TITLE_MAX)) else it },
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics { contentDescription = "Chat name" },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .background(colors.textSecondary.copy(alpha = 0.1f).compositeOver(colors.surfaceRaised), shape)
                        .border(Dimens.hairlineWidth, colors.accent.copy(alpha = 0.5f), shape)
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            SpettroButton("Cancel", onClick = onDismiss)
            SpettroButton("Save", onClick = save, style = ButtonStyle.Prominent, enabled = canSave)
        }
    }
}
