package tv.own.owntv.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import tv.own.owntv.core.theme.Season
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * glcn: the season's touches on Home (Settings → Appearance → Seasonal themes) — subtle, never over
 * the video, and still with animations off. Drawn as a few shapes and emoji: no pictures to load and
 * at most a few dozen dots a frame, so a SHIELD does not notice and a small stick barely does.
 *
 * [behind] sits under the rows (the snow, the bat, the sleigh pass behind the cards); [front] is only
 * what peeks from a corner.
 */
internal object Seasonal {

    @Composable
    fun Behind(season: Season, animated: Boolean, modifier: Modifier = Modifier) {
        if (season == Season.NONE) return
        val animate = animated && tv.own.owntv.ui.theme.animationsOn
        Box(modifier.fillMaxSize()) {
            when (season) {
                Season.HALLOWEEN -> {
                    Cobweb(Modifier.align(Alignment.TopEnd))
                    if (animate) {
                        Flyer("🦇", size = 34, everyMs = 70_000, firstMs = 6_000, durationMs = 9_000, yFraction = 0.08f, wobble = 0.05f)
                        Flyer("👻", size = 30, everyMs = 150_000, firstMs = 45_000, durationMs = 16_000, yFraction = 0.5f, wobble = 0.03f, alpha = 0.35f, reverse = true)
                    }
                }
                Season.CHRISTMAS -> {
                    Lights(animate, Modifier.align(Alignment.TopCenter))
                    if (animate) {
                        Particles(Kind.SNOW, count = 28)
                        Flyer("🦌🦌🛷", size = 26, everyMs = 180_000, firstMs = 20_000, durationMs = 14_000, yFraction = 0.06f, wobble = 0.02f, alpha = 0.8f, reverse = true)
                    }
                }
                Season.NEW_YEAR -> if (animate) Fireworks()
                Season.CARNIVAL -> if (animate) Particles(Kind.CONFETTI, count = 22)
                Season.VALENTINE -> if (animate) Particles(Kind.HEART, count = 12)
                Season.NONE -> Unit
            }
        }
    }

    @Composable
    fun Front(season: Season, animated: Boolean, modifier: Modifier = Modifier) {
        if (season != Season.HALLOWEEN || !animated || !tv.own.owntv.ui.theme.animationsOn) return
        Box(modifier.fillMaxSize()) { PeekingCat(Modifier.align(Alignment.BottomEnd)) }
    }

    // --- Pieces ---------------------------------------------------------------------------------

    /** Something that crosses the screen now and then — a bat, a ghost, the sleigh. */
    @Composable
    private fun BoxScope.Flyer(
        glyph: String,
        size: Int,
        everyMs: Long,
        firstMs: Long,
        durationMs: Int,
        yFraction: Float,
        wobble: Float,
        alpha: Float = 0.9f,
        reverse: Boolean = false,
    ) {
        var box by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
        val p = remember { Animatable(-1f) }
        LaunchedEffect(Unit) {
            delay(firstMs)
            while (true) {
                p.snapTo(0f)
                p.animateTo(1f, tween(durationMs, easing = LinearEasing))
                p.snapTo(-1f)
                delay(everyMs)
            }
        }
        Text(
            glyph,
            fontSize = size.mpxSp,
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { box = it }
                .graphicsLayer {
                    val v = p.value
                    if (v < 0f || box.width == 0) {
                        this.alpha = 0f
                        return@graphicsLayer
                    }
                    val w = box.width.toFloat()
                    val h = box.height.toFloat()
                    val x = if (reverse) w - v * (w + 200f) else -200f + v * (w + 200f)
                    translationX = x
                    translationY = h * yFraction + sin(v * PI.toFloat() * 6f) * h * wobble
                    // Fades in and out at the edges so nothing pops.
                    this.alpha = alpha * (minOf(v, 1f - v) * 8f).coerceIn(0f, 1f)
                },
        )
    }

