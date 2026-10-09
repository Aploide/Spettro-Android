package to.eyed.spettro.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.eyed.spettro.mobile.ui.theme.Dimens
import to.eyed.spettro.mobile.ui.theme.Elevation
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroMotion
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.floatingShadow

/**
 * The desktop's settings switch (form.css `.switch`), at the Remote pane's
 * larger 40×24 size for a finger: a pill track — `switch-on` when on, grey
 * text-secondary at 35% when off — under an always-white thumb that slides in
 * 150ms. Not Material's switch, whose thumb grows and whose track is
 * outlined: this one has to look like the computer's.
 */
@Composable
fun SpettroSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalSpettroColors.current
    val track by animateColorAsState(
        if (checked) colors.switchOn else colors.switchOff,
        animationSpec = tween(SpettroMotion.SWITCH_MS),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 16.dp else 0.dp,
        animationSpec = tween(SpettroMotion.SWITCH_MS),
        label = "switchThumb",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(toggle)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 24.dp)
                .background(track, CircleShape)
                .padding(2.dp),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .floatingShadow(Elevation.Small, CircleShape)
                    .background(colors.paper, CircleShape),
            )
        }
    }
}

/**
 * The desktop's segmented control (shell.css `.segmented`): a tinted track
 * with the active segment raised on it. Used for the Appearance and Accent
 * settings.
 *
 * @param leading an optional mark before each label (the accent swatches).
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    val colors = LocalSpettroColors.current
    Row(
        modifier = modifier
            .background(colors.textSecondary.copy(alpha = 0.12f), RoundedCornerShape(Dimens.radiusSm))
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            val shape = RoundedCornerShape(Dimens.radiusXs)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (isSelected) Modifier.floatingShadow(Elevation.Small, shape) else Modifier)
                    .background(if (isSelected) colors.surfaceRaised else Color.Transparent, shape)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke(option)
                Text(
                    text = label(option),
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The accent picker's swatch: a 10dp disc with a hairline ring. */
@Composable
fun AccentSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(color, CircleShape)
            .border(0.5.dp, LocalSpettroColors.current.hairline, CircleShape),
    )
}

/** The desktop's small pills and tags (`.tag`, `.prompt-pill`, the "Needs you" pill). */
enum class PillTone { Neutral, Success, Warning, Accent }

/** A small rounded tag: 11sp on a 14–16% tint of its tone. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Neutral,
) {
    val colors = LocalSpettroColors.current
    val (fill, ink, weight) = when (tone) {
        PillTone.Neutral -> Triple(colors.hoverFill, colors.textSecondary, FontWeight.Medium)
        PillTone.Success -> Triple(colors.success.copy(alpha = 0.14f), colors.success, FontWeight.Medium)
        PillTone.Warning -> Triple(colors.warning.copy(alpha = 0.16f), colors.warning, FontWeight.SemiBold)
        PillTone.Accent -> Triple(colors.accentSoft, colors.accentText, FontWeight.SemiBold)
    }
    Text(
        text = text,
        modifier = modifier
            .background(fill, CircleShape)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        color = ink,
        style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = weight),
        maxLines = 1,
    )
}

@Preview(name = "Controls dark", showBackground = true, backgroundColor = 0xFF1C1C1E, widthDp = 360)
@Composable
private fun ControlsPreviewDark() {
    SpettroTheme(darkTheme = true) {
        val colors = LocalSpettroColors.current
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpettroSwitch(checked = true, onCheckedChange = {})
                SpettroSwitch(checked = false, onCheckedChange = {})
            }
            SegmentedControl(
                options = listOf("System", "Light", "Dark"),
                selected = "System",
                onSelect = {},
                label = { it },
            )
            SegmentedControl(
                options = listOf("Lilac", "Monochrome"),
                selected = "Lilac",
                onSelect = {},
                label = { it },
                leading = { AccentSwatch(if (it == "Lilac") colors.lilac else colors.textPrimary) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Needs you", tone = PillTone.Warning)
                Pill("Recommended", tone = PillTone.Success)
                Pill("3 files")
            }
        }
    }
}
