package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.features.home.timeLeftText
import tv.own.owntv.features.shell.components.VodCinematicBackdrop
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageLiveBadge
import tv.own.owntv.ui.stage.StageProgress
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.mpxSp
import tv.own.owntv.ui.theme.stageText

/*
 * glcn fork — Live TV "Cinematic" layout (Settings › Live TV layout › Cinematic).
 *
 * Top: the hero — the preview video on the left, the focused programme's picture (XMLTV
 * <programme><icon>) behind everything on the right, and its title, times, progress, details and
 * synopsis. Below: the channels as one row of picture cards (what is on now, LIVE, the channel, the
 * time and progress), each with the next two programmes under it.
 *
 * LiveScreen owns the state and the focus plumbing (the card modifiers carry its requesters and key
 * handling, the row shares its LazyListState), so restore / reveal / CH± keep working unchanged.
 */

private const val CARD_W = 400
private const val CARD_H = 225
private const val WINDOW_HOURS = 6L

@Composable
internal fun LiveCinematicPane(
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    previewChannel: ChannelEntity?,
    nowNext: EpgNowNext?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    loadProgrammes: suspend (List<ChannelEntity>, Long, Long) -> Map<Long, List<EpgProgrammeEntity>>,
    header: @Composable () -> Unit,
    cardModifier: (index: Int, channel: ChannelEntity) -> Modifier,
    onFocus: (ChannelEntity) -> Unit,
    onClick: (ChannelEntity) -> Unit,
    onLongClick: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The guide window for the channels around the visible cards, refreshed on the minute.
    var programmes by remember { mutableStateOf<Map<Long, List<EpgProgrammeEntity>>>(emptyMap()) }
    var loadedMinute by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(60_000 - (t % 60_000))
            now = System.currentTimeMillis()
        }
    }
    val visible by remember(listState) {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            (items.firstOrNull()?.index ?: 0) to (items.lastOrNull()?.index ?: 0)
        }
    }
    LaunchedEffect(visible, channels.itemCount, now) {
        delay(120)
        val minute = now / 60_000
        val snapshot = channels.itemSnapshotList
        val (first, last) = visible
        val from = (first - 3).coerceAtLeast(0)
        val to = (last + 8).coerceAtMost(snapshot.size - 1)
        if (to < from) return@LaunchedEffect
        val want = (from..to).mapNotNull { snapshot.getOrNull(it) }.filter { loadedMinute[it.id] != minute }
        if (want.isEmpty()) return@LaunchedEffect
        val found = runCatching { loadProgrammes(want, now - 60_000, now + WINDOW_HOURS * 3_600_000) }.getOrNull()
            ?: return@LaunchedEffect
        programmes = programmes + want.associate { it.id to found[it.id].orEmpty() }
        loadedMinute = loadedMinute + want.associate { it.id to minute }
    }

    val focusedProgrammes = previewChannel?.let { programmes[it.id] }.orEmpty()
    val heroArt = nowNext?.nowDetails?.imageUrl
        ?: focusedProgrammes.firstOrNull { now in it.startMs until it.stopMs }?.imageUrl

    Box(modifier.fillMaxSize()) {
        // The picture fills the top right and dissolves into the page, as Movies' Cinematic does.
        VodCinematicBackdrop(heroArt, series = true, Modifier.align(Alignment.TopEnd))

        LiveCinematicHero(
            channel = previewChannel,
            nowNext = nowNext,
            art = heroArt,
            previewEngine = previewEngine,
            showVideo = showVideo,
            singleSessionBlocked = singleSessionBlocked,
            modifier = Modifier.padding(start = 64.mpx, top = 40.mpx, end = 64.mpx),
        )

        Column(Modifier.padding(top = 470.mpx)) {
            Box(Modifier.padding(start = 64.mpx, end = 64.mpx)) { header() }
            Spacer(Modifier.height(18.mpx))
            if (channels.itemCount == 0) {
                Text(
                    stringResource(R.string.content_no_channels_here),
                    style = stageText(20, 500), color = StageColors.Muted,
                    modifier = Modifier.padding(start = 64.mpx, top = 40.mpx),
                )
            } else {
                CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(26.mpx),
                        contentPadding = PaddingValues(start = 64.mpx, end = 64.mpx, top = 14.mpx, bottom = 14.mpx),
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                            contentType = channels.itemContentType { "card" },
                        ) { index ->
                            val channel = channels[index]
                            if (channel != null) {
                                val rows = programmes[channel.id].orEmpty()
                                val current = rows.firstOrNull { now in it.startMs until it.stopMs }
                                val next = rows.filter { it.startMs >= (current?.stopMs ?: now) }.take(2)
                                Column(Modifier.width(CARD_W.mpx)) {
                                    LiveChannelCard(
                                        channel = channel,
                                        programme = current,
                                        now = now,
                                        onClick = { onClick(channel) },
                                        onLongClick = { onLongClick(channel) },
                                        modifier = cardModifier(index, channel)
                                            .onFocusChanged { if (it.hasFocus) onFocus(channel) },
                                    )
                                    Spacer(Modifier.height(14.mpx))
                                    next.forEach { p ->
                                        UpNextBox(p)
                                        Spacer(Modifier.height(8.mpx))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The hero: the preview video, then the programme's title, channel, time, progress, details and synopsis. */
@Composable
private fun LiveCinematicHero(
    channel: ChannelEntity?,
    nowNext: EpgNowNext?,
    art: String?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(44.mpx)) {
        LiveCinematicVideo(
            channel = channel,
            art = art,
            previewEngine = previewEngine,
            showVideo = showVideo,
            singleSessionBlocked = singleSessionBlocked,
            modifier = Modifier.size(640.mpx, 360.mpx),
        )
        val now = nowNext?.now
        Column(Modifier.width(980.mpx).padding(top = 6.mpx)) {
            if (channel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    LivePlate(channel.displayLogoUrl, 72.mpx, 52.mpx)
                    Column {
                        Text(
                            ProviderTags.parse(channel.name).name, style = stageText(22, 700), color = StageColors.Text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        channel.number?.let {
                            Text(it.toString(), style = stageText(16, 600), color = StageColors.Muted, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(16.mpx))
            }
            if (now != null) {
                Text(
                    now.title, style = stageText(44, 800, (-0.5).mpxSp), color = StageColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detailsLine(now, nowNext.nowDetails),
                    style = stageText(19, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.mpx, bottom = 14.mpx),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    StageProgress(progressOf(now), Modifier.width(420.mpx))
                    Text(timeLeftText(minutesLeft(now)), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1)
                }
                now.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, style = stageText(19, 400).copy(lineHeight = (19 * 1.5f).mpxSp), color = StageColors.Body,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 16.mpx),
                    )
                }
            }
        }
    }
}

/** The 16:9 preview: the programme's picture (or the logo) until the video plays over it. */
@Composable
private fun LiveCinematicVideo(
    channel: ChannelEntity?,
    art: String?,
    previewEngine: tv.own.owntv.player.LivePreviewEngine,
    showVideo: Boolean,
    singleSessionBlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by previewEngine.state.collectAsStateWithLifecycle()
    val playing = showVideo && state != tv.own.owntv.player.LivePreviewEngine.State.ERROR &&
        state != tv.own.owntv.player.LivePreviewEngine.State.IDLE
    val loading = showVideo && state == tv.own.owntv.player.LivePreviewEngine.State.LOADING
    Box(
        modifier.clip(RoundedCornerShape(24.mpx)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (!art.isNullOrBlank()) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (channel != null) {
            ChannelLogoTile(logoUrl = channel.displayLogoUrl, modifier = Modifier.size(220.mpx), fill = Color.Transparent) {
                OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(56.mpx))
            }
        }
        if (playing) tv.own.owntv.player.ExoPreviewSurface(engine = previewEngine, modifier = Modifier.fillMaxSize())
        if (loading) OwnTVSpinner(sizeDp = 28)
        if (channel != null) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 18.mpx, top = 16.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
        }
        if (singleSessionBlocked && !playing) {
            Text(
                stringResource(R.string.content_preview_single_stream),
                style = stageText(15, 700), color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.mpx)).padding(horizontal = 12.mpx, vertical = 6.mpx),
            )
        }
    }
}

/** One channel: the picture of what is on, LIVE, the logo, the title, the channel, the time and progress. */
@Composable
private fun LiveChannelCard(
    channel: ChannelEntity,
    programme: EpgProgrammeEntity?,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatTime = rememberSystemTimeFormatter()
    val shape = RoundedCornerShape(18.mpx)
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = 18.mpx,
        focusStyle = StageFocus.POSTER,
        modifier = modifier.size(CARD_W.mpx, CARD_H.mpx),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(Modifier.fillMaxSize().clip(shape).background(Color(0xFF15191C))) {
            val art = programme?.imageUrl
            if (!art.isNullOrBlank()) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                ChannelLogoTile(
                    logoUrl = channel.displayLogoUrl,
                    modifier = Modifier.align(Alignment.Center).size(150.mpx, 100.mpx),
                    fill = Color.Transparent,
                ) { OwnTVIcon(OwnTVIcon.LIVE_TV, tint = StageColors.Dim, modifier = Modifier.size(44.mpx)) }
            }
            // Bottom shade so the text reads on any picture.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f)),
                ),
            )
            Box(Modifier.align(Alignment.TopStart).padding(12.mpx)) {
                LivePlate(channel.displayLogoUrl, 64.mpx, 44.mpx)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(12.mpx)) { StageLiveBadge(stringResource(R.string.player_live)) }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.mpx, end = 16.mpx, bottom = 12.mpx)) {
                Text(
                    programme?.title ?: ProviderTags.parse(channel.name).name,
                    style = stageText(21, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ProviderTags.parse(channel.name).name, style = stageText(15, 600), color = Color(0xFFD5DDDA),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (programme != null) {
                    Text(
                        stringResource(R.string.content_live_time_range_plain, formatTime(programme.startMs), formatTime(programme.stopMs)),
                        style = stageText(15, 600), color = Color(0xFFD5DDDA), maxLines = 1,
                    )
                    Spacer(Modifier.height(6.mpx))
                    val span = (programme.stopMs - programme.startMs).coerceAtLeast(1)
                    StageProgress(((now - programme.startMs).toFloat() / span).coerceIn(0f, 1f), Modifier.fillMaxWidth(), flat = true)
                }
            }
        }
    }
}

/** What follows, under a card: "13:16 – 14:46 · 90 min", the title, then the details the feed carries. */
@Composable
private fun UpNextBox(p: EpgProgrammeEntity, width: Dp = CARD_W.mpx) {
    val formatTime = rememberSystemTimeFormatter()
    val minutes = ((p.stopMs - p.startMs) / 60_000).toInt()
    val details = p.details
    val bits = listOfNotNull(
        details.year?.toString(),
        details.rating,
    ) + details.categoryList.take(2)
    Column(
        Modifier
            .width(width)
            .background(StageColors.Ink.copy(alpha = 0.06f), RoundedCornerShape(12.mpx))
            .padding(horizontal = 14.mpx, vertical = 9.mpx),
    ) {
        Text(
            stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)) +
                stringResource(R.string.content_epg_bits_separator) + stringResource(R.string.player_duration_minutes, minutes),
            style = stageText(14, 600), color = StageColors.Muted, maxLines = 1,
        )
        Text(p.title, style = stageText(17, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (bits.isNotEmpty()) {
            Text(bits.joinToString(stringResource(R.string.content_epg_bits_separator)), style = stageText(14, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

