package to.eyed.spettro.mobile.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.model.ImageAttachment
import to.eyed.spettro.mobile.ui.components.SpettroTopBar
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/** One way to start: the chip's [label], and the [prompt] it puts in the composer. */
data class StarterPrompt(val label: String, val prompt: String)

/** The desktop's starters (views/chat/StarterPrompts.tsx), for a folder with code in it. */
val STARTER_PROMPTS = listOf(
    StarterPrompt("Explain this project", "Explain this project in simple terms"),
    StarterPrompt("Fix a bug", "Help me find and fix a bug: "),
    StarterPrompt("Add a feature", "Help me add a new feature: "),
    StarterPrompt("Write tests", "Write tests for the most important code that has none"),
)

/** For a start with no project yet (an empty folder): the ones above assume there is code. */
val FRESH_START_PROMPTS = listOf(
    StarterPrompt("Build a simple website", "Build me a simple website for "),
    StarterPrompt("Make a small app", "Make a small app that "),
    StarterPrompt("Automate a chore", "Write a script that "),
    StarterPrompt("Explain a file", "Explain this file in simple terms: "),
)

private val HOME_FOLDER = Regex("^/(home|Users)/[^/]+/?$")
private val DRIVE_ROOT = Regex("^[A-Za-z]:[\\\\/]?$")

/**
 * Whether [path] is a broad folder — the disk's root, a drive, or the home
 * folder — where the desktop offers the fresh-start prompts: the usual ones
 * assume a project with code in it. (The host doesn't say where home is;
 * these are its usual shapes.)
 */
fun isBroadFolder(path: String?): Boolean {
    if (path.isNullOrEmpty()) return false
    return path == "/" || HOME_FOLDER.matches(path) || DRIVE_ROOT.matches(path)
}

/**
 * A few plain-language ways to start, where a chat has no messages yet. A
 * chip only fills the composer — the user still reads, edits and sends — so
 * it can't start work nobody meant to start (`.starter-prompt`: 32dp pills,
 * a hairline, secondary text).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StarterPrompts(
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
    fresh: Boolean = false,
) {
    val colors = LocalSpettroColors.current
    FlowRow(
        modifier = modifier.widthIn(max = 680.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        for (starter in if (fresh) FRESH_START_PROMPTS else STARTER_PROMPTS) {
            Surface(
                onClick = { onPrompt(starter.prompt) },
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = colors.textSecondary,
                border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
            ) {
                Text(
                    starter.label,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }
    }
}

/**
 * "Working in ▸ acme-web" (`.project-chip`): which folder the new chat will
 * work in, and the way to change it. 12sp, a tertiary caption and the name
 * in 500; no fill until pressed.
 */
@Composable
fun ProjectChip(
    folderName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Working in", style = TextStyle(fontSize = 12.sp), color = colors.textTertiary)
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(
            folderName ?: "Choose a folder",
            modifier = Modifier.widthIn(max = 220.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * The new-session view (the desktop's NewSessionView): a greeting, the
 * composer, and a "Working in" chip that says — and changes — which folder
 * the chat will work in. Starting is "type and send"; the folder is a detail
 * of that rather than a gate in front of it.
 *
 * No chat exists until the first message is sent: [onSend] creates it in
 * the chosen folder and hands it the message. Nothing is created by looking
 * at this screen or changing the folder. With no folder chosen yet, sending
 * asks for one first and keeps the draft.
 *
 * @param onBack null in the tablet split shell, where it is the empty pane.
 */
@Composable
fun NewSessionScreen(
    folderName: String?,
    onChooseFolder: () -> Unit,
    composerText: String,
    onComposerTextChange: (String) -> Unit,
    attachments: List<ImageAttachment>,
    onRemoveAttachment: (ImageAttachment) -> Unit,
    onAddImages: () -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    sending: Boolean = false,
    folderPath: String? = null,
    mentionFiles: (suspend () -> List<String>)? = null,
    onMention: (String) -> Unit = {},
) {
    val colors = LocalSpettroColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding(),
    ) {
        SpettroTopBar(title = "", onBack = onBack)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    // The desktop sits its column a little above centre (12vh).
                    .padding(start = Dimens.spacingSm, end = Dimens.spacingSm, bottom = minHeight * 0.12f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.CenterVertically),
            ) {
                Text(
                    "What should we build?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Dimens.spacingLg),
                )
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    ChatComposer(
                        text = composerText,
                        onTextChange = onComposerTextChange,
                        attachments = attachments,
                        onRemoveAttachment = onRemoveAttachment,
                        onAddImages = onAddImages,
                        onSend = onSend,
                        onStop = {},
                        isBusy = false,
                        enabled = enabled && !sending,
                        configSummary = null,
                        onConfigTap = {},
                        commands = emptyList(),
                        onCommandPick = {},
                        placeholder = NEW_SESSION_PLACEHOLDER,
                        mentionFiles = mentionFiles,
                        onMention = onMention,
                        mentionScope = folderPath,
                    )
                    ProjectChip(
                        folderName = folderName,
                        onClick = onChooseFolder,
                        modifier = Modifier.padding(start = Dimens.spacingSm),
                    )
                }
                StarterPrompts(
                    onPrompt = onComposerTextChange,
                    modifier = Modifier.padding(horizontal = Dimens.spacingSm),
                    fresh = isBroadFolder(folderPath),
                )
            }
        }
    }
}

// MARK: Previews

@Preview(name = "New session dark", showBackground = true, backgroundColor = 0xFF1C1C1E, heightDp = 720)
@Composable
private fun NewSessionPreviewDark() {
    SpettroTheme(darkTheme = true) {
        var text by remember { mutableStateOf("") }
        NewSessionScreen(
            folderName = "spettro",
            onChooseFolder = {},
            composerText = text,
            onComposerTextChange = { text = it },
            attachments = emptyList(),
            onRemoveAttachment = {},
            onAddImages = {},
            onSend = {},
            enabled = true,
            onBack = {},
        )
    }
}

@Preview(name = "New session light, no folder", showBackground = true, heightDp = 720)
@Composable
private fun NewSessionPreviewLight() {
    SpettroTheme(darkTheme = false) {
        NewSessionScreen(
            folderName = null,
            onChooseFolder = {},
            composerText = "",
            onComposerTextChange = {},
            attachments = emptyList(),
            onRemoveAttachment = {},
            onAddImages = {},
            onSend = {},
            enabled = true,
        )
    }
}
