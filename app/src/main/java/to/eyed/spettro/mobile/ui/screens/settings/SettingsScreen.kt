package to.eyed.spettro.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpAccountStatus
import to.eyed.spettro.mobile.core.acp.AcpLoginStatus
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.components.PlanBadge
import to.eyed.spettro.mobile.ui.components.SectionHeader
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums
import kotlin.math.roundToInt

/**
 * Settings: the pairing itself, the Spettro Subscription account (device-flow
 * sign-in, plan, credits), the providers entry point, the connection log, and
 * about. Port of `MobileSettingsView.swift` (+ the device-flow states of
 * `SignInView.swift`, rendered inline in the subscription card).
 *
 * Stateless: every value arrives as a parameter, every action leaves as a
 * callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hostName: String?,
    hostKind: String?,
    connectionLabel: String,
    agentReady: Boolean,
    account: AcpAccountStatus?,
    login: AcpLoginStatus?,
    diagnostics: List<String>,
    appVersion: String,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            SectionHeader("Connection", modifier = Modifier.padding(start = Dimens.spacingXs))
            ConnectionCard(
                hostName = hostName,
                hostKind = hostKind,
                connectionLabel = connectionLabel,
                agentReady = agentReady,
                onDisconnect = onDisconnect,
                onForget = onForget,
            )

            Spacer(Modifier.size(Dimens.spacingSm))
            SectionHeader("Spettro Subscription", modifier = Modifier.padding(start = Dimens.spacingXs))
            SubscriptionCard(
                account = account,
                login = login,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onCancelLogin = onCancelLogin,
            )

            Spacer(Modifier.size(Dimens.spacingSm))
            SectionHeader("Models", modifier = Modifier.padding(start = Dimens.spacingXs))
            SpettroCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(onClick = onOpenProviders) {
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = null,
                        tint = LocalSpettroColors.current.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Providers & Models",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                "Provider keys are stored on your PC, never on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = Dimens.spacingXs),
            )

            if (diagnostics.isNotEmpty()) {
                Spacer(Modifier.size(Dimens.spacingSm))
                SectionHeader("Connection Log", modifier = Modifier.padding(start = Dimens.spacingXs))
                DiagnosticsBlock(diagnostics)
            }

            Spacer(Modifier.size(Dimens.spacingSm))
            SectionHeader("About", modifier = Modifier.padding(start = Dimens.spacingXs))
            SpettroCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Version", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        appVersion,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                "Spettro Remote protocol v1",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = Dimens.spacingXs, bottom = Dimens.spacingXl),
            )
        }
    }
}

/** A tappable full-width card row with the standard paddings. */
@Composable
private fun SettingsRow(
    onClick: () -> Unit,
    contentColor: Color? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = contentColor ?: MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// MARK: - Connection

@Composable
private fun ConnectionCard(
    hostName: String?,
    hostKind: String?,
    connectionLabel: String,
    agentReady: Boolean,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val connected = connectionLabel.equals("connected", ignoreCase = true)
    SpettroCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (hostKind?.lowercase()) {
                    "mac", "macbook", "laptop" -> Icons.Outlined.LaptopMac
                    else -> Icons.Outlined.Computer
                },
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = hostName ?: "Not paired",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusDot(
                color = if (connected) colors.diffAdded else colors.modeColor("yellow"),
                pulsing = !connected,
            )
            Text(
                text = connectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        HairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Agent", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (agentReady) {
                StatusDot(color = colors.diffAdded)
                Text(
                    "Agent running",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                SpettroSpinner(size = 12.dp)
                Text(
                    "Agent starting…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        HairlineDivider()
        SettingsRow(onClick = onDisconnect) {
            Text(
                "Disconnect",
                color = LocalSpettroColors.current.accent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HairlineDivider()
        SettingsRow(onClick = onForget) {
            Text(
                "Forget This PC",
                color = colors.diffRemoved,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// MARK: - Subscription

@Composable
private fun SubscriptionCard(
    account: AcpAccountStatus?,
    login: AcpLoginStatus?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    SpettroCard(modifier = Modifier.fillMaxWidth()) {
        when {
            account?.signedIn == true -> SignedInRows(account, onSignOut)

            login != null && login.state != AcpLoginStatus.State.IDLE &&
                login.state != AcpLoginStatus.State.CANCELLED ->
                LoginFlowRows(login, onSignIn, onCancelLogin)

            else -> SettingsRow(onClick = onSignIn) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Sign In",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SignedInRows(account: AcpAccountStatus, onSignOut: () -> Unit) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = account.email ?: "Signed in",
            style = TextStyle(fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PlanBadge(plan = account.effectivePlan.ifEmpty { null })
    }

    account.remainingFraction?.let { fraction ->
        // Never round a non-empty balance down to a flat 0%: "0% left" when
        // there is still credit is worse than being one point optimistic.
        val percent = if (fraction > 0) maxOf(1, (fraction * 100).roundToInt()) else 0
        val barColor = if (fraction < 0.10) colors.diffRemoved else colors.accent
        HairlineDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Credits", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "$percent% left",
                    color = barColor,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = TabularNums,
                    ),
                )
            }
            LinearProgressIndicator(
                progress = { fraction.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
                trackColor = LocalSpettroColors.current.hairline,
            )
        }
    }

    HairlineDivider()
    SettingsRow(onClick = onSignOut) {
        Text("Sign Out", color = colors.diffRemoved, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The device-flow sign-in, inline: the CLI runs the flow on the PC and
 * pushes state; this only mirrors it, shows the URL in copyable form for when
 * the browser doesn't open on its own, and lets the user cancel.
 */
@Composable
private fun LoginFlowRows(
    login: AcpLoginStatus,
    onSignIn: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        when (login.state) {
            AcpLoginStatus.State.ERROR, AcpLoginStatus.State.EXPIRED -> {
                Text(
                    text = if (login.state == AcpLoginStatus.State.EXPIRED) {
                        "That sign-in link expired."
                    } else {
                        "Sign-in failed."
                    },
                    color = colors.diffRemoved,
                    style = MaterialTheme.typography.bodyMedium,
                )
                login.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                    OutlinedButton(onClick = onSignIn) { Text("Try Again") }
                    TextButton(onClick = onCancelLogin) { Text("Cancel") }
                }
            }

            AcpLoginStatus.State.COMPLETE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = colors.diffAdded)
                    Text(
                        "Signed in — loading your plan…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> { // STARTING / PENDING / UNKNOWN
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpettroSpinner(size = 14.dp)
                    Text(
                        "Waiting for browser…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                login.browserUrl?.let { url ->
                    Text(
                        "Open this link to finish signing in:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = url,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy link",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Open Browser")
                        }
                        TextButton(onClick = onCancelLogin) { Text("Cancel") }
                    }
                } ?: TextButton(onClick = onCancelLogin) { Text("Cancel") }
            }
        }
    }
}

// MARK: - Diagnostics

@Composable
private fun DiagnosticsBlock(diagnostics: List<String>) {
    SpettroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (line in diagnostics) {
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp),
                )
            }
        }
    }
}

// MARK: - Previews

@Preview(name = "Signed in dark", showBackground = true, backgroundColor = 0xFF0E0E0E, heightDp = 900)
@Composable
private fun SettingsPreviewSignedIn() {
    SpettroTheme(darkTheme = true) {
        SettingsScreen(
            hostName = "Carlo's MacBook Pro",
            hostKind = "mac",
            connectionLabel = "Connected",
            agentReady = true,
            account = AcpAccountStatus(
                signedIn = true,
                email = "callolego@gmail.com",
                plan = "max",
                creditsUsed = 42.0,
                creditLimit = 100.0,
                remainingCredits = 58.0,
                modelCount = 12,
            ),
            login = null,
            diagnostics = listOf(
                "browsing for _spettro-remote._tcp",
                "found Carlo's MacBook Pro at 192.168.1.24:51820",
                "hello ok, agent ready",
            ),
            appVersion = "0.1.1",
            onDisconnect = {}, onForget = {}, onSignIn = {}, onSignOut = {},
            onCancelLogin = {}, onOpenProviders = {}, onBack = {},
        )
    }
}

@Preview(name = "Login pending light", showBackground = true, backgroundColor = 0xFFF9F9F7, heightDp = 900)
@Composable
private fun SettingsPreviewLoginPending() {
    SpettroTheme(darkTheme = false) {
        SettingsScreen(
            hostName = "Carlo's MacBook Pro",
            hostKind = "mac",
            connectionLabel = "Connecting",
            agentReady = false,
            account = AcpAccountStatus(signedIn = false),
            login = AcpLoginStatus(
                loginId = "l-1",
                rawStatus = "pending",
                browserUrl = "https://spettro.app/device?code=THX-1138",
            ),
            diagnostics = emptyList(),
            appVersion = "0.1.1",
            onDisconnect = {}, onForget = {}, onSignIn = {}, onSignOut = {},
            onCancelLogin = {}, onOpenProviders = {}, onBack = {},
        )
    }
}
