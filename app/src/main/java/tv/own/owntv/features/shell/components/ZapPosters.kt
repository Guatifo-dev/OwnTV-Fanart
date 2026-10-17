package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: the channel list over the player as picture cards — what each channel shows now, with its
 * programme picture — along the foot of the screen, so the choice is made by what is on rather than by
 * name. ◀ ▶ move, OK switches, ▲ goes back to the list view, Back closes. Pictures are read a window
 * at a time around the cards on screen.
 */
@Composable
fun ZapPosterOverlay(
    channels: List<ChannelEntity>,
    currentId: Long?,
    title: String?,
    loadNow: suspend (List<ChannelEntity>) -> Map<Long, EpgProgrammeEntity>,
    onSelect: (ChannelEntity) -> Unit,
    onListView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceAtLeast(0))
    val first = remember { FocusRequester() }
    val programmes = remember(channels) { mutableStateMapOf<Long, EpgProgrammeEntity?>() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler { onDismiss() }
    // A window of pictures around what is on screen, once per stop.
    LaunchedEffect(channels) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { at ->
            val window = channels.subList((at - 2).coerceAtLeast(0), (at + 10).coerceAtMost(channels.size))
                .filter { it.id !in programmes }
            if (window.isEmpty()) return@collect
            val got = runCatching { loadNow(window) }.getOrDefault(emptyMap())
            window.forEach { programmes[it.id] = got[it.id] }
        }
    }
    // Opened by holding OK, the same OK is still down — and its repeats and release would land on the
    // focused card and pick it at once, closing the strip. OK counts only from a fresh press.
    var okArmed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier.fillMaxSize().onPreviewKeyEvent { e ->
            val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (!ok) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) okArmed = true
            !okArmed
        },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to shade.copy(alpha = 0.78f), 1f to shade.copy(alpha = 0.92f)))
                .padding(top = 90.mpx, bottom = 40.mpx)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { onListView(); true } else false
                },
        ) {
            Row(
                Modifier.padding(start = 64.mpx, end = 64.mpx, bottom = 14.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title ?: stringResource(R.string.content_channel_overlay_title),
                    style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "· " + pluralStringResource(R.plurals.content_live_channel_count, channels.size, channels.size),
                    style = stageText(18, 500), color = StageColors.Muted, maxLines = 1,
                )
                Text(
                    "▲ " + stringResource(R.string.glcn_zap_view_list),
                    style = stageText(16, 600), color = StageColors.Dim, maxLines = 1,
                    modifier = Modifier.padding(start = 20.mpx),
                )
            }
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(20.mpx),
                contentPadding = PaddingValues(start = 64.mpx, end = 64.mpx, top = 10.mpx, bottom = 10.mpx),
                modifier = Modifier.focusGroup(),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, ch ->
                    tv.own.owntv.features.home.ArtLiveCard(
                        channel = ch,
                        programme = programmes[ch.id],
                        now = now,
                        onClick = { onSelect(ch) },
                        width = 360.mpx,
                        modifier = if (index == start) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}
