package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.features.home.relativeLastWatchedLabel
import tv.own.owntv.features.live.LiveStageRow
import tv.own.owntv.features.live.ProviderTags
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.ui.components.chNavPaging
import tv.own.owntv.ui.components.jumpLazyListTo
import tv.own.owntv.ui.stage.PlaylistMark
import tv.own.owntv.ui.stage.StageKeyHints
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.stage.stageSelectedBar
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.gradientWash
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * A channel list that slides in over the playing video, as a Stage glass sheet 20 from the screen edge.
 * Two instances exist in the player: Left opens the playing channel's own provider category (anchored
 * left), Right opens the profile's watch history (anchored right, [watchedAt] set). Rows are the Live TV
 * list's own [LiveStageRow]; the playing channel carries the accent bar. Browse with the D-pad, OK
 * switches channel, Back — or pushing outwards past the list edge — closes it, all without leaving
 * full-screen. The current channel is focused first; in the history list it is the newest row, so focus
 * starts on the row after it and OK there really changes channel.
 * The remote's paging keys (CH+/CH−, rewind/fast-forward by default) page through it as in the Live list.
 */
@Composable
fun ChannelListOverlay(
    channels: List<ChannelEntity>,
    currentId: Long?,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nowProgrammes: Map<Long, XtEpgEntry> = emptyMap(),
    nowPlaying: Map<Long, String> = emptyMap(),
    favoriteIds: Set<Long> = emptySet(),
    playlistMarks: Map<Long, PlaylistMark> = emptyMap(),
    title: String? = null,
    alignEnd: Boolean = false,
    showNumbers: Boolean = true,
    /** History only: channel id → when it was last watched, shown at the row's end. */
    watchedAt: Map<Long, Long>? = null,
    onOpenCategories: (() -> Unit)? = null,
    /** glcn: the focused channel's programme on air, for the picture panel beside the list. */
    loadNow: (suspend (ChannelEntity) -> EpgProgrammeEntity?)? = null,
    /** glcn: the focused channel's coming schedule; when set it takes the picture panel's place, right beside the list. */
    loadSchedule: (suspend (ChannelEntity) -> List<EpgProgrammeEntity>)? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val dismissDirection = if (alignEnd) HorizontalDirection.END else HorizontalDirection.START
    val history = watchedAt != null
    val playingIndex = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId } }
    val currentIndex = remember(channels, playingIndex, history) {
        when {
            history && playingIndex == 0 && channels.size > 1 -> 1
            else -> playingIndex.coerceAtLeast(0)
        }
    }
    val listState = rememberLazyListState()
    val focusTarget = remember { FocusRequester() }
    // The row that owns [focusTarget]: the current channel at first, then wherever paging jumped to.
    var targetIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
    var focusedIndex by remember(currentIndex) { mutableIntStateOf(currentIndex) }
    var listFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        runCatching { listState.scrollToItem(currentIndex) }
        runCatching { focusTarget.requestFocus() }
    }
    BackHandler { onDismiss() }

    val accent = stageAccent.accent
    val shade = tv.own.owntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier = modifier
            .fillMaxSize()
            // Darken only the side the sheet is on, so the picture stays visible beside it.
            .gradientWash(
                vertical = false,
                *(if (alignEnd) arrayOf(0.38f to Color.Transparent, 0.62f to shade.copy(alpha = 0.45f), 1f to shade.copy(alpha = 0.72f))
                else arrayOf(0f to shade.copy(alpha = 0.72f), 0.38f to shade.copy(alpha = 0.45f), 0.62f to Color.Transparent)),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 20.mpx, vertical = 22.mpx)
                .fillMaxHeight()
                .width(if (history) 760.mpx else 720.mpx)
                .stageGlass(StageRadii.Sheet, overContent = true)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == dismissDirection) {
                        // Pushing outward from the Start panel opens categories when they are wired;
                        // pushing outward from the End panel closes it. Back always closes.
                        if (!alignEnd && onOpenCategories != null) onOpenCategories() else onDismiss()
                        true
                    } else false
                }
                .padding(horizontal = 16.mpx, vertical = 26.mpx),
        ) {
            // "Catch-up · 978 channels        Live TV", as the Live TV header and category sheet have it.
            Row(
                Modifier.fillMaxWidth().padding(start = 14.mpx, end = 14.mpx),
                horizontalArrangement = Arrangement.spacedBy(16.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title ?: stringResource(R.string.content_channel_overlay_title),
                        style = stageText(26, 800), color = StageColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "· " + pluralStringResource(R.plurals.content_live_channel_count, channels.size, channels.size),
                        style = stageText(18, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stringResource(R.string.common_nav_live_tv), style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val playing = channels.getOrNull(playingIndex)
            val subLine = when {
                history -> stringResource(R.string.content_history_overlay_hint)
                playing != null -> listOfNotNull(
                    stringResource(R.string.shell_now_playing),
                    playing.number?.toString()?.takeIf { showNumbers },
                    ProviderTags.parse(playing.name).name,
                ).joinToString(" · ")
                else -> null
            }
            subLine?.let {
                Text(
                    it, style = stageText(16, 400), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.mpx, end = 14.mpx, top = 4.mpx, bottom = 12.mpx),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { listFocused = it.hasFocus }
                    .chNavPaging(
                        enabled = chNavEnabled,
                        upSkip = chNavUpSkip,
                        downSkip = chNavDownSkip,
                        isFocused = { listFocused },
                        lastIndex = { channels.lastIndex },
                        currentTargetIndex = { focusedIndex },
                        onJumpToIndex = { idx ->
                            targetIndex = idx
                            scope.jumpLazyListTo(listState, idx) { focusTarget.requestFocus() }
                        },
                    ),
                // Room for the focus ring, which the list would otherwise clip.
                contentPadding = PaddingValues(horizontal = 4.mpx, vertical = 4.mpx),
                verticalArrangement = Arrangement.spacedBy(2.mpx),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                    val isPlaying = index == playingIndex
                    val nowMs = remember { System.currentTimeMillis() }
                    LiveStageRow(
                        channel = ch,
                        name = ProviderTags.parse(ch.name),
                        now = nowProgrammes[ch.id],
                        nowTitle = nowPlaying[ch.id],
                        isFavorite = ch.id in favoriteIds,
                        showNumber = showNumbers,
                        mark = playlistMarks[ch.sourceId],
                        onFocus = { focusedIndex = index },
                        onClick = { onSelect(ch) },
                        onLongClick = {},
                        trailing = watchedAt?.let { times ->
                            {
                                Text(
                                    if (isPlaying) stringResource(R.string.shell_now_playing)
                                    else times[ch.id]?.let { relativeLastWatchedLabel(it, nowMs) }.orEmpty(),
                                    style = stageText(15, if (isPlaying) 700 else 500),
                                    color = if (isPlaying) accent else StageColors.Dim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        modifier = Modifier
                            .then(if (isPlaying) Modifier.stageSelectedBar(accent, 22.mpx) else Modifier)
                            .then(if (index == targetIndex) Modifier.focusRequester(focusTarget) else Modifier),
                    )
                }
            }
            StageKeyHints(
                listOfNotNull(
                    stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
                    // Outward is ◀ on the left sheet, ▶ on the right one (Multiview's picker goes back to categories).
                    when {
                        onOpenCategories != null -> (if (alignEnd) "▶" else "◀") to stringResource(R.string.content_category_browser_title)
                        alignEnd -> "▶" to stringResource(R.string.content_close)
                        else -> null
                    },
                    "CH±" to stringResource(R.string.content_key_page),
                    stringResource(R.string.common_back) to stringResource(R.string.content_close),
                ),
                textSize = 16,
                modifier = Modifier.padding(start = 14.mpx, top = 14.mpx),
            )
        }
        if (loadSchedule != null) {
            ZapSchedulePanel(
                channel = channels.getOrNull(focusedIndex),
                loadSchedule = loadSchedule,
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(vertical = 22.mpx)
                    .padding(
                        start = if (alignEnd) 0.mpx else (20 + (if (history) 760 else 720) + 4).mpx,
                        end = if (alignEnd) (20 + (if (history) 760 else 720) + 4).mpx else 0.mpx,
                    ),
            )
        } else if (loadNow != null) {
            ZapPreviewPanel(
                channel = channels.getOrNull(focusedIndex),
                loadNow = loadNow,
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 40.mpx),
            )
        }
    }
}

/**
 * glcn: the focused channel's programme on air — picture, title, time and synopsis — opposite the
 * zapping list, so browsing shows what is on before switching. Waits a moment while the D-pad is
 * still moving so a fast scroll does not fetch every row.
 */
@Composable
private fun ZapPreviewPanel(
    channel: ChannelEntity?,
    loadNow: suspend (ChannelEntity) -> EpgProgrammeEntity?,
    modifier: Modifier = Modifier,
) {
    var programme by remember { mutableStateOf<EpgProgrammeEntity?>(null) }
    LaunchedEffect(channel?.id) {
        if (channel == null) { programme = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(180)
        programme = runCatching { loadNow(channel) }.getOrNull()
    }
    val p = programme ?: return
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    Column(
        modifier
            .width(620.mpx)
            .stageGlass(StageRadii.Sheet, overContent = true)
            .padding(18.mpx),
        verticalArrangement = Arrangement.spacedBy(8.mpx),
    ) {
        p.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            tv.own.owntv.ui.components.ProgrammeArt(
                url = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(StageRadii.Sheet)),
            )
        }
        Text(p.title, style = stageText(24, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            fmt.format(java.util.Date(p.startMs)) + " – " + fmt.format(java.util.Date(p.stopMs)),
            style = stageText(16, 500), color = StageColors.Muted, maxLines = 1,
        )
        p.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = stageText(16, 400), color = StageColors.Dim, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * glcn: the focused channel's coming schedule in a column right beside the list — now, then the next
 * hours — so browsing reads like a small guide. Waits a moment while the D-pad is still moving.
 */
@Composable
private fun ZapSchedulePanel(
    channel: ChannelEntity?,
    loadSchedule: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    modifier: Modifier = Modifier,
) {
    var rows by remember { mutableStateOf<List<EpgProgrammeEntity>>(emptyList()) }
    LaunchedEffect(channel?.id) {
        if (channel == null) { rows = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(180)
        rows = runCatching { loadSchedule(channel) }.getOrDefault(emptyList())
    }
    val ch = channel ?: return
    if (rows.isEmpty()) return
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val now = remember(rows) { System.currentTimeMillis() }
    val accent = stageAccent.accent
    Column(
        modifier
            .fillMaxHeight()
            .width(560.mpx)
            .stageGlass(StageRadii.Sheet, overContent = true)
            .padding(horizontal = 22.mpx, vertical = 26.mpx),
        verticalArrangement = Arrangement.spacedBy(4.mpx),
    ) {
        Text(
            stringResource(R.string.glcn_zap_schedule_title, ProviderTags.parse(ch.name).name),
            style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.mpx),
        )
        rows.forEach { p ->
            val onAir = now in p.startMs until p.stopMs
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.mpx))
                    .background(if (onAir) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 12.mpx, vertical = 9.mpx),
                horizontalArrangement = Arrangement.spacedBy(14.mpx),
            ) {
                Text(
                    fmt.format(java.util.Date(p.startMs)),
                    style = stageText(17, 700), color = if (onAir) accent else StageColors.Muted, maxLines = 1,
                    modifier = Modifier.width(70.mpx),
                )
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = stageText(18, if (onAir) 800 else 600), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (onAir) {
                        val span = (p.stopMs - p.startMs).coerceAtLeast(1L)
                        val f = ((now - p.startMs).toFloat() / span).coerceIn(0f, 1f)
                        Box(Modifier.padding(top = 6.mpx).fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background(StageColors.Ink.copy(alpha = 0.12f))) {
                            Box(Modifier.fillMaxWidth(f).height(4.mpx).background(accent))
                        }
                        p.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = stageText(15, 400), color = StageColors.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.mpx))
                        }
                    }
                }
            }
        }
    }
}
