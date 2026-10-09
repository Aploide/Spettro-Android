package to.eyed.spettro.mobile.ui.screens.sheets

import to.eyed.spettro.mobile.ui.components.SpettroSheet
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.ButtonSize
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.core.acp.AcpToolContent
import to.eyed.spettro.mobile.model.Prompts
import to.eyed.spettro.mobile.model.ToolCallItem
import to.eyed.spettro.mobile.ui.screens.chat.ToolDiffView
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The modal approval prompt shown when the agent asks the user to authorize
 * something mid-turn — most commonly a shell command, but the same sheet
 * covers file edits and every other permission kind, because the options come
 * straight from the ACP request. What it says — the headline, the command and
 * reason, the diff, the order of the buttons — is the desktop's (see
 * [Prompts]).
 *
 * Swiping the sheet away answers nothing: [onDismiss] is called and the
 * request stays pending — a swipe is not a choice, and the CLI would read
 * a cancelled outcome as a denial. Denying is a button of its own.
 *
 * Questions (`_meta["spettro.app/question"]`) do NOT belong here; route them
 * to [QuestionSheet].
 *
 * @param chatTitle which chat is asking, when the host told us; shown as a
 *   secondary line so a prompt arriving over another chat's screen is legible.
 * @param onSelect the chosen option id.
 * @param onDismiss the sheet was swiped (or backed) away, unanswered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(
    request: AcpPermissionRequest,
    chatTitle: String?,
    onSelect: (optionId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    SpettroSheet(onDismissRequest = onDismiss) {
        PermissionSheetContent(
            request = request,
            chatTitle = chatTitle,
            onSelect = { optionId ->
                onSelect(optionId)
                onDismiss()
            },
        )
    }
}

/** The headline's icon, by kind. */
private fun iconFor(request: AcpPermissionRequest): ImageVector = when {
    request.isCompact -> Icons.Outlined.Compress
    else -> when (request.toolKind) {
        "execute" -> Icons.Outlined.Terminal
        "edit" -> Icons.Outlined.Edit
        "delete" -> Icons.Outlined.Delete
        "move" -> Icons.Outlined.DriveFileMove
        "read" -> Icons.Outlined.Description
        "fetch" -> Icons.Outlined.Language
        "search" -> Icons.Outlined.Search
        "think" -> Icons.AutoMirrored.Outlined.HelpOutline
        else -> Icons.Outlined.Shield
    }
}

/** Fence languages that are a shell command, drawn with a `$ ` prompt. */
private val SHELL_LANGS = setOf("sh", "bash", "shell", "zsh", "console")

@Composable
internal fun PermissionSheetContent(
    request: AcpPermissionRequest,
    chatTitle: String?,
    onSelect: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val secondary = colors.textSecondary
    val body = Prompts.permissionBody(request)
    // The desktop's arm delay: for the first 600ms the answers are dimmed
    // and inert, so a tap meant for whatever was on screen a moment ago
    // cannot approve something the user hasn't read.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROMPT_ARM_DELAY_MS)
        armed = true
    }
    val diffPaths = request.diffs.map { it.path }.toSet()
    // Locations a diff already shows say nothing new.
    val locations = request.locations.filter { it.path !in diffPaths }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, bottom = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
    ) {
        // Header: what is being asked, in a sentence, beside an amber tile —
        // amber like the "Needs you" badge: something is waiting on you.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.Top,
        ) {
            PromptIconTile(icon = iconFor(request))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = Prompts.permissionHeadline(request),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                if (Prompts.showsTitle(request)) {
                    SelectionContainer {
                        Text(
                            text = request.title,
                            color = secondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (chatTitle != null) {
                    Text(
                        text = chatTitle,
                        color = secondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            if (request.isCompact) {
                Text(
                    text = Prompts.compactDetail(request),
                    color = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            for (block in body) {
                when (block) {
                    is Prompts.BodyBlock.Code -> MonoBlock(
                        text = if (block.lang in SHELL_LANGS) "$ ${block.text}" else block.text,
                        fontSize = 12.5.sp,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is Prompts.BodyBlock.Note -> Text(
                        text = block.text,
                        color = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (request.diffs.isNotEmpty()) {
                ToolDiffView(
                    diffs = request.diffs.map { ToolCallItem.ToolDiff(it.path, it.oldText, it.newText) },
                )
            }
            for (location in locations) {
                Text(
                    text = ToolCallItem.shortPath(location.path) + (location.line?.let { ":$it" } ?: ""),
                    style = MonoSmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // One full-width button per option, in the one order they always
        // take: Allow once, Always allow, Deny, then anything else. The
        // recommended option is tagged, never preselected.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            for (option in Prompts.orderedOptions(request)) {
                PermissionOptionButton(
                    option = option,
                    enabled = armed,
                    onClick = { onSelect(option.option.optionId) },
                )
            }
        }
    }
}

/**
 * One answer, as the desktop's prompt buttons: 8dp corners rather than a
 * pill, a hairline on the raised surface — and the main answer, Allow once,
 * in the ink rather than the accent (in-chat primaries are ink). The
 * "Always allow" scope sits under its button as a small caption.
 */
@Composable
private fun PermissionOptionButton(
    option: Prompts.CardOption,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpettroButton(
                text = option.label,
                onClick = onClick,
                // Inert while arming, but without the faded disabled look on
                // top of the arming dim.
                enabled = true,
                style = if (option.role == Prompts.OptionRole.ALLOW_ONCE) ButtonStyle.Ink else ButtonStyle.Neutral,
                size = ButtonSize.Large,
                shape = RoundedCornerShape(Dimens.radiusSm),
                modifier = Modifier
                    .weight(1f)
                    .then(if (enabled) Modifier else Modifier.consumeTaps()),
            )
        }
        if (option.option.isRecommended || option.caption != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (option.option.isRecommended) RecommendedTag()
                option.caption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// MARK: - Previews

private fun previewRequest(kind: String) = AcpPermissionRequest(
    sessionId = "s-1",
    title = if (kind == "execute") "Run a command" else "Edit AppModel.swift",
    toolKind = kind,
    texts = if (kind == "execute") {
        listOf(
            "```sh\nrm -rf build/ && swift build --configuration release 2>&1 | tee build.log\n```",
            "needs approval: rm -rf build/",
        )
    } else {
        emptyList()
    },
    diffs = if (kind == "edit") {
        listOf(AcpToolContent.Diff("/Users/dev/app/Sources/AppModel.swift", "let a = 1", "let a = 2"))
    } else {
        emptyList()
    },
    options = listOf(
        AcpPermissionRequest.Option("allow", "Allow", "allow_once"),
        AcpPermissionRequest.Option("always", "Always allow this command", "allow_always", isRecommended = true),
        AcpPermissionRequest.Option("reject", "Reject", "reject_once"),
    ),
)

@Preview(name = "Execute dark", showBackground = true, backgroundColor = 0xFF2C2C2E)
@Composable
private fun PermissionSheetPreviewDark() {
    SpettroTheme(darkTheme = true) {
        PermissionSheetContent(
            request = previewRequest("execute"),
            chatTitle = "Fix the flaky pairing test",
            onSelect = {},
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}

@Preview(name = "Edit light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PermissionSheetPreviewLight() {
    SpettroTheme(darkTheme = false) {
        PermissionSheetContent(
            request = previewRequest("edit"),
            chatTitle = null,
            onSelect = {},
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}
