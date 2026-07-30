package to.eyed.spettro.mobile.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import to.eyed.spettro.mobile.ui.components.EyeGlyph
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The one-time setup screen: point the phone at the pairing QR code on the
 * Mac. Port of the iOS `PairingView` intro, with the camera handled by
 * zxing's [ScanContract] instead of an in-place AVCapture preview.
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
    onSwitchToCli: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current

    // The launcher needs an activity registry, which previews don't have.
    val scanLauncher = if (LocalInspectionMode.current) {
        null
    } else {
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let(onScanned)
        }
    }

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

            EyeGlyph(
                modifier = Modifier.size(width = 96.dp, height = 48.dp),
                tint = colors.accent,
            )
            Spacer(Modifier.height(Dimens.spacingLg))
            Text(
                "Spettro Remote",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Dimens.spacingSm))
            Text(
                "Drive the agent on your Mac from this phone — same chats, live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )

            Spacer(Modifier.height(Dimens.spacingXl))
            Column(
                modifier = Modifier.widthIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            ) {
                PairingStep(1, "Open Spettro on your Mac.")
                PairingStep(2, "Settings → Remote Access → turn on sharing.")
                PairingStep(3, "Scan the QR code.")
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

            Button(
                onClick = {
                    scanLauncher?.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Point at the QR code on your Mac")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        },
                    )
                },
                enabled = !isPairing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(Dimens.radiusLg),
            ) {
                Icon(
                    Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(Dimens.spacingSm))
                Text("Scan QR Code", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(onClick = { showManualEntry = true }, enabled = !isPairing) {
                Text("Enter code manually", color = colors.accent)
            }

            Spacer(Modifier.height(Dimens.spacingSm))
            Text(
                "You only do this once. After that the app reconnects on its own whenever your Mac is sharing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )

            // The escape hatch for people without the macOS app: Protocol A,
            // straight to `spettro --headless`. Deliberately quiet.
            TextButton(onClick = onSwitchToCli, enabled = !isPairing) {
                Text(
                    "Or connect directly to the CLI",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
        Box(
            modifier = Modifier.size(22.dp).background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(Color.Black.copy(alpha = 0.55f))
            // Swallow taps so nothing underneath is reachable mid-pair.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Dimens.radiusMd),
            color = colors.surfaceRaised,
            border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        ) {
            Row(
                modifier = Modifier.padding(Dimens.spacingXl),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpettroSpinner(size = 24.dp)
                Text("Pairing…", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ManualEntryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter pairing code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                Text(
                    "Paste the spettro-pair:// link from your Mac — or just the part after the “?”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("spettro-pair://pair?v=1&h=…", style = MonoSmall) },
                    textStyle = MonoSmall,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text("Pair") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * A dismissible inline error: diffRemoved-tinted card, shared by the pairing
 * and CLI-connect forms. Pass a null [onDismiss] to drop the close button.
 */
@Composable
internal fun ErrorCard(
    text: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.radiusMd),
        color = colors.diffRemoved.copy(alpha = 0.12f),
        contentColor = colors.diffRemoved,
    ) {
        Row(
            modifier = Modifier.padding(
                start = Dimens.spacingMd,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingSm,
                end = Dimens.spacingXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(vertical = Dimens.spacingXs),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Previews

@Preview(name = "Pairing dark", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun PairingScreenPreview() {
    SpettroTheme(darkTheme = true) {
        PairingScreen(
            isPairing = false,
            errorText = null,
            onScanned = {},
            onManualEntry = {},
            onSwitchToCli = {},
        )
    }
}

@Preview(name = "Pairing light + error", showBackground = true, backgroundColor = 0xFFF9F9F7)
@Composable
private fun PairingScreenErrorPreview() {
    SpettroTheme(darkTheme = false) {
        PairingScreen(
            isPairing = false,
            errorText = "That isn't a Spettro pairing code.",
            onScanned = {},
            onManualEntry = {},
            onSwitchToCli = {},
        )
    }
}

@Preview(name = "Pairing in flight", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun PairingScreenBusyPreview() {
    SpettroTheme(darkTheme = true) {
        PairingScreen(
            isPairing = true,
            errorText = null,
            onScanned = {},
            onManualEntry = {},
            onSwitchToCli = {},
        )
    }
}
