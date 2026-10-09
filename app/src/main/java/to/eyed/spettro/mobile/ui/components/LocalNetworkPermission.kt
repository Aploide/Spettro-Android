package to.eyed.spettro.mobile.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import to.eyed.spettro.mobile.core.remote.LocalNetworkAccess

/**
 * Asking for Android 17's local-network permission, the way the rest of the
 * app asks for things: our own sentence first, then the system prompt.
 * Once the user has refused for good (the system stops showing its prompt),
 * asking again opens Spettro's page in system settings instead.
 */
@Stable
class LocalNetworkPermissionRequest internal constructor(
    private val context: Context,
    private val ask: () -> Unit,
) {
    /** Whether the permission is held right now (always true before Android 17). */
    val isGranted: Boolean get() = LocalNetworkAccess.isGranted(context)

    /** Asks — or, when it is already held, reports that at once. */
    fun request() = ask()
}

/**
 * @param onResult called with whether the permission is held once the user
 *        has answered — or straight away, when it already is.
 */
@Composable
fun rememberLocalNetworkPermission(onResult: (Boolean) -> Unit): LocalNetworkPermissionRequest {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var rationaleVisible by remember { mutableStateOf(false) }
    // Survives rotation: "refused for good" is only knowable right after a refusal.
    var refusedForGood by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            val permission = LocalNetworkAccess.permission
            val activity = context.findActivity()
            refusedForGood = permission != null && activity != null &&
                !activity.shouldShowRequestPermissionRationale(permission)
        }
        currentOnResult(granted)
    }

    if (rationaleVisible) {
        AlertDialog(
            onDismissRequest = { rationaleVisible = false },
            title = { Text("Let Spettro reach your computer") },
            text = {
                Text(
                    if (refusedForGood) {
                        "On the same Wi-Fi, Spettro talks to your computer directly, and Android needs your OK " +
                            "for that. In Spettro’s settings, under Permissions, allow Nearby devices."
                    } else {
                        "On the same Wi-Fi, Spettro talks to your computer directly. Android asks before an " +
                            "app can reach devices on your local network, so it will ask you next."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    rationaleVisible = false
                    val permission = LocalNetworkAccess.permission
                    when {
                        permission == null -> currentOnResult(true)
                        refusedForGood -> context.openAppSettings()
                        else -> launcher.launch(permission)
                    }
                }) { Text(if (refusedForGood) "Open settings" else "Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    rationaleVisible = false
                    currentOnResult(false)
                }) { Text("Not now") }
            },
        )
    }

    return remember(context) {
        LocalNetworkPermissionRequest(context) {
            if (LocalNetworkAccess.isGranted(context)) currentOnResult(true) else rationaleVisible = true
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
