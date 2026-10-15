package tv.own.owntv.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.home.FeaturedItem
import tv.own.owntv.core.home.TonightItem
import tv.own.owntv.core.live.SportsNow
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.ProgrammeArt
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.backdropLeftWash
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.StageRadii
import tv.own.owntv.ui.theme.dissolveEdges
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/**
 * glcn: Home's renewed pieces — the featured billboard, live cards drawn from the programme's
 * picture, "Tonight", the greeting, and the backdrop that follows the focused card.
 */

/** A card tells Home which picture it shows when it takes focus; Home paints it behind the rows. */
internal val LocalHomeFocusArt = staticCompositionLocalOf<(String?) -> Unit> { {} }

/** The focused card's picture, top right, dissolving into the page, with the page tinted to match. */
@Composable
internal fun HomeFocusBackdrop(url: String?, modifier: Modifier = Modifier) {
    var settled by remember { mutableStateOf(url) }
    LaunchedEffect(url) { if (url != settled) { delay(160); settled = url } }
    Box(modifier.fillMaxSize()) {
        tv.own.owntv.ui.components.ArtTint(settled, Modifier.fillMaxSize(), strength = 0.55f)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.6f)
                .height(560.mpx)
                .dissolveEdges(start = 0.45f, bottom = 0.45f),
        ) {
            Crossfade(targetState = settled, label = "homeFocusArt") { u ->
                if (!u.isNullOrBlank()) {
                    AsyncImage(
                        model = u, contentDescription = null, contentScale = ContentScale.Crop,
                        alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(Modifier.fillMaxSize().backdropLeftWash(0f to 0.85f, 0.35f to 0.45f, 0.7f to 0.15f, 1f to 0f))
        }
    }
}

/** "Buenas noches, Carlos" and the time, above the first row. */
@Composable
internal fun HomeGreeting(name: String?, start: Dp) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val hour = remember(now) { java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 6..13 -> R.string.glcn_greeting_morning
            in 14..20 -> R.string.glcn_greeting_afternoon
            else -> R.string.glcn_greeting_evening
        },
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val date = remember(now) { java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(java.util.Date(now)) }
    Column(Modifier.padding(start = start, bottom = 6.mpx)) {
        Text(
            listOfNotNull(greeting, name?.takeIf { it.isNotBlank() }).joinToString(", "),
            style = stageText(34, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            fmt.format(java.util.Date(now)) + "  ·  " + date.replaceFirstChar { it.uppercase() },
            style = stageText(18, 500), color = StageColors.Muted, maxLines = 1,
        )
    }
}

/** "EN DIRECTO" in a red pill. */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.glcn_live_badge),
        style = stageText(13, 800),
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.mpx))
            .background(Color(0xFFE5243B))
            .padding(horizontal = 10.mpx, vertical = 3.mpx),
    )
}

/**
 * A live programme as a picture card: the programme's fanart filling the card, the channel logo in the
 * corner, title and time over a dark foot, a progress bar — or the channel logo alone when the guide
 * has no picture. [startLabel] replaces the time left for an upcoming programme ("21:30").
 */
