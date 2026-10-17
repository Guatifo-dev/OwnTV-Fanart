package tv.own.owntv.glcn

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: "Skip intro" for series. Each series learns where its intro ends: the first time the viewer
 * jumps forward early in an episode (with this button or by fast-forwarding) and keeps watching from
 * there, that moment is remembered for the series, and later episodes offer — or, with Auto-skip on,
 * make — the same jump. Before anything is learned the button jumps a typical intro length.
 */
object IntroSkip {
    /** How far the very first "Skip intro" of a series jumps — a typical opening. */
    const val DEFAULT_JUMP_MS = 80_000L
    /** Early in an episode is where an intro can be. */
    const val WINDOW_MS = 8 * 60_000L
    /** Before anything is learned the button shows from here… */
    const val SHOW_FROM_MS = 10_000L
    /** …until here. */
    const val SHOW_UNTIL_MS = 4 * 60_000L
    /** A jump forward of more than this counts as skipping. */
    private const val JUMP_MS = 15_000L
    /** Watching on this long after a jump confirms where the intro ended. */
    private const val SETTLE_MS = 12_000L

    private fun prefs(c: Context) = c.getSharedPreferences("glcn_intro", Context.MODE_PRIVATE)
    fun key(series: String) = series.trim().lowercase()
    fun learned(c: Context, series: String): Long = prefs(c).getLong(key(series), -1L)
    fun learn(c: Context, series: String, endMs: Long) { prefs(c).edit().putLong(key(series), endMs).apply() }

    /**
     * Watches [engine] while an episode plays: learns jumps, and returns whether the button is up.
     * [controlsHidden] — the button stands in for the controls, never over them.
     */
    @Composable
    fun Chip(engine: PlaybackEngine, controlsHidden: Boolean, autoSkip: Boolean, onVisible: (Boolean) -> Unit, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val meta by engine.currentMeta.collectAsStateWithLifecycle()
        val series = meta.subtitle?.takeIf { meta.episodeNumber != null && it.isNotBlank() }
        val position by engine.position.collectAsStateWithLifecycle()
        var learnedEnd by remember(series) { mutableLongStateOf(series?.let { learned(context, it) } ?: -1L) }
        var autoDone by remember(meta) { mutableStateOf(false) }

        // Learning: a forward jump early in the episode, then watching on, marks the intro's end.
        var last by remember(meta) { mutableLongStateOf(-1L) }
        var candidate by remember(meta) { mutableLongStateOf(-1L) }
        LaunchedEffect(position, series) {
            if (series == null) return@LaunchedEffect
            val prev = last
            last = position
            if (prev > 3_000L && position - prev > JUMP_MS && prev < WINDOW_MS && position < WINDOW_MS + DEFAULT_JUMP_MS) {
                candidate = position
            }
        }
        LaunchedEffect(candidate) {
            if (candidate < 0 || series == null) return@LaunchedEffect
            val at = candidate
            delay(SETTLE_MS)
            if (candidate == at && position >= at) {
                learn(context, series, at)
                learnedEnd = at
            }
        }

        val inWindow = series != null && when {
            learnedEnd > 0 -> position in 2_000L until (learnedEnd - 3_000L)
            else -> position in SHOW_FROM_MS until SHOW_UNTIL_MS
        }
        // Auto-skip: once per episode, only to a learned end.
        LaunchedEffect(inWindow, autoSkip, learnedEnd) {
            if (autoSkip && inWindow && learnedEnd > 0 && !autoDone) {
                autoDone = true
                engine.seekBy(learnedEnd - position)
            }
        }
        val visible = inWindow && controlsHidden && !(autoSkip && learnedEnd > 0)
        LaunchedEffect(visible) { onVisible(visible) }
        Box(modifier) {
            AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd)) {
                Row(
                    Modifier
                        .padding(end = 64.mpx, bottom = 72.mpx)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.mpx))
                        .border(2.mpx, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.mpx))
                        .padding(horizontal = 26.mpx, vertical = 14.mpx),
                ) {
                    Text(stringResource(R.string.glcn_skip_intro) + "   OK", style = stageText(22, 800), color = Color.White)
                }
            }
        }
    }

    /** The button's action: to the learned end, or a typical intro length on. */
    fun skip(context: Context, engine: PlaybackEngine) {
        val series = engine.currentMeta.value.subtitle ?: return
        val end = learned(context, series)
        val pos = engine.position.value
        engine.seekBy(if (end > pos) end - pos else DEFAULT_JUMP_MS)
    }
}
