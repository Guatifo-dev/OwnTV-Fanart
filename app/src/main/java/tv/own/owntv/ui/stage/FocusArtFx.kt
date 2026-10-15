package tv.own.owntv.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * glcn: the focused card's picture comes alive — it drifts slowly closer (a Ken Burns push-in) and a
 * soft band of light crosses it once as focus lands. Only the focused card animates, so a rail of a
 * hundred posters costs what one does; with animations off in Settings it stays still.
 */
@Composable
internal fun BoxScope.FocusArtFx(focused: Boolean, content: @Composable BoxScope.() -> Unit) {
    val animate = tv.own.owntv.ui.theme.animationsOn
    val zoom by animateFloatAsState(
        targetValue = if (focused && animate) 1.08f else 1f,
        animationSpec = tween(if (focused) 6_000 else 400, easing = LinearOutSlowInEasing),
        label = "artZoom",
    )
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused && animate) {
            shine.snapTo(-0.6f)
            shine.animateTo(1.6f, tween(900, easing = FastOutSlowInEasing))
        } else shine.snapTo(-1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = zoom; scaleY = zoom }
            .drawWithContent {
                drawContent()
                val s = shine.value
                if (s > -0.9f && s < 1.5f) {
                    val x = size.width * s
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent),
                            start = Offset(x - size.width * 0.35f, 0f),
                            end = Offset(x + size.width * 0.05f, size.height),
                        ),
                    )
                }
            },
        content = content,
    )
}
