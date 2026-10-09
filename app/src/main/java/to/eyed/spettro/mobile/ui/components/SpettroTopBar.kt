package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors

/**
 * A screen's header, the phone's version of the desktop's modal head: the
 * canvas colour (no tonal tint, no elevation), a back arrow, and a 17sp
 * semibold title. [actions] sit at the end.
 */
@Composable
fun SpettroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    background: Color = LocalSpettroColors.current.canvas,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

/** A 40dp icon button in the secondary ink, for top bars and toolbars. */
@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalSpettroColors.current.textSecondary,
    badge: Color? = null,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
            if (badge != null) {
                StatusDot(color = badge, size = 7.dp, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}
