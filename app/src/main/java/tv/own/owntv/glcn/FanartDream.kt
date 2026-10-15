package tv.own.owntv.glcn

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.live.NowArt
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

/**
 * glcn: the SHIELD's screensaver. While the TV sits idle it shows, one after another, the pictures of
 * what is on now on the favourite channels and of the films and series most recently added to the
 * library (Emby), each with its title — the house's own gallery instead of stock photos.
 *
 * Plain Views (a dream is not a Compose host), two image layers crossfading every [SLIDE_MS].
 */
class FanartDream : DreamService() {

    private data class Slide(val url: String, val title: String, val subtitle: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var front: ImageView
    private lateinit var back: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        fun image() = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            root.addView(this, FrameLayout.LayoutParams(-1, -1))
        }
        back = image()
        front = image()
        root.addView(
            View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.BL_TR,
                    intArrayOf(Color.argb(230, 0, 0, 0), Color.argb(90, 0, 0, 0), Color.TRANSPARENT),
                )
            },
            FrameLayout.LayoutParams(-1, -1),
        )
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 48).toInt()
            setPadding(pad, pad, pad, pad)
        }
        title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(12f, 0f, 2f, Color.argb(160, 0, 0, 0))
            maxLines = 2
        }
        subtitle = TextView(this).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 18f
            maxLines = 1
        }
        text.addView(title)
        text.addView(subtitle)
        root.addView(text, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.START))
        // The time in the corner, as on an Apple TV's aerial screensaver.
        val pad = (resources.displayMetrics.density * 40).toInt()
        root.addView(
            android.widget.TextClock(this).apply {
                setTextColor(Color.argb(220, 255, 255, 255))
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(12f, 0f, 2f, Color.argb(160, 0, 0, 0))
                setPadding(pad, pad, pad, pad)
            },
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END),
        )
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scope.launch {
            val slides = runCatching { withContext(Dispatchers.IO) { loadSlides() } }.getOrDefault(emptyList())
            if (slides.isEmpty()) return@launch
            var i = 0
            while (true) {
                val s = slides[i % slides.size]
                val bmp = withContext(Dispatchers.IO) { load(s.url) }
                if (bmp != null) show(bmp, s)
                i++
                delay(SLIDE_MS)
            }
        }
    }

    override fun onDreamingStopped() {
        scope.cancel()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private fun show(bmp: Bitmap, s: Slide) {
        back.setImageBitmap(bmp)
        back.animate().alpha(1f).setDuration(FADE_MS).start()
        front.animate().alpha(0f).setDuration(FADE_MS).start()
        // A slow push-in on the incoming picture.
        back.scaleX = 1.0f; back.scaleY = 1.0f
        back.animate().scaleX(1.06f).scaleY(1.06f).setDuration(SLIDE_MS).start()
        title.text = s.title
        subtitle.text = s.subtitle
        val t = front; front = back; back = t
    }

    private suspend fun load(url: String): Bitmap? = runCatching {
        val request = coil3.request.ImageRequest.Builder(this).data(url).build()
        val result = coil3.SingletonImageLoader.get(this).execute(request)
        ((result as? coil3.request.SuccessResult)?.image as? coil3.BitmapImage)?.bitmap
    }.getOrNull()

    private suspend fun loadSlides(): List<Slide> {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val sourceDao = koin.get<SourceDao>()
        val channelDao = koin.get<ChannelDao>()
        val movieDao = koin.get<MovieDao>()
        val seriesDao = koin.get<SeriesDao>()
        val nowArt = koin.get<NowArt>()
        val pid = settings.activeProfileId.first()
        if (pid < 0) return emptyList()
        val out = ArrayList<Slide>()
        val fmt = android.text.format.DateFormat.getTimeFormat(this)
        // What is on now on the favourite channels.
        val liveIds = activeSourceIds(settings, sourceDao, pid, MediaType.LIVE).toSet()
        val favourites = channelDao.favoritesListAlpha(pid).first().filter { it.sourceId in liveIds }.take(60)
        val now = nowArt.many(favourites)
        favourites.forEach { ch ->
            val p = now[ch.id] ?: return@forEach
            val url = p.imageUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Slide(url, p.title, ch.name + " · " + fmt.format(java.util.Date(p.startMs)) + " – " + fmt.format(java.util.Date(p.stopMs)))
        }
        // The newest films and series in the library.
        val movieIds = activeSourceIds(settings, sourceDao, pid, MediaType.MOVIE)
        if (movieIds.isNotEmpty()) movieDao.recentlyAdded(movieIds, 25).forEach { m ->
            val url = m.backdropUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Slide(url, m.name, listOfNotNull(m.year?.toString(), m.rating?.let { "★ %.1f".format(it) }).joinToString(" · "))
        }
        val seriesIds = activeSourceIds(settings, sourceDao, pid, MediaType.SERIES)
        if (seriesIds.isNotEmpty()) seriesDao.recentlyAdded(seriesIds, 15).forEach { s ->
            val url = s.backdropUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            out += Slide(url, s.name, "")
        }
        return out.shuffled()
    }

    private companion object {
        const val SLIDE_MS = 12_000L
        const val FADE_MS = 1_200L
    }
}
