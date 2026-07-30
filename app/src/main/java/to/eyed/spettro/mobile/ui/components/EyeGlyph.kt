package to.eyed.spettro.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.LocalSpettroColors
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The Spettro eye brand mark, drawn as vector paths so it can be tinted and
 * scaled freely — the almond/lens outline as two tapering crescents meeting
 * in sharp points, with a vertical rounded-quadrilateral pupil offset right
 * of centre (per the app icon art).
 *
 * Used as the fallback brand image and in empty states. Size the glyph with
 * the modifier (e.g. `Modifier.size(64.dp)`); it centers itself in whatever
 * bounds it is given, preserving the eye's 2:1-ish proportions.
 */
@Composable
fun EyeGlyph(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(48.dp)) {
        drawEye(tint)
    }
}

private fun DrawScope.drawEye(tint: Color) {
    // Work in a centered box twice as wide as tall — the eye's natural frame.
    val w: Float
    val h: Float
    if (size.width >= size.height * 2f) {
        h = size.height
        w = h * 2f
    } else {
        w = size.width
        h = w / 2f
    }
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f

    fun x(u: Float) = left + u * w
    fun y(v: Float) = top + v * h

    // Upper lid: a tapering crescent — outer arc up, inner arc back — meeting
    // the lower lid in sharp points at the corners of the eye.
    val upper = Path().apply {
        moveTo(x(0.02f), y(0.5f))
        cubicTo(x(0.24f), y(0.02f), x(0.76f), y(0.02f), x(0.98f), y(0.5f))
        cubicTo(x(0.78f), y(0.20f), x(0.22f), y(0.20f), x(0.02f), y(0.5f))
        close()
    }
    // Lower lid, mirrored.
    val lower = Path().apply {
        moveTo(x(0.02f), y(0.5f))
        cubicTo(x(0.24f), y(0.98f), x(0.76f), y(0.98f), x(0.98f), y(0.5f))
        cubicTo(x(0.78f), y(0.80f), x(0.22f), y(0.80f), x(0.02f), y(0.5f))
        close()
    }
    // The pupil: a vertical rounded quadrilateral, offset right of centre.
    val pupilWidth = 0.13f * w
    val pupilHeight = 0.52f * h
    val pupil = Path().apply {
        addRoundRect(
            RoundRect(
                left = x(0.60f) - pupilWidth / 2f,
                top = y(0.5f) - pupilHeight / 2f,
                right = x(0.60f) + pupilWidth / 2f,
                bottom = y(0.5f) + pupilHeight / 2f,
                cornerRadius = CornerRadius(pupilWidth * 0.42f),
            ),
        )
    }

    drawPath(upper, tint)
    drawPath(lower, tint)
    drawPath(pupil, tint)
}

@Preview(name = "Eye glyph", showBackground = true, backgroundColor = 0xFF0E0E0E)
@Composable
private fun EyeGlyphPreview() {
    SpettroTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            EyeGlyph(modifier = Modifier.size(32.dp))
            EyeGlyph(modifier = Modifier.size(64.dp))
            EyeGlyph(
                modifier = Modifier.size(96.dp),
                tint = LocalSpettroColors.current.accent,
            )
        }
    }
}
