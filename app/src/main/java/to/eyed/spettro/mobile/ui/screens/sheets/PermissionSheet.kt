package to.eyed.spettro.mobile.ui.screens.sheets

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
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
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val body = Prompts.permissionBody(request)
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
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(colors.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(request),
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = Prompts.permissionHeadline(request),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
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
                PermissionOptionButton(option = option, onClick = { onSelect(option.option.optionId) })
            }
        }
    }
}

@Composable
private fun PermissionOptionButton(
    option: Prompts.CardOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val label: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(option.label, style = MaterialTheme.typography.labelLarge)
            if (option.option.isRecommended) RecommendedTag()
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (option.role) {
            Prompts.OptionRole.ALLOW_ONCE -> Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) { label() }

            Prompts.OptionRole.ALLOW_ALWAYS -> OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)),
            ) { label() }

            else -> OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = BorderStroke(Dimens.hairlineWidth, MaterialTheme.colorScheme.outline),
            ) { label() }
        }
        option.caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

@Preview(name = "Execute dark", showBackground = true, backgroundColor = 0xFF0E0E0E)
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

@Preview(name = "Edit light", showBackground = true, backgroundColor = 0xFFF9F9F7)
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
