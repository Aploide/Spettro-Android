package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The desktop's button kinds (design/shell.css `.btn*`). Neutral is the
 * default; a pane gets at most one [Prominent] action; in-chat primaries use
 * [Ink] rather than the accent.
 */
enum class ButtonStyle {
    /** Raised fill at 80% with a hairline: most buttons. */
    Neutral,

    /** The accent fill: the one main action of a pane (Pair a device, Scan…). */
    Prominent,

    /** The ink fill (text-primary under canvas): send, a prompt's main answer. */
    Ink,

    /** A neutral button with danger text: an action that removes something. */
    Destructive,

    /** A filled danger button: the confirming step of a destructive action. */
    DestructiveFill,

    /** No fill and no border: a dialog's secondary action, a toolbar text button. */
    Plain,
}

/** `.btn--small`, the default, and `.btn--large`, grown to phone touch sizes. */
enum class ButtonSize(val minHeight: Dp, val padding: PaddingValues, val fontSize: Float) {
    Small(32.dp, PaddingValues(horizontal = 12.dp), 12f),
    Regular(40.dp, PaddingValues(horizontal = 16.dp), 13f),
    Large(48.dp, PaddingValues(horizontal = 22.dp), 15f),
}

private data class ButtonColors(val container: Color, val content: Color, val border: Color?)

private fun SpettroColors.buttonColors(style: ButtonStyle, enabled: Boolean): ButtonColors = when (style) {
    ButtonStyle.Neutral -> ButtonColors(surfaceRaised.copy(alpha = 0.8f), textPrimary, hairline)
    // Disabled is a neutral pill, never a dimmed accent — that read as a
    // muddy, broken button on the desktop.
    ButtonStyle.Prominent -> if (enabled) {
        ButtonColors(accent, accentContrast, null)
    } else {
        ButtonColors(selectedFill, textTertiary, null)
    }
    ButtonStyle.Ink -> ButtonColors(ink, onInk, null)
    ButtonStyle.Destructive -> ButtonColors(surfaceRaised.copy(alpha = 0.8f), danger, hairline)
    ButtonStyle.DestructiveFill -> ButtonColors(dangerFill, dangerContrast, null)
    ButtonStyle.Plain -> ButtonColors(Color.Transparent, textPrimary, null)
}

/**
 * A pill button in one of the desktop's [ButtonStyle]s. Labels are sentence
 * case, and an action that opens more UI ends in "…".
 */
@Composable
fun SpettroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Neutral,
    size: ButtonSize = ButtonSize.Regular,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = CircleShape,
) {
    val colors = LocalSpettroColors.current.buttonColors(style, enabled)
    // Prominent draws its own disabled look; the others fade.
    val fade = if (!enabled && style != ButtonStyle.Prominent) {
        if (style == ButtonStyle.Ink) 0.25f else 0.5f
    } else {
        1f
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(fade),
        shape = shape,
        color = colors.container,
        contentColor = colors.content,
        border = colors.border?.let { BorderStroke(Dimens.hairlineWidth, it) },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = size.minHeight)
                .padding(size.padding),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size((size.fontSize + 4).dp))
            }
            Text(
                text = text,
                style = TextStyle(
                    fontSize = size.fontSize.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * A text-only action in the accent's text colour (`.link`): "Rename",
 * "Enter code manually". Pass [color] for a danger link.
 */
@Composable
fun SpettroLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalSpettroColors.current.accentText,
    fontSize: Float = 13f,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusXs))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        color = color,
        style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Medium),
    )
}

@Preview(name = "Buttons dark", showBackground = true, backgroundColor = 0xFF1C1C1E)
@Composable
private fun SpettroButtonPreviewDark() {
    SpettroTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpettroButton("Scan the code", {}, style = ButtonStyle.Prominent, size = ButtonSize.Large, icon = Icons.Default.QrCodeScanner)
            SpettroButton("Try again", {})
            SpettroButton("Send answer", {}, style = ButtonStyle.Ink)
            SpettroButton("Forget this computer…", {}, style = ButtonStyle.Destructive)
            SpettroButton("Forget", {}, style = ButtonStyle.DestructiveFill)
            SpettroButton("Pair", {}, style = ButtonStyle.Prominent, enabled = false)
            SpettroLink("Enter code manually", {})
        }
    }
}

@Preview(name = "Buttons light", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun SpettroButtonPreviewLight() {
    SpettroTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpettroButton("Pair a device", {}, style = ButtonStyle.Prominent)
            SpettroButton("Try again", {})
            SpettroButton("Send answer", {}, style = ButtonStyle.Ink)
        }
    }
}
