package to.eyed.spettro.mobile.ui.screens.settings

import to.eyed.spettro.mobile.ui.components.Pill
import to.eyed.spettro.mobile.ui.components.SpettroTopBar
import to.eyed.spettro.mobile.ui.components.SpettroTextField
import to.eyed.spettro.mobile.ui.components.SpettroSwitch
import to.eyed.spettro.mobile.ui.components.SpettroSheet
import to.eyed.spettro.mobile.ui.components.SpettroButton
import to.eyed.spettro.mobile.ui.components.RingSpinner
import to.eyed.spettro.mobile.ui.components.EmptyState
import to.eyed.spettro.mobile.ui.components.ConfirmDialog
import to.eyed.spettro.mobile.ui.components.ButtonStyle
import to.eyed.spettro.mobile.ui.components.ButtonSize
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.core.acp.AcpLocalEndpoint
import to.eyed.spettro.mobile.core.acp.AcpModelEntry
import to.eyed.spettro.mobile.core.acp.AcpModelsList
import to.eyed.spettro.mobile.core.acp.AcpProviderEntry
import to.eyed.spettro.mobile.core.acp.AcpProvidersList
import to.eyed.spettro.mobile.ui.components.HairlineDivider
import to.eyed.spettro.mobile.ui.components.SectionHeader
import to.eyed.spettro.mobile.ui.components.SpettroCard
import to.eyed.spettro.mobile.ui.components.SpettroSpinner
import to.eyed.spettro.mobile.ui.components.StatusDot
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * Providers & models: the Spettro subscription, connected API providers,
 * OpenAI-compatible local servers, and the model catalog with favorites.
 * Port of `MobileProvidersView.swift` in the Spettro card language.
 *
 * Fail open: [lastRefreshFailed] shows a non-blocking banner over the last
 * known lists — a refresh that failed is never treated as "everything
 * disconnected".
 *
 * @param probeResult the outcome message of the last local-endpoint probe
 *   (success or failure copy), delivered asynchronously by the caller.
 * @param probeSucceeded whether that probe found a server — gates the
 *   "Add server" button in [AddLocalServerSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    providers: AcpProvidersList?,
    models: AcpModelsList?,
    isLoading: Boolean,
    lastRefreshFailed: Boolean,
    probeResult: String?,
    probeSucceeded: Boolean,
    onConnectProvider: (providerId: String, apiKey: String, activate: Boolean) -> Unit,
    onDisconnectProvider: (providerId: String) -> Unit,
    onProbeLocal: (endpoint: String, apiKey: String?) -> Unit,
    onAddLocal: (endpoint: String, apiKey: String?) -> Unit,
    onRemoveLocal: (endpoint: String) -> Unit,
    onToggleFavorite: (provider: String, model: String, favorite: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    var search by rememberSaveable { mutableStateOf("") }
    var connectingId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingLocal by rememberSaveable { mutableStateOf(false) }
    var disconnectingId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SpettroTopBar(
                title = "Models & providers",
                onBack = onBack,
                actions = {
                    if (isLoading) {
                        RingSpinner(size = 16.dp, modifier = Modifier.padding(end = Dimens.spacingLg))
                    }
                },
            )
        },
    ) { padding ->
        if (providers == null && models == null) {
            // Nothing known yet — first load.
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    RingSpinner(size = 24.dp)
                } else {
                    EmptyState(
                        title = "Couldn’t load your providers",
                        message = "Spettro on your computer didn’t answer. Go back and try again in a moment.",
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                bottom = Dimens.spacingXl,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (lastRefreshFailed) {
                item(key = "banner") { RefreshFailedBanner() }
            }

            item(key = "search") {
                SpettroTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Search models",
                    leading = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                )
            }

            // While a search is typed, its results come straight under the
            // box, as in the desktop's model picker: the sections above the
            // models would otherwise keep the matches off screen.
            val searching = search.isNotBlank()

            // Subscription.
            if (!searching && providers != null && providers.subscription.connected) {
                item(key = "subscription") {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                        SectionHeader("Spettro subscription", modifier = Modifier.padding(start = 12.dp))
                        SpettroCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimens.spacingMd),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = colors.accentText,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(providers.subscription.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${providers.subscription.modelCount} models included with " +
                                            "your plan — no API key needed.",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Local endpoints.
            if (!searching && providers != null) {
                item(key = "local") {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                        SectionHeader("Models on your computer", modifier = Modifier.padding(start = 12.dp))
                        SpettroCard(modifier = Modifier.fillMaxWidth()) {
                            providers.local.forEachIndexed { index, endpoint ->
                                if (index > 0) HairlineDivider()
                                LocalEndpointRow(endpoint, onRemove = { onRemoveLocal(endpoint.endpoint) })
                            }
                            if (providers.local.isNotEmpty()) HairlineDivider()
                            Surface(onClick = { addingLocal = true }, color = Color.Transparent) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimens.spacingMd),
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = colors.accentText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Add a local server…",
                                        color = colors.accentText,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        Text(
                            "Run a model on your computer with LM Studio (easiest) or Ollama, then add its address here.",
                            color = colors.textSecondary.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 16.sp),
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                        )
                    }
                }

                // API providers.
                item(key = "providers") {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                        SectionHeader("Providers", modifier = Modifier.padding(start = 12.dp))
                        SpettroCard(modifier = Modifier.fillMaxWidth()) {
                            providers.providers.forEachIndexed { index, provider ->
                                if (index > 0) HairlineDivider()
                                ProviderRow(
                                    provider = provider,
                                    onConnect = { connectingId = provider.id },
                                    onDisconnect = { disconnectingId = provider.id },
                                )
                            }
                        }
                    }
                }
            }

            // Models, grouped by provider, filtered by the search text.
            if (models != null) {
                val query = search.trim().lowercase()
                val groups = models.grouped.mapNotNull { group ->
                    val filtered = if (query.isEmpty()) {
                        group.models
                    } else {
                        group.models.filter {
                            it.displayName.lowercase().contains(query) ||
                                it.name.lowercase().contains(query) ||
                                it.providerName.lowercase().contains(query)
                        }
                    }
                    if (filtered.isEmpty()) null else group.copy(models = filtered)
                }
                if (searching && groups.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            "No model matches “${search.trim()}”.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp, top = Dimens.spacingSm),
                        )
                    }
                }
                if (groups.isNotEmpty()) {
                    item(key = "models-header") {
                        SectionHeader(
                            "Models",
                            modifier = Modifier.padding(start = 12.dp, top = Dimens.spacingSm),
                        )
                    }
                }
                items(count = groups.size, key = { "group-" + groups[it].provider }) { index ->
                    val group = groups[index]
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                        Text(
                            text = group.provider,
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        SpettroCard(modifier = Modifier.fillMaxWidth()) {
                            group.models.forEachIndexed { modelIndex, model ->
                                if (modelIndex > 0) HairlineDivider()
                                ModelRow(
                                    model = model,
                                    onToggleFavorite = {
                                        onToggleFavorite(model.provider, model.name, !model.favorite)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val connectingProvider = providers?.providers?.firstOrNull { it.id == connectingId }
    if (connectingProvider != null) {
        ConnectProviderSheet(
            provider = connectingProvider,
            onConnect = { apiKey, activate ->
                onConnectProvider(connectingProvider.id, apiKey, activate)
                connectingId = null
            },
            onDismiss = { connectingId = null },
        )
    }

    val disconnectingProvider = providers?.providers?.firstOrNull { it.id == disconnectingId }
    if (disconnectingProvider != null) {
        ConfirmDialog(
            title = "Disconnect “${disconnectingProvider.name}”?",
            message = "Its key is removed from your computer. Chats using its models will need another model.",
            confirmLabel = "Disconnect",
            onConfirm = {
                disconnectingId = null
                onDisconnectProvider(disconnectingProvider.id)
            },
            onDismiss = { disconnectingId = null },
        )
    }

    if (addingLocal) {
        AddLocalServerSheet(
            probeResult = probeResult,
            probeSucceeded = probeSucceeded,
            onProbe = onProbeLocal,
            onAdd = { endpoint, apiKey ->
                onAddLocal(endpoint, apiKey)
                addingLocal = false
            },
            onDismiss = { addingLocal = false },
        )
    }
}

// MARK: - Rows

/** The list is from the last good refresh: the desktop's inline warning notice. */
@Composable
private fun RefreshFailedBanner() {
    val colors = LocalSpettroColors.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = 0.10f).compositeOver(colors.canvas), shape)
            .border(1.dp, colors.warning.copy(alpha = 0.35f), shape)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(16.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Showing the last list Spettro sent") }
                append(". It couldn’t refresh just now; pull back in later to try again.")
            },
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LocalEndpointRow(endpoint: AcpLocalEndpoint, onRemove: () -> Unit) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd, top = Dimens.spacingSm, bottom = Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Computer,
            contentDescription = null,
            tint = colors.accentText,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                endpoint.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                endpoint.shortHost,
                color = colors.textSecondary,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${endpoint.modelCount} models",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove ${endpoint.name}",
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * One API provider. A connected one is not a button: removing its key takes
 * the explicit "Disconnect…" (the desktop's row), so a tap on the row on the
 * way to somewhere else can't land on it. An unconnected one opens Connect.
 */
@Composable
private fun ProviderRow(provider: AcpProviderEntry, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val colors = LocalSpettroColors.current
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimens.spacingMd,
                    end = Dimens.spacingMd,
                    top = if (provider.connected) Dimens.spacingSm else Dimens.spacingMd,
                    bottom = if (provider.connected) Dimens.spacingSm else Dimens.spacingMd,
                ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (provider.connected) Icons.Outlined.Verified else Icons.Outlined.Key,
                contentDescription = if (provider.connected) "Connected" else null,
                tint = if (provider.connected) colors.accentText else colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(provider.name, style = MaterialTheme.typography.bodyMedium)
                    if (provider.suggested && !provider.connected) {
                        Text(
                            "popular",
                            color = colors.accentText,
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier
                                .background(colors.accentSoft, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (provider.connected) {
                    Text(
                        if (provider.modelCount > 0) "Connected · ${provider.modelCount} models" else "Connected",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (provider.connected) {
                SpettroButton("Disconnect…", onClick = onDisconnect, size = ButtonSize.Small)
            } else {
                Text(
                    "Connect",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    if (provider.connected) content() else Surface(onClick = onConnect, color = Color.Transparent) { content() }
}

@Composable
private fun ModelRow(model: AcpModelEntry, onToggleFavorite: () -> Unit) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingMd, top = Dimens.spacingSm, bottom = Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.active) StatusDot(color = colors.accentText, size = 6.dp)
                Text(
                    text = model.displayName,
                    color = if (model.active) colors.accentText else MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (model.active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                model.contextLabel?.let {
                    Text(
                        it,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (model.vision) CapabilityChip("vision")
                if (model.reasoning) CapabilityChip("reasoning")
                if (model.toolCall) CapabilityChip("tools")
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (model.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (model.favorite) "Unfavorite" else "Favorite",
                tint = if (model.favorite) colors.accentText else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CapabilityChip(label: String) {
    Pill(label)
}

// MARK: - Connect provider sheet

/**
 * API-key entry for one provider. The key is typed here but never lives
 * here — it travels straight to the computer's encrypted key store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectProviderSheet(
    provider: AcpProviderEntry,
    onConnect: (apiKey: String, activate: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SpettroSheet(onDismissRequest = onDismiss) {
        ConnectProviderSheetContent(provider = provider, onConnect = onConnect)
    }
}

@Composable
internal fun ConnectProviderSheetContent(
    provider: AcpProviderEntry,
    onConnect: (apiKey: String, activate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    var apiKey by rememberSaveable { mutableStateOf("") }
    var activate by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, bottom = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
    ) {
        Text(
            "Connect ${provider.name}",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("API key")
            SpettroTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = provider.envKey ?: "sk-…",
                mono = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Switch to this provider", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Make one of its models active after connecting.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SpettroSwitch(checked = activate, onCheckedChange = { activate = it })
        }
        Text(
            "Sent straight to your computer and stored in the CLI's encrypted key store. " +
                "It is never kept on this device.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SpettroButton(
            "Connect",
            onClick = { onConnect(apiKey.trim(), activate) },
            enabled = apiKey.isNotBlank(),
            style = ButtonStyle.Prominent,
            size = ButtonSize.Large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// MARK: - Add local server sheet

private data class LocalPreset(val name: String, val port: Int)

private val LocalPresets = listOf(
    LocalPreset("LM Studio", 1234),
    LocalPreset("Ollama", 11434),
    LocalPreset("llama.cpp", 8080),
)

/**
 * Add an OpenAI-compatible local server. Probe before saving: an endpoint
 * that saves cleanly and then serves nothing is a worse outcome than being
 * told now that nothing answered — Add stays disabled until a probe succeeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocalServerSheet(
    probeResult: String?,
    probeSucceeded: Boolean,
    onProbe: (endpoint: String, apiKey: String?) -> Unit,
    onAdd: (endpoint: String, apiKey: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SpettroSheet(onDismissRequest = onDismiss) {
        AddLocalServerSheetContent(
            probeResult = probeResult,
            probeSucceeded = probeSucceeded,
            onProbe = onProbe,
            onAdd = onAdd,
        )
    }
}

@Composable
internal fun AddLocalServerSheetContent(
    probeResult: String?,
    probeSucceeded: Boolean,
    onProbe: (endpoint: String, apiKey: String?) -> Unit,
    onAdd: (endpoint: String, apiKey: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpettroColors.current
    var endpoint by rememberSaveable { mutableStateOf("http://localhost:1234") }
    var apiKey by rememberSaveable { mutableStateOf("") }

    fun keyOrNull(): String? = apiKey.trim().ifEmpty { null }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, bottom = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        Text(
            "Add a local server",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            for (preset in LocalPresets) {
                SpettroButton(
                    preset.name,
                    onClick = { endpoint = "http://localhost:${preset.port}" },
                    size = ButtonSize.Small,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Endpoint")
            SpettroTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "http://localhost:1234",
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("API key (optional)")
            SpettroTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Only if the server asks for one",
                mono = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        Text(
            "The address as your computer sees it — localhost here means your computer, not this phone.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        probeResult?.let {
            Text(
                text = it,
                color = if (probeSucceeded) colors.success else colors.danger,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            SpettroButton(
                "Check",
                onClick = { onProbe(endpoint.trim(), keyOrNull()) },
                enabled = endpoint.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            SpettroButton(
                "Add server",
                onClick = { onAdd(endpoint.trim(), keyOrNull()) },
                enabled = probeSucceeded,
                style = ButtonStyle.Prominent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// MARK: - Previews

internal val previewProviders = AcpProvidersList(
    providers = listOf(
        AcpProviderEntry(id = "anthropic", name = "Anthropic", connected = true, modelCount = 8),
        AcpProviderEntry(id = "openai", name = "OpenAI", suggested = true),
        AcpProviderEntry(id = "groq", name = "Groq"),
    ),
    local = listOf(
        AcpLocalEndpoint(endpoint = "http://localhost:1234", name = "LM Studio", modelCount = 3),
    ),
    subscription = AcpProviderEntry(id = "spettro", name = "Spettro Subscription", connected = true, modelCount = 12),
)

internal val previewModels = AcpModelsList(
    models = listOf(
        AcpModelEntry(
            provider = "anthropic", providerName = "Anthropic",
            name = "claude-sonnet", displayName = "Claude Sonnet",
            vision = true, reasoning = true, toolCall = true,
            context = 200_000, favorite = true, active = true,
        ),
        AcpModelEntry(
            provider = "anthropic", providerName = "Anthropic",
            name = "claude-haiku", displayName = "Claude Haiku",
            vision = true, toolCall = true, context = 200_000,
        ),
        AcpModelEntry(
            provider = "local", providerName = "LM Studio",
            name = "qwen3-8b", displayName = "Qwen3 8B",
            reasoning = true, toolCall = true, context = 32_000, local = true,
        ),
    ),
    activeProvider = "anthropic",
    activeModel = "claude-sonnet",
)

@Preview(name = "Providers dark", showBackground = true, backgroundColor = 0xFF1C1C1E, heightDp = 980)
@Composable
private fun ProvidersScreenPreview() {
    SpettroTheme(darkTheme = true) {
        ProvidersScreen(
            providers = previewProviders,
            models = previewModels,
            isLoading = false,
            lastRefreshFailed = true,
            probeResult = null,
            probeSucceeded = false,
            onConnectProvider = { _, _, _ -> },
            onDisconnectProvider = {},
            onProbeLocal = { _, _ -> },
            onAddLocal = { _, _ -> },
            onRemoveLocal = {},
            onToggleFavorite = { _, _, _ -> },
            onBack = {},
        )
    }
}

@Preview(name = "Connect sheet light", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun ConnectProviderSheetPreview() {
    SpettroTheme(darkTheme = false) {
        ConnectProviderSheetContent(
            provider = AcpProviderEntry(id = "openai", name = "OpenAI", envKey = "OPENAI_API_KEY"),
            onConnect = { _, _ -> },
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}

@Preview(name = "Add local dark", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun AddLocalServerSheetPreview() {
    SpettroTheme(darkTheme = true) {
        AddLocalServerSheetContent(
            probeResult = "LM Studio — found 3 models.",
            probeSucceeded = true,
            onProbe = { _, _ -> },
            onAdd = { _, _ -> },
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}