@Composable
internal fun ArtLiveCard(
    channel: ChannelEntity,
    programme: EpgProgrammeEntity?,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = 400.mpx,
    startLabel: String? = null,
    bell: Boolean = false,
) {
    val a = stageAccent
    val setArt = LocalHomeFocusArt.current
    val art = programme?.imageUrl?.takeIf { it.isNotBlank() }
    StageSurface(
        onClick = onClick,
        radius = StageRadii.Row,
        modifier = modifier
            .size(width, width * 9f / 16f)
            .onFocusChanged { if (it.hasFocus) setArt(art) },
        onLongClick = onLongClick,
        focusStyle = StageFocus.FX,
        idle = Modifier.background(StageColors.Ink.copy(alpha = 0.06f), RoundedCornerShape(StageRadii.Row)),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(StageRadii.Row))) {
            if (art != null) {
                ProgrammeArt(url = art, modifier = Modifier.fillMaxSize())
            } else {
                ChannelPlate(channel, Modifier.align(Alignment.Center).size(150.mpx, 96.mpx))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f)),
                ),
            )
            if (art != null) ChannelPlate(channel, Modifier.padding(12.mpx).size(66.mpx, 44.mpx))
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.mpx),
                horizontalArrangement = Arrangement.spacedBy(8.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (programme != null && startLabel == null && SportsNow.isLive(programme)) LiveBadge()
                if (bell) {
                    Box(
                        Modifier.size(34.mpx).clip(RoundedCornerShape(17.mpx)).background(a.accent),
                        contentAlignment = Alignment.Center,
                    ) { tv.own.owntv.ui.components.OwnTVIcon(OwnTVIcon.BELL, Color.White, Modifier.size(20.mpx)) }
                }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.mpx, vertical = 12.mpx)) {
                Text(
                    programme?.title ?: channel.name,
                    style = stageText(20, 800), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val line = when {
                    startLabel != null -> "$startLabel · ${channel.name}"
                    programme != null -> channel.name + " · " + timeLeftText(((programme.stopMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L)
                    else -> null
                }
                line?.let {
                    Text(it, style = stageText(15, 500), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (programme != null && startLabel == null) {
                    val span = (programme.stopMs - programme.startMs).coerceAtLeast(1L)
                    val p = ((now - programme.startMs).toFloat() / span).coerceIn(0f, 1f)
                    Spacer(Modifier.height(8.mpx))
                    Box(Modifier.fillMaxWidth().height(4.mpx).clip(RoundedCornerShape(2.mpx)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(p).height(4.mpx).background(Brush.horizontalGradient(listOf(a.accent, a.focus))))
                    }
                }
            }
        }
    }
}

/** A live rail drawn as picture cards ("On now" mode of Recent, Favourite channels and Sports now). */
@Composable
internal fun ArtLiveRow(
    title: String,
    channels: List<ChannelEntity>,
    programmes: Map<Long, List<EpgProgrammeEntity>>,
    now: Long,
    onChannelClick: (Long, List<ChannelEntity>) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    Column(modifier) {
        HomeRowHeader(title, small = stringResource(R.string.home_row_on_now), start = start)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                ArtLiveCard(
                    channel = channel,
                    programme = programmes[channel.id].orEmpty().firstOrNull { now in it.startMs until it.stopMs },
                    now = now,
                    onClick = { onChannelClick(channel.id, channels) },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}

/** "Tonight": what the user's channels show from 21:00; OK sets or clears a reminder, long-press tunes. */
@Composable
internal fun TonightRow(
    title: String,
    items: List<TonightItem>,
    reminded: (TonightItem) -> Boolean,
    onToggleReminder: (TonightItem) -> Unit,
    onOpenChannel: (TonightItem) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val now = remember(items) { System.currentTimeMillis() }
    Column(modifier) {
        HomeRowHeader(title, small = stringResource(R.string.content_remind_me), start = start)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, t -> "${t.channel.id}:${t.programme.startMs}" }) { index, item ->
                ArtLiveCard(
                    channel = item.channel,
                    programme = item.programme,
                    now = now,
                    startLabel = fmt.format(java.util.Date(item.programme.startMs)),
                    bell = reminded(item),
                    onClick = { onToggleReminder(item) },
                    onLongClick = { onOpenChannel(item) },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}

/**
 * The featured billboard: one big picture at a time — a match on now, a favourite on air, the newest
 * film or show — with its title, a line about it and "Watch now". ◀ ▶ step through; it turns on its own
 * every few seconds while nobody is on it.
 */
@Composable
internal fun FeaturedBillboard(
    items: List<FeaturedItem>,
    onPlayChannel: (ChannelEntity) -> Unit,
    onOpenMovie: (tv.own.owntv.core.database.entity.MovieEntity) -> Unit,
    onOpenSeries: (tv.own.owntv.core.database.entity.SeriesEntity) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
    start: Dp,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val item = items[index.coerceIn(0, items.lastIndex)]
    // glcn: live slides never play by themselves — the billboard only shows pictures.
    LaunchedEffect(items, focused, index) {
        if (items.size < 2) return@LaunchedEffect
        delay(if (focused) 12_000 else 8_000)
        index = (index + 1) % items.size
    }
    val setArt = LocalHomeFocusArt.current
    val a = stageAccent
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    Box(
        modifier
            .fillMaxWidth()
            .height(560.mpx)
            .padding(start = start, end = 64.mpx),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.mpx))) {
            Crossfade(targetState = item.art, label = "billboard") { url ->
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.55f to Color.Black.copy(alpha = 0.25f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 48.mpx, bottom = 40.mpx).widthIn(max = 900.mpx),
            verticalArrangement = Arrangement.spacedBy(10.mpx),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                when (item) {
                    is FeaturedItem.Live -> {
                        if (item.isLiveEvent) LiveBadge()
                        ChannelPlate(item.channel, Modifier.size(60.mpx, 40.mpx))
                        Text(item.channel.name, style = stageText(18, 700), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                    }
                    is FeaturedItem.Movie -> Text(stringResource(R.string.glcn_recent_movies).uppercase(), style = stageText(15, 800), color = a.accent)
                    is FeaturedItem.Series -> Text(stringResource(R.string.glcn_recent_series).uppercase(), style = stageText(15, 800), color = a.accent)
                }
            }
            Text(
                when (item) {
                    is FeaturedItem.Live -> item.programme.title
                    is FeaturedItem.Movie -> item.movie.name
                    is FeaturedItem.Series -> item.series.name
                },
                style = stageText(52, 800), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val line = when (item) {
                is FeaturedItem.Live -> fmt.format(java.util.Date(item.programme.startMs)) + " – " + fmt.format(java.util.Date(item.programme.stopMs))
                is FeaturedItem.Movie -> listOfNotNull(item.movie.year?.toString(), item.movie.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
                is FeaturedItem.Series -> listOfNotNull(item.series.year?.toString(), item.series.rating?.let { "★ %.1f".format(it) }).joinToString("  ·  ")
            }
            if (line.isNotBlank()) Text(line, style = stageText(19, 600), color = Color.White.copy(alpha = 0.8f))
            val plot = when (item) {
                is FeaturedItem.Live -> item.programme.description
                is FeaturedItem.Movie -> item.movie.plot
                is FeaturedItem.Series -> item.series.plot
            }
            plot?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = stageText(17, 400), color = Color.White.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.owntv.ui.stage.StageButton(
                    text = stringResource(R.string.glcn_watch_now),
                    icon = OwnTVIcon.PLAY,
                    iconFilled = true,
                    onClick = {
                        when (item) {
                            is FeaturedItem.Live -> onPlayChannel(item.channel)
                            is FeaturedItem.Movie -> onOpenMovie(item.movie)
                            is FeaturedItem.Series -> onOpenSeries(item.series)
                        }
                    },
                    modifier = Modifier
                        .then(if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged {
                            focused = it.hasFocus
                            if (it.hasFocus) { onFocus(); setArt(null) }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionRight -> { index = (index + 1) % items.size; true }
                                Key.DirectionLeft -> if (index > 0) { index -= 1; true } else false
                                else -> false
                            }
                        },
                )
                // Where in the run of slides this one is.
                Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == index) 28.mpx else 10.mpx, 10.mpx)
                                .clip(RoundedCornerShape(5.mpx))
                                .background(if (i == index) a.accent else Color.White.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}
