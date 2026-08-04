package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Why the app can't reach the PC right now — each case is a different thing
 * to tell the user, mapped from `core.remote`'s state by the router:
 * Searching (still looking / connecting), HostAsleep (found nothing at the
 * address), SharingOff (host said it stopped), NoLocalNetwork (discovery is
 * blocked), Revoked (host no longer recognises this device), and Transport
 * (the socket dropped for an unnamed reason).
 */
enum class DisconnectReason { Searching, HostAsleep, SharingOff, NoLocalNetwork, Revoked, Transport }

/**
 * The resting screen whenever the PC isn't reachable — deliberately calm,
 * because the fix is usually on the other device. Port of the iOS
 * `DisconnectedView`, minus the router that decides when to show it.
 */
@Composable
fun DisconnectedScreen(
    reason: DisconnectReason,
    hostName: String?,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    val host = hostName ?: "your PC"
    val isWorking = reason == DisconnectReason.Searching
    var confirmingForget by rememberSaveable { mutableStateOf(false) }

    val icon: ImageVector = when (reason) {
        DisconnectReason.Searching -> Icons.Outlined.Wifi
        DisconnectReason.HostAsleep, DisconnectReason.Transport -> Icons.Outlined.Computer
        DisconnectReason.SharingOff -> Icons.Outlined.Block
        DisconnectReason.NoLocalNetwork -> Icons.Outlined.WarningAmber
        DisconnectReason.Revoked -> Icons.Outlined.PersonRemove
    }
    val title = when (reason) {
        DisconnectReason.Searching -> "Looking for $host…"
        DisconnectReason.HostAsleep, DisconnectReason.Transport -> "Can't reach $host"
        DisconnectReason.SharingOff -> "Sharing was turned off"
        DisconnectReason.NoLocalNetwork -> "Can't see the local network"
        DisconnectReason.Revoked -> "This device was removed"
    }
    val detail = when (reason) {
        DisconnectReason.Searching ->
            "Make sure your PC is awake and on the same network."
        DisconnectReason.HostAsleep, DisconnectReason.Transport ->
            "It isn't reachable right now. This screen updates by itself the moment it comes back."
        DisconnectReason.SharingOff ->
            "Turn it back on in Spettro on your PC."
        DisconnectReason.NoLocalNetwork ->
            "Spettro needs to see devices on your Wi-Fi to find $host. Check that Wi-Fi is on and that this app is allowed to use the local network in system settings."
        DisconnectReason.Revoked ->
            "$host no longer recognises this phone. Pair again to reconnect."
    }

    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).padding(Dimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isWorking) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Dimens.spacingXl))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.spacingSm))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )

        Spacer(Modifier.height(Dimens.spacingXl))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            when {
                isWorking -> SpettroSpinner(size = 20.dp)
                reason == DisconnectReason.Revoked ->
                    // Retrying is pointless here — the PC answered and said
                    // no. Pairing is the only way forward.
                    PrimaryActionButton(
                        label = "Pair Again",
                        icon = Icons.Outlined.QrCodeScanner,
                        onClick = onPairAgain,
                    )
                else ->
                    PrimaryActionButton(
                        label = "Try Again",
                        icon = Icons.Outlined.Refresh,
                        onClick = onRetry,
                    )
            }
            // Settings is only reachable once connected, so this screen is
            // the sole way out of a stale pairing — offer it in every state,
            // even while retries run in the background.
            TextButton(
                onClick = { confirmingForget = true },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.diffRemoved),
            ) {
                Text("Forget This PC")
            }
        }

        Spacer(Modifier.weight(1f))

        // The instruction that actually resolves these states lives on the
        // other device, so say it here.
        if (reason == DisconnectReason.HostAsleep ||
            reason == DisconnectReason.SharingOff ||
            reason == DisconnectReason.Transport
        ) {
            Surface(
                shape = RoundedCornerShape(Dimens.radiusMd),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.spacingMd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                ) {
                    Text(
                        "On your PC",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Open Spettro and turn on Remote Access, or run /remote in the terminal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spacingSm))
        }
    }

    if (confirmingForget) {
        AlertDialog(
            onDismissRequest = { confirmingForget = false },
            title = { Text("Forget $host?") },
            text = { Text("You'll scan a new pairing code to connect again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingForget = false
                        onForget()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.diffRemoved),
                ) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(Dimens.radiusLg),
        modifier = Modifier.widthIn(min = 180.dp).height(48.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(Dimens.spacingSm))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Previews

@Composable
private fun DisconnectedPreview(reason: DisconnectReason, darkTheme: Boolean = true) {
    SpettroTheme(darkTheme = darkTheme) {
        DisconnectedScreen(
            reason = reason,
            hostName = "Carlo's MacBook Pro",
            onRetry = {},
            onPairAgain = {},
            onForget = {},
        )
    }
}

@Preview(name = "Searching", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun SearchingPreview() = DisconnectedPreview(DisconnectReason.Searching)

@Preview(name = "Host asleep", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun HostAsleepPreview() = DisconnectedPreview(DisconnectReason.HostAsleep)

@Preview(name = "Sharing off — light", showBackground = true, backgroundColor = 0xFFF9F9F7)
@Composable
private fun SharingOffPreview() = DisconnectedPreview(DisconnectReason.SharingOff, darkTheme = false)

@Preview(name = "No local network", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun NoLocalNetworkPreview() = DisconnectedPreview(DisconnectReason.NoLocalNetwork)

@Preview(name = "Revoked", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun RevokedPreview() = DisconnectedPreview(DisconnectReason.Revoked)
