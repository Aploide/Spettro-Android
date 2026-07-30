package to.eyed.spettro.mobile.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoBody
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Protocol A entry: connect straight to `spettro --headless` over HTTP+SSE,
 * no macOS app involved. Raw strings go up unparsed — the caller runs them
 * through `HeadlessEndpoint.parse` / `HeadlessEndpoint.parseTokenPaste`.
 *
 * @param onConnect `(endpoint, tokenOrPaste)`: the host field as typed, and
 *   the token field which may be a bare token or a paste of the CLI's
 *   `SPETTRO_TOKEN=` / `SPETTRO_PORT=` lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CliConnectScreen(
    defaultHost: String,
    isConnecting: Boolean,
    errorText: String?,
    onConnect: (endpoint: String, tokenOrPaste: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    var host by rememberSaveable(defaultHost) { mutableStateOf(defaultHost) }
    var token by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        containerColor = colors.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Connect to the CLI", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.canvas,
                    scrolledContainerColor = colors.canvas,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        ) {
            SpettroCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Dimens.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                ) {
                    Text("Run this on your computer:", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "spettro --headless --bind 0.0.0.0",
                        style = MonoBody,
                        color = colors.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(Dimens.radiusSm),
                            )
                            .padding(Dimens.spacingSm),
                    )
                    Text(
                        "Then paste the two lines it prints below — or enter the host and token separately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                placeholder = { Text("192.168.1.10:7878", style = MonoBody) },
                textStyle = MonoBody,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.radiusMd),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.hairline,
                ),
            )

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token — or paste the CLI output") },
                placeholder = {
                    Text("SPETTRO_TOKEN=…\nSPETTRO_PORT=7878", style = MonoSmall)
                },
                textStyle = MonoSmall,
                minLines = 3,
                maxLines = 6,
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimens.radiusMd),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.hairline,
                ),
            )

            if (errorText != null) {
                ErrorCard(text = errorText, modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = { onConnect(host.trim(), token) },
                enabled = !isConnecting && host.isNotBlank() && token.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(Dimens.radiusLg),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isConnecting) {
                    SpettroSpinner(size = 16.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(Dimens.spacingSm))
                    Text("Connecting…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Connect", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// MARK: - Previews

@Preview(name = "CLI connect dark", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun CliConnectPreview() {
    SpettroTheme(darkTheme = true) {
        CliConnectScreen(
            defaultHost = "192.168.1.10:7878",
            isConnecting = false,
            errorText = null,
            onConnect = { _, _ -> },
            onBack = {},
        )
    }
}

@Preview(name = "CLI connect — error, light", showBackground = true, backgroundColor = 0xFFF9F9F7)
@Composable
private fun CliConnectErrorPreview() {
    SpettroTheme(darkTheme = false) {
        CliConnectScreen(
            defaultHost = "",
            isConnecting = false,
            errorText = "Couldn't reach 192.168.1.10:7878 — is the CLI still running?",
            onConnect = { _, _ -> },
            onBack = {},
        )
    }
}

@Preview(name = "CLI connecting", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun CliConnectingPreview() {
    SpettroTheme(darkTheme = true) {
        CliConnectScreen(
            defaultHost = "192.168.1.10:7878",
            isConnecting = true,
            errorText = null,
            onConnect = { _, _ -> },
            onBack = {},
        )
    }
}
