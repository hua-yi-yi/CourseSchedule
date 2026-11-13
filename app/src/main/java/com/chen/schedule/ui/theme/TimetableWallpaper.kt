package com.chen.schedule.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.chen.schedule.util.BackgroundFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TimetableWallpaper(
    val bitmap: ImageBitmap,
    val tint: Color
)

@Composable
internal fun rememberTimetableWallpaper(path: String?, revision: Long): TimetableWallpaper? =
    key(path) {
        var wallpaper by remember { mutableStateOf<TimetableWallpaper?>(null) }
        LaunchedEffect(revision) {
            wallpaper = withContext(Dispatchers.IO) {
                val bitmap = BackgroundFileManager.loadBitmap(path) ?: return@withContext null
                // Favor the center so that the crop's discarded edges have less influence.
                var red = 0
                var green = 0
                var blue = 0
                for (row in 0 until 10) {
                    for (column in 0 until 10) {
                        val x = (bitmap.width * (0.275f + column * 0.05f)).toInt()
                        val y = (bitmap.height * (0.275f + row * 0.05f)).toInt()
                        val pixel = bitmap.getPixel(x, y)
                        red += android.graphics.Color.red(pixel)
                        green += android.graphics.Color.green(pixel)
                        blue += android.graphics.Color.blue(pixel)
                    }
                }
                TimetableWallpaper(
                    bitmap = bitmap.asImageBitmap(),
                    tint = Color(red / 25500f, green / 25500f, blue / 25500f)
                )
            }
        }
        wallpaper
    }

@Composable
internal fun TimetableWallpaperImage(
    wallpaper: TimetableWallpaper,
    dim: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        Image(
            bitmap = wallpaper.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        if (dim > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}
