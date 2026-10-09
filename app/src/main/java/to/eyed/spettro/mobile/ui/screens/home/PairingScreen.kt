package to.eyed.spettro.mobile.ui.screens.home

import to.eyed.spettro.mobile.ui.theme.floatingShadow
import to.eyed.spettro.mobile.ui.theme.TabularNums
import to.eyed.spettro.mobile.ui.theme.Elevation
import to.eyed.spettro.mobile.ui.components.SpettroTextField
import to.eyed.spettro.mobile.ui.components.SpettroLink
import to.eyed.spettro.mobile.ui.components.SpettroDialog
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.RingSpinner
import to.eyed.spettro.mobile.ui.components.CardVariant
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.ButtonSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.components.AppIconImage
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The one-time setup screen: point the phone at the pairing code on the
 * computer. Laid out as the desktop's setup step — one centred column, a
 * 24sp title, a sentence of what this is, the steps, and one accent action —
 * with the camera handled by the in-app CameraX + ML Kit [QrScannerScreen].
 *
 * Stateless: the caller drives [isPairing] and [errorText]; scans and manual
 * pastes flow up through [onScanned] / [onManualEntry] as the raw string for
 * `RemotePairing.parse`.
 */
@Composable
fun PairingScreen(
    isPairing: Boolean,
    errorText: String?,
    onScanned: (String) -> Unit,
    onManualEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current

    var showScanner by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    // Dismissal is presentation-only, so it lives here; a *new* error text
    // resets it and shows again.
    var errorDismissed by remember(errorText) { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(colors.canvas)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            AppIconImage(size = 64.dp)
            Spacer(Modifier.height(Dimens.spacingXl))
            Text(
                "Pair with your computer",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dimens.spacingSm))
            Text(
                "Drive Spettro on your computer from this phone — the same chats, live.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )

            Spacer(Modifier.height(Dimens.spacingXl))
            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PairingStep(1, "Open Spettro on your computer.")
                PairingStep(2, "Go to Settings › Remote and turn on sharing.")
                PairingStep(3, "Choose Pair a device, then scan the code.")
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(Dimens.spacingXl))

            if (errorText != null && !errorDismissed) {
                ErrorCard(
                    text = errorText,
                    onDismiss = { errorDismissed = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.spacingLg),
                )
            }

            SpettroButton(
                text = "Scan the code",
                onClick = { showScanner = true },
                enabled = !isPairing,
                style = ButtonStyle.Prominent,
                size = ButtonSize.Large,
                icon = Icons.Outlined.QrCodeScanner,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.spacingXs))
            SpettroLink("Paste a pairing link instead", onClick = { showManualEntry = true }, enabled = !isPairing)

            Spacer(Modifier.height(Dimens.spacingSm))
            Text(
                "You only do this once. After that your phone reconnects on its own whenever your computer is " +
                    "sharing. Pair on the same Wi-Fi as your computer, unless it allows pairing over the internet.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
        }

        if (showScanner) {
            QrScannerScreen(
                onResult = { value ->
                    showScanner = false
                    onScanned(value)
                },
                onClose = { showScanner = false },
                modifier = Modifier.matchParentSize(),
            )
        }

        if (isPairing) {
            PairingOverlay(Modifier.matchParentSize())
        }
    }

    if (showManualEntry) {
        ManualEntryDialog(
            onConfirm = { text ->
                showManualEntry = false
                onManualEntry(text)
            },
            onDismiss = { showManualEntry = false },
        )
    }
}

@Composable
private fun PairingStep(number: Int, text: String) {
    val colors = LocalSpettroColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(colors.surfaceRaised, CircleShape)
                .border(Dimens.hairlineWidth, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                color = colors.textSecondary,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularNums),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The full-screen scrim shown while the HMAC handshake is in flight. */
@Composable
private fun PairingOverlay(modifier: Modifier = Modifier) {
    val colors = LocalSpettroColors.current
    Box(
        modifier = modifier
            .background(colors.scrim)
            // Swallow taps so nothing underneath is reachable mid-pair.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(Dimens.radiusMd)
        Surface(
            modifier = Modifier.floatingShadow(Elevation.Large, shape),
            shape = shape,
            color = colors.surfaceRaised,
            border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingLg),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingSpinner(size = 18.dp)
                Text("Pairing…", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun ManualEntryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    var text by rememberSaveable { mutableStateOf("") }
    SpettroDialog(onDismiss = onDismiss) {
        Text("Paste a pairing link", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "On your computer choose Copy link, then paste it here — or open the link on this phone and it comes straight to Spettro.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Dimens.spacingMd))
        SpettroTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "spettro-pair://pair?v=1&h=…",
            mono = true,
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            SpettroButton("Cancel", onClick = onDismiss)
            SpettroButton(
                "Pair",
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                style = ButtonStyle.Prominent,
            )
        }
    }
}

/**
 * A dismissible inline error, as the desktop's error card: a danger-tinted
 * ground and border, the icon in danger, the sentence in the primary ink.
 * Pass a null [onDismiss] to drop the close button.
 */
@Composable
internal fun ErrorCard(
    text: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    SpettroCard(modifier = modifier, variant = CardVariant.Error) {
        Row(
            modifier = Modifier.padding(
                start = Dimens.spacingMd,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingSm,
                end = Dimens.spacingXs,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(vertical = Dimens.spacingXs),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Previews

@Preview(name = "Pairing dark", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun PairingScreenPreview() {
    SpettroTheme(darkTheme = true) {
        PairingScreen(
            isPairing = false,
            errorText = null,
            onScanned = {},
            onManualEntry = {},
        )
    }
}

@Preview(name = "Pairing light + error", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun PairingScreenErrorPreview() {
    SpettroTheme(darkTheme = false) {
        PairingScreen(
            isPairing = false,
            errorText = "That isn't a Spettro pairing code.",
            onScanned = {},
            onManualEntry = {},
        )
    }
}

@Preview(name = "Pairing in flight", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun PairingScreenBusyPreview() {
    SpettroTheme(darkTheme = true) {
        PairingScreen(
            isPairing = true,
            errorText = null,
            onScanned = {},
            onManualEntry = {},
        )
    }
}
