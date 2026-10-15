package to.eyed.spettro.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import to.eyed.spettro.mobile.core.remote.RemoteLink
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpAccountStatus
import to.eyed.spettro.mobile.core.acp.AcpLoginStatus
import to.eyed.spettro.mobile.ui.components.AccentSwatch
import to.eyed.spettro.mobile.ui.components.ButtonSize
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.ConfirmDialog
import to.eyed.spettro.mobile.ui.components.FormDivider
import to.eyed.spettro.mobile.ui.components.FormRow
import to.eyed.spettro.mobile.ui.components.FormSection
import to.eyed.spettro.mobile.ui.components.PlanBadge
import to.eyed.spettro.mobile.ui.components.RowLabel
import to.eyed.spettro.mobile.ui.components.SegmentedControl
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.SpettroLink
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.components.SpettroTopBar
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.theme.Accent
import to.eyed.spettro.mobile.ui.theme.Appearance
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums
import kotlin.math.roundToInt

/** Space between grouped sections, as the desktop's `.form-section + .form-section`. */
private val SectionGap = 22.dp

/**
 * What the connection row says about a live connection: the way it actually
 * reaches the computer, in the desktop's words ("Connected over Wi-Fi",
 * "Connected on your network").
 */
internal fun connectionLabel(link: RemoteLink): String = when (link) {
    RemoteLink.WiFi -> "Connected over Wi-Fi"
    RemoteLink.Ethernet -> "Connected over Ethernet"
    RemoteLink.Usb -> "Connected over USB"
    RemoteLink.LocalNetwork -> "Connected on your network"
}

