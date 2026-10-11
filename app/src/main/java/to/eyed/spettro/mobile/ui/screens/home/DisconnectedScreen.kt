package to.eyed.spettro.mobile.ui.screens.home

import to.eyed.spettro.mobile.ui.components.SpettroLink
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.SectionHeader
import to.eyed.spettro.mobile.ui.components.RingSpinner
import to.eyed.spettro.mobile.ui.components.ConfirmDialog
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.ButtonSize
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
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
import to.eyed.spettro.mobile.core.remote.OfflineReason
import to.eyed.spettro.mobile.core.remote.RemoteState
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Why the app can't reach the computer right now — each case is a different
 * thing to tell the user, mapped from `core.remote`'s state by [from]:
 * Searching (still looking / connecting), HostAsleep (not on this network),
 * Unreachable (not here, and its internet tunnel didn't answer either),
 * SharingOff (host said it stopped), NoLocalNetwork (Android withholds the
 * local network), TurnedAway (the host refused the handshake for now),
 * VersionMismatch (the two can't talk), Revoked (the host no longer knows
 * this phone), Transport (the socket dropped for an unnamed reason), and
 * Disconnected (the user dropped it, and nothing retries).
 *
 * Only Revoked invites pairing again: the others leave a working pairing
 * alone, and forgetting it over a passing refusal would throw it away.
 */
enum class DisconnectReason {
    Searching,
    HostAsleep,
    Unreachable,
    SharingOff,
    NoLocalNetwork,
    TurnedAway,
    VersionMismatch,
    Revoked,
    Transport,
    Disconnected,
    ;

    companion object {
        /** The reason to show for a non-connected [state]. */
        fun from(state: RemoteState): DisconnectReason = when (state) {
            is RemoteState.Offline -> when (state.reason) {
                OfflineReason.HostNotFound -> HostAsleep
                OfflineReason.TunnelUnreachable -> Unreachable
                OfflineReason.Rejected -> TurnedAway
                OfflineReason.VersionMismatch -> VersionMismatch
                OfflineReason.NotRecognised -> Revoked
                OfflineReason.HostStopped -> SharingOff
                OfflineReason.NoLocalNetwork -> NoLocalNetwork
                OfflineReason.Transport -> Transport
            }
            RemoteState.Disconnected -> Disconnected
            else -> Searching
        }
    }
}

/**
 * The resting screen whenever the computer isn't reachable — deliberately
 * calm, because the fix is usually on the other device. Port of the iOS
 * `DisconnectedView`, minus the router that decides when to show it.
 *
 * [hasRemoteHost] says the computer has an internet tunnel, so "be on the
 * same Wi-Fi" is no longer the advice.
 */
@Composable
fun DisconnectedScreen(
    reason: DisconnectReason,
    hostName: String?,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
    hasRemoteHost: Boolean = false,
    /**
     * Asks for the local-network permission: the main action for
     * [DisconnectReason.NoLocalNetwork], and a second one when the tunnel
     * didn't answer while [localNetworkMissing] kept the LAN out of reach.
     */
    onAllowLocalNetwork: (() -> Unit)? = null,
    localNetworkMissing: Boolean = false,
    /** The host has turned down more than one handshake in a row. */
    refusedRepeatedly: Boolean = false,
) {
    val colors = LocalSpettroColors.current
    val host = hostName ?: "your computer"
    val isWorking = reason == DisconnectReason.Searching
    var confirmingForget by rememberSaveable { mutableStateOf(false) }

    val icon: ImageVector = when (reason) {
        DisconnectReason.Searching -> Icons.Outlined.Wifi
        DisconnectReason.HostAsleep, DisconnectReason.Transport -> Icons.Outlined.Computer
        DisconnectReason.Unreachable -> Icons.Outlined.CloudOff
        DisconnectReason.SharingOff -> Icons.Outlined.Block
        DisconnectReason.NoLocalNetwork -> Icons.Outlined.WarningAmber
        DisconnectReason.TurnedAway -> Icons.Outlined.WarningAmber
        DisconnectReason.VersionMismatch -> Icons.Outlined.SystemUpdate
        DisconnectReason.Revoked -> Icons.Outlined.PersonRemove
        DisconnectReason.Disconnected -> Icons.Outlined.LinkOff
    }
    val title = when (reason) {
        DisconnectReason.Searching -> "Looking for $host…"
        DisconnectReason.HostAsleep, DisconnectReason.Unreachable -> "Can’t reach $host"
        DisconnectReason.Transport -> "Lost the connection to $host"
        DisconnectReason.SharingOff -> "Sharing is off"
        DisconnectReason.NoLocalNetwork -> "Allow local network access"
        DisconnectReason.TurnedAway -> "$host turned the connection down"
        DisconnectReason.VersionMismatch -> "Update Spettro"
        DisconnectReason.Revoked -> "This phone was removed"
        DisconnectReason.Disconnected -> "Disconnected from $host"
    }
    val detail = when (reason) {
        DisconnectReason.Searching ->
            if (hasRemoteHost) "Make sure it’s awake and sharing is on."
            else "Make sure it’s awake and on the same Wi-Fi as this phone."
        DisconnectReason.HostAsleep ->
            "It isn’t on this network right now. This screen updates by itself the moment it’s back."
        DisconnectReason.Unreachable ->
            "It isn’t on this network, and it didn’t answer over the internet either. This screen updates by itself the moment it’s back."
        DisconnectReason.Transport ->
            "Spettro keeps trying, and this screen updates by itself the moment it’s back."
        DisconnectReason.SharingOff ->
            "Turn it back on in Settings › Remote on your computer. This screen updates by itself once it’s on."
        // Only shown with no tunnel to try: one that was tried and failed
        // reads as Unreachable, with the permission offered alongside.
        DisconnectReason.NoLocalNetwork ->
            "Spettro talks to $host directly over your Wi-Fi, and Android asks before an app can reach devices on your local network."
        // One refusal is usually passing. A run of them may well be a
        // pairing that no longer matches, so stop vouching for it — but
        // leave the forgetting to the user.
        DisconnectReason.TurnedAway ->
            if (refusedRepeatedly) {
                "It answered, but keeps turning this phone away. Spettro tries again every so often; if this keeps happening, forget this computer and pair again."
            } else {
                "It answered, but didn’t let this phone in just now. Spettro keeps trying."
            }
        DisconnectReason.VersionMismatch ->
            "This phone and $host run versions of Spettro that can’t talk to each other. Update whichever is older, then try again."
        DisconnectReason.Revoked ->
            "$host no longer recognises this phone. Pair again to reconnect."
        DisconnectReason.Disconnected ->
            "You disconnected this phone. The pairing is kept, so it reconnects whenever you’re ready."
    }

    Column(
        modifier = modifier.fillMaxSize().background(colors.canvas).padding(Dimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // A quiet tile, not an accent disc: the desktop keeps the accent for
        // the one action that resolves things.
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.surfaceRaised, CircleShape)
                .border(Dimens.hairlineWidth, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.textSecondary,
            )
        }

        Spacer(Modifier.height(Dimens.spacingXl))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.spacingSm))
        Text(
            detail,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )

        Spacer(Modifier.height(Dimens.spacingXl))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            when {
                isWorking -> RingSpinner(size = 20.dp)
                reason == DisconnectReason.Revoked ->
                    // Retrying is pointless here — the computer answered and
                    // said no. Pairing is the only way forward.
                    PrimaryActionButton(
                        label = "Pair again",
                        icon = Icons.Outlined.QrCodeScanner,
                        onClick = onPairAgain,
                    )
                reason == DisconnectReason.NoLocalNetwork && onAllowLocalNetwork != null ->
                    PrimaryActionButton(
                        label = "Allow access",
                        icon = Icons.Outlined.Wifi,
                        onClick = onAllowLocalNetwork,
                    )
                else ->
                    PrimaryActionButton(
                        label = if (reason == DisconnectReason.Disconnected) "Reconnect" else "Try again",
                        icon = Icons.Outlined.Refresh,
                        onClick = onRetry,
                    )
            }
            // Over the internet it didn't answer, and the LAN was never
            // tried: on the same Wi-Fi as the computer, this is the way in.
            if (reason == DisconnectReason.Unreachable && localNetworkMissing && onAllowLocalNetwork != null) {
                SpettroLink("On the same Wi-Fi? Allow local network access", onClick = onAllowLocalNetwork)
            }
            // Settings is only reachable once connected, so this screen is
            // the sole way out of a stale pairing — offer it in every state,
            // even while retries run in the background.
            SpettroLink("Forget this computer…", onClick = { confirmingForget = true }, color = colors.danger)
        }

        Spacer(Modifier.weight(1f))

        // The instruction that actually resolves these states lives on the
        // other device, so say it here.
        if (reason == DisconnectReason.HostAsleep ||
            reason == DisconnectReason.Unreachable ||
            reason == DisconnectReason.SharingOff ||
            reason == DisconnectReason.Transport
        ) {
            SpettroCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Dimens.spacingMd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                ) {
                    SectionHeader("On your computer")
                    Text(
                        "Open Spettro, then go to Settings › Remote and turn on sharing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spacingSm))
        }
    }

    if (confirmingForget) {
        ConfirmDialog(
            title = "Forget “$host”?",
            message = "You’ll scan a new pairing code to connect again.",
            confirmLabel = "Forget",
            onConfirm = {
                confirmingForget = false
                onForget()
            },
            onDismiss = { confirmingForget = false },
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SpettroButton(
        text = label,
        onClick = onClick,
        style = ButtonStyle.Prominent,
        size = ButtonSize.Large,
        icon = icon,
        modifier = Modifier.widthIn(min = 200.dp),
    )
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

@Preview(name = "Searching", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun SearchingPreview() = DisconnectedPreview(DisconnectReason.Searching)

@Preview(name = "Host asleep", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun HostAsleepPreview() = DisconnectedPreview(DisconnectReason.HostAsleep)

@Preview(name = "Sharing off — light", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun SharingOffPreview() = DisconnectedPreview(DisconnectReason.SharingOff, darkTheme = false)

@Preview(name = "No local network", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun NoLocalNetworkPreview() = DisconnectedPreview(DisconnectReason.NoLocalNetwork)

@Preview(name = "Revoked", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun RevokedPreview() = DisconnectedPreview(DisconnectReason.Revoked)

@Preview(name = "Unreachable over the internet", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun UnreachablePreview() = DisconnectedPreview(DisconnectReason.Unreachable)

@Preview(name = "Turned away", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun TurnedAwayPreview() = DisconnectedPreview(DisconnectReason.TurnedAway)

@Preview(name = "Disconnected", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun DisconnectedByUserPreview() = DisconnectedPreview(DisconnectReason.Disconnected)

@Preview(name = "Version mismatch", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun VersionMismatchPreview() = DisconnectedPreview(DisconnectReason.VersionMismatch)
