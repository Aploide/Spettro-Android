package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import to.eyed.spettro.mobile.core.remote.RemoteProject
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums

/**
 * Where should the new chat run? The phone can't browse the PC's
 * filesystem, so it picks from the folders the host already knows about.
 *
 * @param recentFirst true keeps the host's order (most recently active
 *   first); false re-sorts alphabetically by folder name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerSheet(
    projects: List<RemoteProject>,
    recentFirst: Boolean = true,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalSpettroColors.current.surfaceRaised,
    ) {
        ProjectPickerContent(
            projects = projects,
            recentFirst = recentFirst,
            onPick = onPick,
        )
    }
}

@Composable
private fun ProjectPickerContent(
    projects: List<RemoteProject>,
    recentFirst: Boolean,
    onPick: (String) -> Unit,
) {
    val ordered = if (recentFirst) {
        projects
    } else {
        projects.sortedBy { it.displayName.lowercase() }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.spacingXl)) {
        Text(
            "New Chat",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Dimens.spacingLg),
        )
        Text(
            "Pick the folder the chat should run in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                top = Dimens.spacingXs,
                bottom = Dimens.spacingMd,
            ),
        )
        HairlineDivider()
        if (ordered.isEmpty()) {
            Text(
                "No folders yet — open a project on your PC first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.spacingXl).align(Alignment.CenterHorizontally),
            )
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(ordered, key = { it.path }) { project ->
                    ProjectRow(project = project, onClick = { onPick(project.path) })
                }
            }
        }
    }
}

private val RemoteProject.displayName: String
    get() = name.ifBlank { path.trimEnd('/').substringAfterLast('/').ifEmpty { path } }

@Composable
private fun ProjectRow(project: RemoteProject, onClick: () -> Unit) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.accent,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                project.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                project.path,
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (project.chatCount > 0) {
            Box(
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    RoundedCornerShape(10.dp),
                ),
            ) {
                Text(
                    "${project.chatCount}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TabularNums),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// MARK: - Previews (the sheet content — ModalBottomSheet is a window and
// doesn't render inside the preview canvas)

internal fun previewProjects(): List<RemoteProject> = listOf(
    RemoteProject(path = "/Users/carlo/dev/spettro", name = "spettro", chatCount = 12),
    RemoteProject(path = "/Users/carlo/dev/SpettroAndroid", name = "SpettroAndroid", chatCount = 4),
    RemoteProject(path = "/Users/carlo/Documents/deep/nested/folder/experiments", name = "", chatCount = 0),
)

@Preview(name = "Project picker", showBackground = true, backgroundColor = 0xFF1C1C1C)
@Composable
private fun ProjectPickerPreview() {
    SpettroTheme(darkTheme = true) {
        ProjectPickerContent(projects = previewProjects(), recentFirst = true, onPick = {})
    }
}

@Preview(name = "Project picker — empty", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ProjectPickerEmptyPreview() {
    SpettroTheme(darkTheme = false) {
        ProjectPickerContent(projects = emptyList(), recentFirst = true, onPick = {})
    }
}
