package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import to.eyed.spettro.mobile.model.RunPhase
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme
import to.eyed.spettro.mobile.ui.theme.TabularNums
import java.util.Locale

/**
 * The live readout of an in-flight turn, as one muted line at the tail of
 * the transcript, as the desktop draws it (RunTicker.tsx): the spinner in the
 * mode's colour, what the turn is doing ([phase], shimmering — see
 * [to.eyed.spettro.mobile.model.RunPhase]), how long it has run, and the
 * tokens it has used. The desktop's "Esc to interrupt" has no phone
 * counterpart: the composer's Stop button is right there.
 *
 * It ticks once a second rather than per frame: the spinner carries the sense
 * of motion, and re-laying out a text run at 60 fps to advance a seconds
 * counter would be wasted work.
 *
 * @param startedAt epoch millis the run started, or null when idle (renders
 *   nothing).
 * @param tokens live token count; hidden when null or zero.
 * @param color the active mode's tint, so the spinner matches the agent
 *   that's running.
 */
@Composable
fun RunTicker(
    startedAt: Long?,
    tokens: Long?,
    modifier: Modifier = Modifier,
    phase: String? = null,
    color: Color = LocalSpettroColors.current.accentText,
) {
    if (startedAt == null) return

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val colors = LocalSpettroColors.current
    val numbers = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TabularNums)
    val elapsed = RunPhase.elapsed(now - startedAt)
    Row(
        modifier = modifier
            .heightIn(min = 26.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(phase, elapsed).joinToString(" ") },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpettroSpinner(size = 12.dp, color = color)
        if (phase != null) {
            Shimmer(
                text = phase,
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Text(elapsed, style = numbers, color = colors.textTertiary)
        if (tokens != null && tokens > 0) {
            Text("·", style = numbers, color = colors.textTertiary)
            Text("${formatTokens(tokens)} tokens", style = numbers, color = colors.textTertiary, maxLines = 1)
        }
    }
}

/** "42s", then "3m 07s", then "1h 12m" — mirrors the iOS elapsed label. */
internal fun elapsedLabel(elapsedMillis: Long): String {
    val seconds = (elapsedMillis / 1_000).coerceAtLeast(0)
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m %02ds".format(Locale.US, seconds % 60)
    return "${minutes / 60}h ${minutes % 60}m"
}

/** 999 -> "999", 12_345 -> "12.3k", 4_200_000 -> "4.2M". */
internal fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
    else -> n.toString()
}

@Preview(name = "Run ticker", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun RunTickerPreview() {
    SpettroTheme(darkTheme = true) {
        RunTicker(
            startedAt = System.currentTimeMillis() - 83_000,
            tokens = 12_345,
            phase = RunPhase.WORKING,
            modifier = Modifier.padding(24.dp),
        )
    }
}
