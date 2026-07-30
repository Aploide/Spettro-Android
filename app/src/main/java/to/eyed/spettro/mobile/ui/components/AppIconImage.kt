package to.eyed.spettro.mobile.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.eyed.spettro.mobile.ui.theme.SpettroTheme

/**
 * The app's own launcher icon as a view — the brand image for empty states
 * and the pairing intro (port of AppIconImage.swift, which digs the primary
 * icon out of the bundle the same way).
 */
@Composable
fun AppIconImage(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val bitmap: ImageBitmap? = remember {
        if (isPreview) null else runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val px = (context.resources.displayMetrics.density * size.value).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Spettro",
            modifier = modifier
                .size(size)
                // The adaptive icon drawable is unmasked; round it like the launcher does.
                .clip(RoundedCornerShape(size * 0.22f)),
        )
    }
}

@Preview
@Composable
private fun AppIconImagePreview() {
    SpettroTheme {
        AppIconImage(size = 72.dp)
    }
}
