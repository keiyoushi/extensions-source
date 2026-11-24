package eu.kanade.tachiyomi.multisrc.pam

import android.graphics.Bitmap
import android.graphics.Canvas
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One chapter open in the site's own reader: scrolls through it with a finger and copies each page as the reader
 * draws it on screen, from [first] on, until the app stops asking for pages.
 */
internal class ChapterCapture(
    private val url: String,
    private val first: Int,
    private val view: SiteView,
    private val dir: File,
    private val scope: CoroutineScope,
) {
    val pageCount = CompletableDeferred<Int>()

    private val pages = ConcurrentHashMap<Int, CompletableDeferred<File>>()

    /** The furthest page the app has asked for. */
    @Volatile
    private var wanted = first

    fun page(index: Int): CompletableDeferred<File> {
        wanted = maxOf(wanted, index)
        return pages.getOrPut(index) { CompletableDeferred() }
    }

    /** Whether this run has [index] or can still deliver it. */
    fun covers(index: Int) = pages[index]?.let { it.isCompleted && !it.isCancelled } == true || (index >= first && job.isActive)

    // Some WebViews only paint what would fit on the phone's own screen, so the frame is no taller
    private val frameHeight = minOf(MAX_FRAME_HEIGHT, applicationContext.resources.displayMetrics.heightPixels)

    private val job = scope.launch(Dispatchers.IO) {
        try {
            dir.mkdirs()
            run()
            synchronized(encoding) { encoding.toList() }.joinAll()
            val ended = IOException("The chapter's reader closed")
            pages.values.forEach { it.completeExceptionally(ended) }
        } catch (e: Throwable) {
            pageCount.completeExceptionally(e)
            pages.values.forEach { it.completeExceptionally(e) }
        }
    }

    fun close() {
        job.cancel()
        dir.deleteRecursively()
    }

    private suspend fun run() = view.drive(url, "(${view.asset("plates.js")})($SETTLE_MS)", FRAME_WIDTH, frameHeight, CAPTURE_TIMEOUT) { surface, snapshot ->
        step(surface, snapshot.parseAs())
    }

    private var target = first
    private var bitmap: Bitmap? = null
    private var filled = 0
    private var waitingSince: TimeMark? = null
    private var resized = false
    private var retries = 0
    private var dismissals = 0
    private val encoding = Collections.synchronizedList(mutableListOf<Job>())
    private var lastSpot: Triple<Int, Int, Float>? = null
    private var stuck = 0
    private var pending: Segment? = null

    private fun WebViewScope<Unit>.step(surface: Surface, reader: ReaderPage): Step<Unit> {
        // The reader shows placeholder plates until it has the chapter's page list, and draws pages only after
        when (reader.gate) {
            // Turnstile may pass by itself, like a Cloudflare check
            "check" -> {
                val since = waitingSince ?: TimeSource.Monotonic.markNow().also { waitingSince = it }
                if (since.elapsedNow() > CHECK_WAIT) throw IOException(SOLVE_IN_WEBVIEW)
                return Step.Wait
            }
            "locked" -> throw IOException(LOCKED)
        }
        if (!pageCount.isCompleted) {
            if (reader.plates.none { it.drawn }) return waitForDraw()
            pageCount.complete(reader.plates.size)
        }
        if (target >= reader.plates.size) return Step.Done(Unit)

        // Paused ahead of the app; ends once it stops asking
        val idle = waitingSince ?: TimeSource.Monotonic.markNow().also { waitingSince = it }
        if (target > wanted + PREFETCH) return if (idle.elapsedNow() > IDLE) Step.Done(Unit) else Step.Wait

        val plate = reader.plates[target]
        val scale = reader.scale
        // Close the site's floating ad the way a reader does; a few tries in case it comes back
        reader.dismiss?.takeIf { dismissals < MAX_DISMISSALS }?.let { close ->
            dismissals++
            return Step.Act { done ->
                tap(scale, close.x, close.y)
                later(TAP_WAIT_MS, done)
            }
        }
        val viewRows = (reader.height * scale).toInt()
        plate.retry?.takeIf { it.clear }?.let { retry ->
            if (++retries > MAX_RETRIES) throw IOException("The site's reader could not load page ${target + 1}")
            return Step.Act { done ->
                tap(scale, retry.x, retry.y)
                later(RETRY_WAIT_MS, done)
            }
        }
        if (!plate.drawn) {
            // Below the screen: bring it up, the reader draws it on the way
            if (plate.top > reader.height * 0.5f) return scroll(reader, plate, plate.top - reader.height * 0.25f)
            if (idle.elapsedNow() > DRAW_WAIT) throw IOException("The site's reader did not draw page ${target + 1}")
            return Step.Wait
        }
        // One frame pixel per canvas pixel
        if (!resized && plate.canvasWidth != surface.width) {
            resized = true
            return Step.Act { done ->
                surface.show(plate.canvasWidth, frameHeight)
                later(RESIZE_WAIT_MS, done)
            }
        }

        val left = (plate.left * scale).roundToInt()
        val width = (plate.width * scale).roundToInt()
        val top = (plate.top * scale).roundToInt()
        val height = (plate.height * scale).roundToInt()
        // The reader resized the page while it was being copied: start it again at its new size
        bitmap?.takeIf { it.width != width || it.height != height }?.let {
            it.recycle()
            bitmap = null
            pending?.bitmap?.recycle()
            pending = null
        }
        val page = bitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            bitmap = it
            filled = 0
        }
        val bands = reader.clear.map { ceil(it.top * scale).toInt() to min(floor(it.bottom * scale).toInt(), viewRows) }
        // A copied segment counts once this later read shows the page in the same place and nothing over those rows
        pending?.let { segment ->
            pending = null
            val confirmed = segment.target == target && segment.plateTop == top && segment.from == filled &&
                bands.any { (bandTop, bandBottom) -> top + segment.from >= bandTop && top + segment.until <= bandBottom }
            if (confirmed) {
                Canvas(page).drawBitmap(segment.bitmap, 0f, segment.from.toFloat(), null)
                filled = segment.until
                waitingSince = null
                retries = 0
            }
            segment.bitmap.recycle()
            if (filled >= height) {
                finishPage(page)
                return Step.Wait
            }
        }
        // Only rows nothing covers: the first missing one has to be in such a band
        val missing = top + filled
        val band = bands.firstOrNull { (bandTop, bandBottom) -> missing in bandTop until bandBottom }
        if (band == null) {
            // The same spot again after scrolling: the page can't move there
            val spot = Triple(target, filled, plate.top)
            if (spot == lastSpot && ++stuck > MAX_STUCK) throw IOException("The site's reader stopped scrolling at page ${target + 1}")
            if (spot != lastSpot) stuck = 0
            lastSpot = spot
            val widest = reader.clear.maxByOrNull { it.bottom - it.top } ?: return Step.Wait
            return scroll(reader, plate, missing / scale - widest.top - EDGE)
        }
        val until = min(height, band.second - top)
        val from = filled
        return Step.Act { done ->
            surface.copy(left, missing, left + width, top + until) { segment ->
                if (segment != null) pending = Segment(segment, target, top, from, until)
                done()
            }
        }
    }

    /** Rows [from] until [until] of page [target], copied while its top was at frame row [plateTop]. */
    private class Segment(val bitmap: Bitmap, val target: Int, val plateTop: Int, val from: Int, val until: Int)

    private fun waitForDraw(): Step<Unit> {
        val since = waitingSince ?: TimeSource.Monotonic.markNow().also { waitingSince = it }
        if (since.elapsedNow() > DRAW_WAIT) throw IOException("The site's reader did not draw the chapter")
        return Step.Wait
    }

    private fun finishPage(page: Bitmap) {
        val index = target
        target++
        bitmap = null
        val file = File(dir, "$index.jpg")
        encoding += scope.launch(Dispatchers.IO) {
            try {
                file.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                pages.getOrPut(index) { CompletableDeferred() }.complete(file)
            } catch (e: Throwable) {
                pages.getOrPut(index) { CompletableDeferred() }.completeExceptionally(e)
            } finally {
                page.recycle()
            }
        }
    }

    /** Swipes the page up by [distance] CSS pixels (down when negative), within the widest uncovered rows over [plate]. */
    private fun WebViewScope<Unit>.scroll(reader: ReaderPage, plate: ReaderPage.Plate, distance: Float): Step<Unit> {
        val band = reader.clear.maxByOrNull { it.bottom - it.top } ?: return Step.Wait
        val margin = (band.bottom - band.top) * SWIPE_MARGIN
        val reach = band.bottom - band.top - 2 * margin
        val travel = distance.coerceIn(-reach, reach)
        val from = if (travel > 0) band.bottom - margin else band.top + margin
        val x = plate.left + plate.width * X_FRACTION
        return Step.Act { done ->
            swipe(reader.scale, x, from, x, from - travel)
            later(SWIPE_WAIT_MS, done)
        }
    }

    private fun act(block: (done: () -> Unit) -> Unit) = Step.Act(block)
}

private const val SETTLE_MS = 10000
private const val FRAME_WIDTH = 720
private const val MAX_FRAME_HEIGHT = 3600
private val CAPTURE_TIMEOUT = 30.minutes
private val IDLE = 1.minutes
private val DRAW_WAIT = 1.minutes
private const val PREFETCH = 6
private const val MAX_RETRIES = 2
private const val MAX_DISMISSALS = 3
private const val TAP_WAIT_MS = 800L
private val CHECK_WAIT = 30.seconds
private const val LOCKED = "This chapter needs an account or a subscription on the site, log in via WebView"
private const val MAX_STUCK = 4
private const val EDGE = 4f
private const val SWIPE_MARGIN = 0.04f
private const val X_FRACTION = 0.2f
private const val SWIPE_WAIT_MS = 550L
private const val RETRY_WAIT_MS = 2000L
private const val RESIZE_WAIT_MS = 1500L
private const val JPEG_QUALITY = 92
