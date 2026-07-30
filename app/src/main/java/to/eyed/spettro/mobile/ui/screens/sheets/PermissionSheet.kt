package to.eyed.spettro.mobile.ui.screens.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.eyed.spettro.mobile.core.acp.AcpPermissionRequest
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The modal approval prompt shown when the agent asks the user to authorize
 * something mid-turn — most commonly a shell command, but the same sheet
 * covers file edits and every other permission kind, because the options come
 * straight from the ACP request. Port of `PermissionSheet.swift` +
 * docs/30-permission-sheet.md, as a bottom sheet.
 *
 * Swiping the sheet away answers the request with a cancelled outcome —
 * [onSelect] receives null — so the agent is never left waiting on a prompt
 * nobody can see.
 *
 * Questions (`_meta["spettro.app/question"]`) do NOT belong here; route them
 * to [QuestionSheet].
 *
 * @param chatTitle which chat is asking, when the host told us; shown as a
 *   secondary line so a prompt arriving over another chat's screen is legible.
 * @param onSelect the chosen option id, or null for dismissed-without-choosing
 *   (a cancelled outcome, distinct from picking a reject option).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(
    request: AcpPermissionRequest,
    chatTitle: String?,
    onSelect: (optionId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            onSelect(null)
            onDismiss()
        },
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

/** Presentation order: allows first, rejects last, unknown kinds trailing. */
private fun kindRank(kind: String): Int = when (kind) {
    "allow_once" -> 0
    "allow_always" -> 1
    "reject_once" -> 2
    "reject_always" -> 3
    else -> 4
}

private fun iconFor(toolKind: String?): ImageVector = when (toolKind) {
    "execute" -> Icons.Outlined.Terminal
    "think" -> Icons.AutoMirrored.Outlined.HelpOutline
    else -> Icons.Outlined.Shield
}

/** The command to show, when the tool's raw input carries one. */
private fun commandText(request: AcpPermissionRequest): String? {
    val obj = request.rawInput as? kotlinx.serialization.json.JsonObject ?: return null
    val command = obj["command"] as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return if (command.isString) command.content else null
}

@Composable
internal fun PermissionSheetContent(
    request: AcpPermissionRequest,
    chatTitle: String?,
    onSelect: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, bottom = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
    ) {
        // Header: the tool kind's icon and the tool call's own title.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = iconFor(request.toolKind),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Spettro needs your approval",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                SelectionContainer {
                    Text(
                        text = request.title,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
                    )
                }
                if (chatTitle != null) {
                    Text(
                        text = chatTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        // The command block, only when the raw input carries a command string.
        commandText(request)?.let { command ->
            MonoBlock(
                text = command,
                fontSize = 12.sp,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // One full-width button per option, allows before rejects. The
        // recommended option is tagged, never preselected.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            for (option in request.options.sortedBy { kindRank(it.kind) }) {
                PermissionOptionButton(option = option, onClick = { onSelect(option.optionId) })
            }
        }
    }
}

@Composable
private fun PermissionOptionButton(
    option: AcpPermissionRequest.Option,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val label: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(option.name, style = MaterialTheme.typography.labelLarge)
            if (option.isRecommended) RecommendedTag()
        }
    }
    when (option.kind) {
        "allow_once" -> Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) { label() }

        "allow_always" -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)),
        ) { label() }

        else -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            border = BorderStroke(Dimens.hairlineWidth, MaterialTheme.colorScheme.outline),
        ) { label() }
    }
}

// MARK: - Previews

private fun previewRequest(kind: String) = AcpPermissionRequest(
    sessionId = "s-1",
    title = if (kind == "execute") "Run a shell command" else "Edit AppModel.swift",
    toolKind = kind,
    rawInput = if (kind == "execute") {
        buildJsonObject { put("command", "rm -rf build/ && swift build --configuration release 2>&1 | tee build.log") }
    } else {
        null
    },
    options = listOf(
        AcpPermissionRequest.Option("allow", "Allow", "allow_once"),
        AcpPermissionRequest.Option("always", "Always Allow", "allow_always", isRecommended = true),
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
