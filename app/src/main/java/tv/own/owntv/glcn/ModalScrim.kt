package tv.own.owntv.glcn

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * glcn: the modal wash behind our own small menus ("Remove from Continue watching" and the like): the
 * screen behind stays readable, the menu in front. Upstream dropped its own copy with the old dialog
 * panel, so the fork keeps this one.
 */
@Composable
fun Modifier.modalScrim(strength: Float = 1f): Modifier {
    val s = strength.coerceIn(0f, 1.35f)
    return background(
        if (tv.own.owntv.ui.theme.LocalStagePalette.current.isLight) Color(20, 32, 28).copy(alpha = (0.30f * s).coerceAtMost(0.40f))
        else Color(2, 5, 6).copy(alpha = (0.55f * s).coerceAtMost(0.72f)),
    )
}
