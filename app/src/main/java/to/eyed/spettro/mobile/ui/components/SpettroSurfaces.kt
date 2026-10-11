package to.eyed.spettro.mobile.ui.components

import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.Elevation
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.MonoSmall
import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.theme.floatingShadow

// ---------------------------------------------------------------------------
// Floating layers: sheets, menus, dialogs. Material draws these with tonal
// elevation and 28dp corners; the desktop draws them as raised surfaces under
// a hairline, 12dp corners, and a real shadow. These wrappers pin that look so
// call sites don't repeat it.
// ---------------------------------------------------------------------------

/**
 * A bottom sheet in the desktop's popover dress: raised, the scrim token,
 * 20dp top corners, no tonal tint, and a small neutral grabber.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpettroSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Dimens.radiusLg, topEnd = Dimens.radiusLg),
        containerColor = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = { SheetGrabber() },
        content = content,
    )
}

/** The sheet's drag handle: a 36×4 pill in the tertiary ink, quietly. */
@Composable
fun SheetGrabber() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(LocalSpettroColors.current.textTertiary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * A context menu (shell.css `.context-menu`): raised, hairline, 12dp radius,
 * the medium shadow, 4dp inset.
 */
@Composable
fun SpettroMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 196.dp),
        offset = offset,
        shape = RoundedCornerShape(Dimens.radiusMd),
        containerColor = colors.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        content = content,
    )
}

/**
 * One item of a [SpettroMenu]: 14sp, a quiet icon, danger text for a
 * destructive action, and — when disabled — the reason as a small line under
 * the label rather than only in a tooltip.
 */
@Composable
fun SpettroMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    reason: String? = null,
) {
    val colors = LocalSpettroColors.current
    val ink = if (destructive) colors.danger else colors.textPrimary
    DropdownMenuItem(
        text = {
            Column {
                Text(text, style = TextStyle(fontSize = 14.sp))
                if (!enabled && reason != null) {
                    Text(reason, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            }
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        colors = MenuDefaults.itemColors(
            textColor = ink,
            leadingIconColor = if (destructive) colors.danger else colors.textSecondary,
            disabledTextColor = colors.textTertiary,
            disabledLeadingIconColor = colors.textTertiary,
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** The hairline between groups of menu items. */
@Composable
fun SpettroMenuSeparator() {
    HairlineDivider(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

/**
 * The desktop's confirm dialog (common.css `.confirm`): a raised card with a
 * warning or danger icon on top, a 15sp title (curly-quote the thing it
 * names: Delete “Fix the crash”?), the consequence in a sentence, and the
 * buttons right-aligned — Cancel, then the action, filled red when it
 * destroys something.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String? = "Cancel",
    icon: ImageVector? = if (destructive) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
    enabled: Boolean = true,
) {
    SpettroDialog(onDismiss = onDismiss) {
        val colors = LocalSpettroColors.current
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) colors.danger else colors.warning,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (dismissLabel != null) {
                SpettroButton(dismissLabel, onClick = onDismiss, enabled = enabled)
            }
            SpettroButton(
                confirmLabel,
                onClick = onConfirm,
                style = if (destructive) ButtonStyle.DestructiveFill else ButtonStyle.Prominent,
                enabled = enabled,
            )
        }
    }
}

/** A dialog card: raised, hairline, 12dp radius, the large shadow, 24/24/16 padding. */
@Composable
fun SpettroDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    Dialog(onDismissRequest = onDismiss) {
        // The dialog window pans by default: the keyboard keeps the focused
        // field in view and covers whatever is under it — on a phone on its
        // side, the buttons. Resizing instead fits the dialog above the
        // keyboard, where a dialog that scrolls its body keeps them in view.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val shape = RoundedCornerShape(Dimens.radiusMd)
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).floatingShadow(Elevation.Large, shape),
            shape = shape,
            color = colors.surfaceRaised,
            contentColor = colors.textPrimary,
            border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp), content = content)
        }
    }
}

/**
 * "Show details": a chevron that turns 90° in 140ms over a body revealed
 * beneath it — the raw error behind a humanized one, a log.
 */
@Composable
fun Disclosure(
    label: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSpettroColors.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val turn by animateFloatAsState(if (expanded) 90f else 0f, SpettroMotion.chevron(), label = "disclosure")
    Column(modifier) {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .defaultMinSize(minHeight = 36.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp).rotate(turn),
            )
            Text(
                if (expanded) label.replaceFirst("Show", "Hide") else label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(SpettroMotion.quick()) + expandVertically(SpettroMotion.quick()),
            exit = fadeOut(SpettroMotion.quick()) + shrinkVertically(SpettroMotion.quick()),
        ) {
            Column(content = content)
        }
    }
}

/**
 * A text input (form.css `.input`): a faint text-secondary 10% wash under a
 * hairline that turns accent 50% on focus, 8dp radius. [mono] for codes and
 * addresses.
 */
@Composable
fun SpettroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = if (mono) MonoSmall.copy(fontSize = 13.sp) else TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val shape = RoundedCornerShape(Dimens.radiusSm)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .background(colors.textSecondary.copy(alpha = 0.1f).compositeOver(colors.surfaceRaised), shape)
                    .border(
                        Dimens.hairlineWidth,
                        if (focused) colors.accent.copy(alpha = 0.5f) else colors.hairline,
                        shape,
                    )
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = colors.textTertiary, maxLines = maxLines)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * An empty state with a way out (`.sidebar-empty`, the Remote pane's "No
 * devices yet"): a quiet icon, a title, one sentence, and optionally an
 * action.
 */
@Composable
fun EmptyState(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

/** How a toast reads (common.css `.toast--*`). */
enum class ToastTone { Info, Success, Error }

/**
 * The desktop's toast: a raised card under a hairline with the medium
 * shadow, a title in 600 and an optional detail line, an icon coloured by
 * tone, and an optional pill action. Placement and timing belong to the
 * caller (top centre; 4s, 6s for errors, 8s with an action).
 */
@Composable
fun SpettroToast(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: ToastTone = ToastTone.Info,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    details: String? = null,
) {
    val colors = LocalSpettroColors.current
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Surface(
        modifier = modifier.widthIn(max = 520.dp).floatingShadow(Elevation.Medium, shape),
        shape = shape,
        color = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        border = BorderStroke(Dimens.hairlineWidth, colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when (tone) {
                        ToastTone.Error -> colors.danger
                        ToastTone.Success -> colors.success
                        ToastTone.Info -> colors.textSecondary
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                // The error as it arrived, for whoever needs to report it.
                if (details != null) {
                    Disclosure("Show details") {
                        Text(
                            details,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .background(colors.surfaceSunken, RoundedCornerShape(Dimens.radiusSm))
                                .border(Dimens.hairlineWidth, colors.hairline, RoundedCornerShape(Dimens.radiusSm))
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MonoSmall,
                            color = colors.textSecondary,
                        )
                        SpettroLink(
                            "Copy",
                            onClick = { clipboard.setText(AnnotatedString(details)) },
                            fontSize = 12f,
                        )
                    }
                }
            }
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    modifier = Modifier
                        .background(colors.accentText.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.accentText,
                )
            }
            if (onClose != null) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onClose)
                        .padding(5.dp),
                )
            }
        }
    }
}
