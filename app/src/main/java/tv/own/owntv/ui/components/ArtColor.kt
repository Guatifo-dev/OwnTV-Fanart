package tv.own.owntv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import coil3.request.allowHardware
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * glcn: the colour of a picture — a fanart, a poster, a backdrop — so the screen around it can take
 * its tone, the way streaming apps tint their pages. A 24-pixel copy of the image is averaged with
 * the greys and the near-black and near-white pixels left out, then the result is darkened into a
 * shade that sits behind white text. Remembered per URL for the life of the process.
 */
object ArtColor {
    private val cache = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 200
    }

    suspend fun of(context: android.content.Context, url: String): Color? {
        synchronized(cache) { cache[url] }?.let { return Color(it) }
        val argb = withContext(Dispatchers.IO) {
            runCatching {
                val request = coil3.request.ImageRequest.Builder(context).data(url)
                    .size(24, 24)
                    .allowHardware(false)
                    .build()
                val result = coil3.SingletonImageLoader.get(context).execute(request)
                val image = (result as? coil3.request.SuccessResult)?.image ?: return@runCatching null
                val bmp = (image as? coil3.BitmapImage)?.bitmap ?: return@runCatching null
                dominant(bmp)
            }.getOrNull()
        } ?: return null
        synchronized(cache) { cache[url] = argb }
        return Color(argb)
    }

    private fun dominant(bmp: android.graphics.Bitmap): Int? {
        var r = 0L; var g = 0L; var b = 0L; var weight = 0L
        val hsv = FloatArray(3)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[2] < 0.12f || (hsv[2] > 0.95f && hsv[1] < 0.1f)) continue
            // Saturated pixels count more: a red shirt says more about the picture than grey sky.
            val w = (1 + (hsv[1] * 4)).toLong()
            r += android.graphics.Color.red(c) * w; g += android.graphics.Color.green(c) * w; b += android.graphics.Color.blue(c) * w
            weight += w
        }
        if (weight == 0L) return null
        val avg = android.graphics.Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt())
        android.graphics.Color.colorToHSV(avg, hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0.25f, 0.75f)
        hsv[2] = 0.32f
        return android.graphics.Color.HSVToColor(hsv)
    }
}

/** The tone of [url]'s picture, or null while it loads or when there is none. */
@Composable
fun rememberArtColor(url: String?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(null, url) {
        value = if (url.isNullOrBlank()) null else ArtColor.of(context, url)
    }
    return color
}

/**
 * glcn: a soft glow in the tone of [url]'s picture, drawn behind a screen's content — strongest at
 * [focusX]/[focusY] (fractions of the box) and fading out. Changes colour gently as the picture does.
 */
@Composable
fun ArtTint(
    url: String?,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    strength: Float = 0.6f,
    focusX: Float = 0.8f,
    focusY: Float = 0.1f,
) {
    val target = rememberArtColor(url)
    val tone by androidx.compose.animation.animateColorAsState(
        target ?: Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "artTint",
    )
    androidx.compose.foundation.layout.Box(
        modifier.drawBehind {
            if (tone.alpha <= 0.01f) return@drawBehind
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(tone.copy(alpha = tone.alpha * strength), tone.copy(alpha = tone.alpha * strength * 0.35f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * focusX, size.height * focusY),
                    radius = size.maxDimension * 0.85f,
                ),
            )
        },
    )
}