    /** Halloween: a black cat looks up from the bottom corner now and then, blinks, and is gone. */
    @Composable
    private fun PeekingCat(modifier: Modifier) {
        val peek = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(25_000)
            while (true) {
                peek.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                delay(3_500)
                peek.animateTo(0f, tween(700))
                delay(140_000)
            }
        }
        Text(
            "🐈‍⬛",
            fontSize = 54.mpxSp,
            modifier = modifier.graphicsLayer {
                translationX = -40.mpx.toPx()
                translationY = (1f - peek.value) * 90.mpx.toPx()
                alpha = peek.value
            },
        )
    }

    /** Halloween, static: a fine cobweb in the top corner. */
    @Composable
    private fun Cobweb(modifier: Modifier) {
        Canvas(modifier.size(170.mpx)) {
            val c = Color.White.copy(alpha = 0.13f)
            val origin = Offset(size.width, 0f)
            val r = size.width
            val stroke = Stroke(width = 1.4f)
            val spokes = 6
            for (i in 0..spokes) {
                val a = (PI / 2 * i / spokes + PI / 2).toFloat()
                drawLine(c, origin, Offset(origin.x + cos(a) * r, origin.y + sin(a) * r), strokeWidth = 1.4f)
            }
            for (ring in 1..4) {
                val rr = r * ring / 4.5f
                val path = Path()
                for (i in 0..spokes) {
                    val a = (PI / 2 * i / spokes + PI / 2).toFloat()
                    val pt = Offset(origin.x + cos(a) * rr, origin.y + sin(a) * rr)
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.quadraticTo(
                        origin.x + cos(a - 0.13f) * rr * 0.9f, origin.y + sin(a - 0.13f) * rr * 0.9f, pt.x, pt.y,
                    )
                }
                drawPath(path, c, style = stroke)
            }
        }
    }

    /** Christmas: a string of small coloured lights along the top edge, twinkling when animated. */
    @Composable
    private fun Lights(animate: Boolean, modifier: Modifier) {
        var tick by remember { mutableIntStateOf(0) }
        if (animate) LaunchedEffect(Unit) { while (true) { delay(900); tick++ } }
        val colours = remember { listOf(Color(0xFFFF5A5A), Color(0xFFFFD25A), Color(0xFF5AD27A), Color(0xFF5AA8FF)) }
        Canvas(modifier.fillMaxWidth().height(26.mpx)) {
            val n = 34
            val step = size.width / n
            val sag = size.height * 0.45f
            var prev: Offset? = null
            for (i in 0..n) {
                val x = i * step
                val y = 4f + sag * sin((i % 6) / 6f * PI.toFloat())
                val pt = Offset(x, y)
                prev?.let { drawLine(Color.White.copy(alpha = 0.12f), it, pt, strokeWidth = 1.2f) }
                prev = pt
                if (i % 2 == 0) {
                    val col = colours[(i / 2) % colours.size]
                    val on = !animate || (i / 2 + tick) % 3 != 0
                    drawCircle(col.copy(alpha = if (on) 0.85f else 0.25f), radius = 4.5f, center = pt.copy(y = pt.y + 5f))
                    if (on) drawCircle(col.copy(alpha = 0.18f), radius = 11f, center = pt.copy(y = pt.y + 5f))
                }
            }
        }
    }

    private enum class Kind { SNOW, CONFETTI, HEART }

    private class Bit(var x: Float, var y: Float, val speed: Float, val drift: Float, val size: Float, val phase: Float, val colour: Color)

    /** Falling snow, confetti, or hearts rising — one canvas, redrawn by itself. */
    @Composable
    private fun Particles(kind: Kind, count: Int) {
        val rnd = remember { Random(kind.ordinal) }
        val palette = remember {
            when (kind) {
                Kind.SNOW -> listOf(Color.White)
                Kind.CONFETTI -> listOf(Color(0xFFFF5A8A), Color(0xFFFFD25A), Color(0xFF5AD2C8), Color(0xFF9A7BFF))
                Kind.HEART -> listOf(Color(0xFFFF4D6D), Color(0xFFFF8FA3))
            }
        }
        val bits = remember {
            List(count) {
                Bit(
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    speed = 0.018f + rnd.nextFloat() * 0.03f, drift = rnd.nextFloat() * 0.02f,
                    size = if (kind == Kind.HEART) 9f + rnd.nextFloat() * 7f else 2.2f + rnd.nextFloat() * 3.2f,
                    phase = rnd.nextFloat() * 6.28f, colour = palette[rnd.nextInt(palette.size)],
                )
            }
        }
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            var last = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val dt = ((now - last).coerceAtMost(64L)) / 1000f
                    last = now
                    for (b in bits) {
                        val dir = if (kind == Kind.HEART) -1f else 1f
                        b.y += dir * b.speed * dt * (if (kind == Kind.HEART) 0.6f else 1f)
                        if (b.y > 1.05f) { b.y = -0.05f; b.x = rnd.nextFloat() }
                        if (b.y < -0.05f) { b.y = 1.05f; b.x = rnd.nextFloat() }
                    }
                    frame = now
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = frame / 1000f
            for (b in bits) {
                val x = (b.x + sin(t * 0.6f + b.phase) * b.drift) * size.width
                val y = b.y * size.height
                when (kind) {
                    Kind.SNOW -> drawCircle(b.colour.copy(alpha = 0.55f), radius = b.size, center = Offset(x, y))
                    Kind.CONFETTI -> rotate(degrees = (t * 90f + b.phase * 57f) % 360f, pivot = Offset(x, y)) {
                        drawRect(b.colour.copy(alpha = 0.7f), topLeft = Offset(x - b.size, y - b.size * 0.5f), size = Size(b.size * 2f, b.size))
                    }
                    Kind.HEART -> drawPath(heart(x, y, b.size), b.colour.copy(alpha = 0.35f))
                }
            }
        }
    }

    private fun heart(cx: Float, cy: Float, s: Float) = Path().apply {
        moveTo(cx, cy + s * 0.9f)
        cubicTo(cx - s * 1.6f, cy - s * 0.2f, cx - s * 0.6f, cy - s * 1.3f, cx, cy - s * 0.4f)
        cubicTo(cx + s * 0.6f, cy - s * 1.3f, cx + s * 1.6f, cy - s * 0.2f, cx, cy + s * 0.9f)
        close()
    }

    private class Burst(val x: Float, val y: Float, val start: Long, val colour: Color)

    /** New Year's Eve: small fireworks bursting high up, one every few seconds. */
    @Composable
    private fun Fireworks() {
        val rnd = remember { Random(31) }
        val colours = remember { listOf(Color(0xFFFFD25A), Color(0xFFFF5A8A), Color(0xFF5AD2C8), Color(0xFFFFFFFF)) }
        val bursts = remember { ArrayDeque<Burst>() }
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            var next = 0L
            while (true) {
                withFrameMillis { now ->
                    if (now >= next) {
                        bursts.addLast(Burst(0.15f + rnd.nextFloat() * 0.7f, 0.08f + rnd.nextFloat() * 0.22f, now, colours[rnd.nextInt(colours.size)]))
                        if (bursts.size > 4) bursts.removeFirst()
                        next = now + 2_200L + rnd.nextLong(2_500L)
                    }
                    frame = now
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            for (b in bursts) {
                val age = (frame - b.start) / 1600f
                if (age !in 0f..1f) continue
                val cx = b.x * size.width
                val cy = b.y * size.height
                val r = 120f * (1f - (1f - age) * (1f - age))
                val a = (1f - age) * 0.8f
                for (i in 0 until 22) {
                    val ang = i / 22f * 2f * PI.toFloat()
                    drawCircle(b.colour.copy(alpha = a), radius = 2.6f, center = Offset(cx + cos(ang) * r, cy + sin(ang) * r + age * age * 40f))
                }
            }
        }
    }
}