/**
 * Settings, laid out as the desktop's grouped form: the computer this phone
 * is paired with, the Spettro account (device-flow sign-in, plan, credits),
 * the way to models and providers, the phone's own appearance, the
 * connection log, and about. Section titles are sentence case — the eyebrow
 * uppercases them.
 *
 * Stateless: every value arrives as a parameter, every action leaves as a
 * callback.
 */
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
    /** Whether [connectionLabel] describes a live connection (green) or a pending one. */
    isConnected: Boolean = true,
    appearance: Appearance = Appearance.System,
    accent: Accent = Accent.Lilac,
    onAppearanceChange: (Appearance) -> Unit = {},
    onAccentChange: (Accent) -> Unit = {},
    /** Forget also takes this phone off the computer's paired devices (`devices/unpair`). */
    forgetRemovesFromComputer: Boolean = false,
) {
    val colors = LocalSpettroColors.current
    var confirmForget by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(colors.canvas)) {
        SpettroTopBar(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            ConnectionSection(
                hostName = hostName,
                hostKind = hostKind,
                connectionLabel = connectionLabel,
                isConnected = isConnected,
                agentReady = agentReady,
                onDisconnect = onDisconnect,
                onForget = { confirmForget = true },
            )

            AccountSection(
                account = account,
                login = login,
                onSignIn = onSignIn,
                onSignOut = { confirmSignOut = true },
                onCancelLogin = onCancelLogin,
            )

            FormSection(
                title = "Models & providers",
                footer = "Provider keys are stored on your computer, never on this phone.",
            ) {
                FormRow("Manage providers…", icon = Icons.Outlined.Key, onClick = onOpenProviders)
            }

            AppearanceSection(
                appearance = appearance,
                accent = accent,
                onAppearanceChange = onAppearanceChange,
                onAccentChange = onAccentChange,
            )

            if (diagnostics.isNotEmpty()) {
                FormSection(title = "Connection log") {
                    DiagnosticsBlock(diagnostics)
                }
            }

            FormSection(
                title = "About",
                footer = "Spettro Remote protocol v1",
                modifier = Modifier.padding(bottom = Dimens.spacingXl),
            ) {
                FormRow("Version", value = appVersion)
            }
        }
    }

    if (confirmForget) {
        ConfirmDialog(
            title = "Forget “${hostName ?: "this computer"}”?",
            message = if (forgetRemovesFromComputer) {
                "This phone won’t connect to it again until you pair it again. The computer also removes it from its paired devices."
            } else {
                "This phone won’t connect to it again until you pair it again."
            },
            confirmLabel = "Forget",
            onConfirm = {
                confirmForget = false
                onForget()
            },
            onDismiss = { confirmForget = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out of Spettro?",
            message = "Sessions using Spettro’s own models will need another model.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

// MARK: - Connection

/** What the Agent row says. */
internal enum class AgentRow { Running, Starting, Unknown }

/**
 * The Agent row's state. While the link is down ([isConnected] false,
 * "Reconnecting…") the agent flag is the one from before the drop, so the
 * row says nothing rather than a green "Running" it can't vouch for.
 */
internal fun agentRow(isConnected: Boolean, agentReady: Boolean): AgentRow = when {
    !isConnected -> AgentRow.Unknown
    agentReady -> AgentRow.Running
    else -> AgentRow.Starting
}

@Composable
private fun ConnectionSection(
    hostName: String?,
    hostKind: String?,
    connectionLabel: String,
    isConnected: Boolean,
    agentReady: Boolean,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    val colors = LocalSpettroColors.current
    FormSection(title = "Connection") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (hostKind?.lowercase()) {
                    "mac", "macbook", "laptop" -> Icons.Outlined.LaptopMac
                    else -> Icons.Outlined.Computer
                },
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = hostName ?: "Not paired",
                    style = RowLabel.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The route on a line of its own: "Connected on your
                // network" doesn't fit beside a long computer name.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(
                        color = if (isConnected) colors.success else colors.warning,
                        pulsing = !isConnected,
                    )
                    Text(
                        connectionLabel,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        FormDivider()
        FormRow(
            "Agent",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (agentRow(isConnected = isConnected, agentReady = agentReady)) {
                        AgentRow.Running -> {
                            StatusDot(color = colors.success)
                            Text("Running", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        AgentRow.Starting -> {
                            SpettroSpinner(size = 12.dp)
                            Text("Starting…", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        AgentRow.Unknown -> {
                            StatusDot(color = colors.textTertiary)
                            Text("—", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
        )
        FormDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpettroButton("Disconnect", onClick = onDisconnect, size = ButtonSize.Small)
            Spacer(Modifier.weight(1f))
            SpettroButton(
                "Forget this computer…",
                onClick = onForget,
                style = ButtonStyle.Destructive,
                size = ButtonSize.Small,
            )
        }
    }
}

// MARK: - Account

@Composable
private fun AccountSection(
    account: AcpAccountStatus?,
    login: AcpLoginStatus?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    when {
        account?.signedIn == true -> FormSection(title = "Subscription") {
            SignedInRows(account, onSignOut)
        }

        login != null && login.state != AcpLoginStatus.State.IDLE &&
            login.state != AcpLoginStatus.State.CANCELLED -> FormSection(title = "Spettro account") {
            LoginFlowRows(login, onSignIn, onCancelLogin)
        }

        else -> FormSection(title = "Spettro account") {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            ) {
                Text(
                    "Sign in to use Spettro’s own models, with credits included — no API keys to set up.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                    color = LocalSpettroColors.current.textSecondary,
                )
                SpettroButton("Sign in…", onClick = onSignIn, style = ButtonStyle.Prominent, size = ButtonSize.Small)
            }
        }
    }
}

@Composable
private fun SignedInRows(account: AcpAccountStatus, onSignOut: () -> Unit) {
    val colors = LocalSpettroColors.current
    account.email?.let {
        FormRow("Email", value = it, valueColor = colors.textPrimary)
        FormDivider()
    }
    FormRow(
        "Plan",
        trailing = { PlanBadge(plan = account.effectivePlan.ifEmpty { null }, fontSize = 12.sp) },
    )

    account.remainingFraction?.let { fraction ->
        // Never round a non-empty balance down to a flat 0%: "0% left" when
        // there is still credit is worse than being one point optimistic.
        val percent = if (fraction > 0) maxOf(1, (fraction * 100).roundToInt()) else 0
        val low = fraction < 0.10
        FormDivider()
        FormRow(
            "Credits",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CreditMeter(fraction.toFloat(), low)
                    Text(
                        "$percent% left",
                        color = if (low) colors.danger else colors.textSecondary,
                        style = TextStyle(fontSize = 13.sp, fontFeatureSettings = TabularNums),
                    )
                }
            },
        )
    }

    FormDivider()
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
        SpettroButton("Sign out…", onClick = onSignOut, size = ButtonSize.Small)
    }
}

/** The desktop's credit meter: a short, thin bar, accent-filled (danger when low). */
@Composable
private fun CreditMeter(fraction: Float, low: Boolean) {
    val colors = LocalSpettroColors.current
    Box(
        Modifier
            .widthIn(min = 72.dp)
            .size(width = 72.dp, height = 4.dp)
            .clip(CircleShape)
            .background(colors.textPrimary.copy(alpha = 0.11f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(if (low) colors.danger else colors.accent, CircleShape),
        )
    }
}

/**
 * The device-flow sign-in, inline: the CLI runs the flow on the computer and
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
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        when (login.state) {
            AcpLoginStatus.State.ERROR, AcpLoginStatus.State.EXPIRED -> {
                Text(
                    text = if (login.state == AcpLoginStatus.State.EXPIRED) {
                        "That sign-in link expired"
                    } else {
                        "Sign-in didn’t finish"
                    },
                    color = colors.textPrimary,
                    style = RowLabel.copy(fontWeight = FontWeight.Medium),
                )
                login.error?.let {
                    Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                    SpettroButton("Try again", onClick = onSignIn, size = ButtonSize.Small)
                    SpettroButton("Cancel", onClick = onCancelLogin, style = ButtonStyle.Plain, size = ButtonSize.Small)
                }
            }

            AcpLoginStatus.State.COMPLETE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = colors.success)
                    Text(
                        "Signed in — loading your plan…",
                        color = colors.textSecondary,
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
                        "Waiting for the browser…",
                        style = RowLabel.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                    )
                }
                login.browserUrl?.let { url ->
                    Text(
                        "Open this link to finish signing in:",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = url,
                                style = MonoSmall,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy link",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpettroButton(
                            "Open browser",
                            onClick = { uriHandler.openUri(url) },
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            size = ButtonSize.Small,
                        )
                        SpettroButton("Cancel", onClick = onCancelLogin, style = ButtonStyle.Plain, size = ButtonSize.Small)
                    }
                } ?: SpettroLink("Cancel", onClick = onCancelLogin)
            }
        }
    }
}

// MARK: - Appearance

/**
 * The phone's own look, as the desktop's General › Appearance: Theme
 * (System, Light, Dark) and Accent (Lilac, Monochrome), each a segmented
 * control. The accent segments carry a swatch of themselves, not of the
 * current accent.
 */
@Composable
private fun AppearanceSection(
    appearance: Appearance,
    accent: Accent,
    onAppearanceChange: (Appearance) -> Unit,
    onAccentChange: (Accent) -> Unit,
) {
    val colors = LocalSpettroColors.current
    FormSection(
        title = "Appearance",
        footer = "System follows your phone’s light or dark setting.",
    ) {
        LabeledControl("Theme") {
            SegmentedControl(
                options = Appearance.entries,
                selected = appearance,
                onSelect = onAppearanceChange,
                label = { it.name },
            )
        }
        FormDivider()
        LabeledControl("Accent") {
            SegmentedControl(
                options = Accent.entries,
                selected = accent,
                onSelect = onAccentChange,
                label = { if (it == Accent.Lilac) "Lilac" else "Monochrome" },
                leading = { AccentSwatch(if (it == Accent.Lilac) colors.lilac else colors.textPrimary) },
            )
        }
    }
}

/** A form row whose control is too wide to share a line with its label on a phone. */
@Composable
private fun LabeledControl(label: String, control: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = RowLabel, color = LocalSpettroColors.current.textPrimary)
        control()
    }
}

// MARK: - Diagnostics

@Composable
private fun DiagnosticsBlock(diagnostics: List<String>) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken)
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (line in diagnostics) {
            Text(
                text = line,
                color = colors.textSecondary,
                style = MonoSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            )
        }
    }
}

// MARK: - Previews

@Preview(name = "Signed in dark", showBackground = true, backgroundColor = 0xFF1C1C1E, heightDp = 1100)
@Composable
private fun SettingsPreviewSignedIn() {
    SpettroTheme(darkTheme = true) {
        SettingsScreen(
            hostName = "Carlo's MacBook Pro",
            hostKind = "mac",
            connectionLabel = "Connected over Wi-Fi",
            agentReady = true,
            account = AcpAccountStatus(
                signedIn = true,
                email = "carlo@eyed.to",
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

@Preview(name = "Login pending light", showBackground = true, backgroundColor = 0xFFFAFAFA, heightDp = 1100)
@Composable
private fun SettingsPreviewLoginPending() {
    SpettroTheme(darkTheme = false) {
        SettingsScreen(
            hostName = "Carlo's MacBook Pro",
            hostKind = "mac",
            connectionLabel = "Reconnecting…",
            isConnected = false,
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
