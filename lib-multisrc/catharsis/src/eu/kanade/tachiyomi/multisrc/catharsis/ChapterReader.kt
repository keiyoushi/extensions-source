package eu.kanade.tachiyomi.multisrc.catharsis

import android.util.Base64
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One chapter open in the site's own reader: scrolls through it and keeps each page as the reader draws it, from
 * [first] on, until the app stops asking for pages.
 */
internal class ChapterReader(
    private val url: String,
    private val first: Int,
    private val view: SiteView,
    private val dir: File,
    scope: CoroutineScope,
    private val pageSelector: String,
    private val textSelector: String?,
    private val refusal: (String) -> String?,
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

    private val job = scope.launch(Dispatchers.IO) {
        try {
            dir.mkdirs()
            view.drive(url, "(${view.asset("reader.js")})(${pageSelector.toJsonString()}, ${textSelector.toJsonString()})", timeout = READ_TIMEOUT) { snapshot -> step(snapshot.parseAs()) }
            val ended = IOException("El lector del capítulo se cerró")
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

    private val done = mutableSetOf<Int>()
    private var checkTaps = 0
    private var gateTaps = 0
    private var since: TimeMark = TimeSource.Monotonic.markNow()
    private var waitingFor = -1
    private var retries = 0
    private var shown = false
    private var lastState: State? = null

    private enum class State { GATE, CHECK, TURNSTILE, EMPTY, PAGES }

    @OptIn(NativeWebViewApi::class)
    private fun WebViewScope<Unit>.step(reader: ReaderPage): Step<Unit> {
        reader.image?.let { image ->
            val file = File(dir, "${image.index}.jpg")
            file.writeBytes(Base64.decode(image.data.substringAfter(','), Base64.DEFAULT))
            done += image.index
            pages.getOrPut(image.index) { CompletableDeferred() }.complete(file)
        }

        // Each wait below counts from when the reader got to its current state
        val state = when {
            reader.gate != null -> State.GATE
            reader.check != null -> State.CHECK
            reader.turnstile -> State.TURNSTILE
            reader.pages.isEmpty() -> State.EMPTY
            else -> State.PAGES
        }
        if (state != lastState) {
            lastState = state
            since = TimeSource.Monotonic.markNow()
            waitingFor = -1
        }

        // A question the site asks before the reader, such as its age check
        reader.gate?.let {
            // Ads that keep coming back after being closed: the reader is stuck behind them
            if (++gateTaps > MAX_GATE_TAPS) throw IOException("Algo tapa el lector del sitio; ábrelo en WebView")
            return touch(reader.scale, reader.width, reader.height, it)
        }

        // The site's proof-of-work check, started by its checkbox
        reader.check?.let { check ->
            if (checkTaps < MAX_CHECK_TAPS && since.elapsedNow() > CHECK_TAP_WAIT * checkTaps) {
                checkTaps++
                return touch(reader.scale, reader.width, reader.height, check)
            }
            if (since.elapsedNow() > CHECK_WAIT) throw IOException(SOLVE_IN_WEBVIEW)
            return Step.Wait
        }

        if (reader.turnstile) {
            // Shown to the user only when it doesn't pass by itself
            if (!shown && since.elapsedNow() > TURNSTILE_ALONE) shown = showToUser(TURNSTILE_AREA)
            if (since.elapsedNow() > CHECK_WAIT + USER_WAIT) throw IOException(SOLVE_IN_WEBVIEW)
            return Step.Wait
        }
        if (shown) {
            hideFromUser()
            shown = false
            // Back on a display of its own, so the reader keeps drawing pages
            withNativeWebView { webView ->
                val metrics = webView.resources.displayMetrics
                Surface.host(webView, metrics.widthPixels, metrics.heightPixels)
            }
        }

        if (reader.textOnly) throw IOException("Este capítulo es solo texto; léelo en WebView")

        if (reader.pages.isEmpty()) {
            // A chapter the site doesn't show this reader says why in place of the pages
            reader.text?.let(refusal)?.let { throw IOException(it) }
            if (since.elapsedNow() > PAGES_WAIT) throw IOException("El lector del sitio no mostró páginas")
            return Step.Wait
        }
        if (!pageCount.isCompleted) pageCount.complete(reader.pages.size)

        val target = reader.pages.firstOrNull { it.index >= first && it.index !in done } ?: return Step.Done(Unit)
        // Paused ahead of the app; ends once it stops asking
        if (target.index > wanted + PREFETCH) return if (since.elapsedNow() > IDLE) Step.Done(Unit) else Step.Wait
        if (target.index != waitingFor) {
            waitingFor = target.index
            since = TimeSource.Monotonic.markNow()
            retries = 0
        }

        // The page in view: its top in the upper part of the screen, or the screen within it
        if (target.top > reader.height * 0.3f || target.top + target.height < reader.height * 0.5f) {
            // Only where a finger would land on the pages, not on an ad over them
            val grip = reader.grip ?: return Step.Wait
            val travel = target.top - reader.height * 0.1f
            return Step.Act { done ->
                if (travel > reader.height * FLING_SCREENS) {
                    // Far below: skim towards it
                    fling(reader.scale, grip.x, grip.y, grip.y - reader.height * 0.6f)
                    later(FLING_WAIT_MS, done)
                } else {
                    val distance = travel.coerceIn(-reader.height * 0.6f, reader.height * 0.6f)
                    swipe(reader.scale, grip.x, grip.y, grip.x, grip.y - distance)
                    later(SWIPE_WAIT_MS, done)
                }
            }
        }
        if (target.ready) return Step.Wait

        // The reader's own retry button on a page that failed
        target.retry?.takeIf { retries < MAX_RETRIES && since.elapsedNow() > RETRY_WAIT }?.let { retry ->
            retries++
            since = TimeSource.Monotonic.markNow()
            return touch(reader.scale, reader.width, reader.height, retry)
        }
        if (since.elapsedNow() > PAGE_WAIT) throw IOException("La página ${target.index + 1} no cargó")
        return Step.Wait
    }
}

private const val PREFETCH = 4
private const val MAX_CHECK_TAPS = 2
private const val MAX_GATE_TAPS = 30
private const val MAX_RETRIES = 2
private val CHECK_TAP_WAIT = 20.seconds
private val CHECK_WAIT = 60.seconds
private val TURNSTILE_ALONE = 8.seconds
private val USER_WAIT = 2.minutes
private val PAGES_WAIT = 45.seconds
private val RETRY_WAIT = 5.seconds
private val PAGE_WAIT = 45.seconds
private val IDLE = 1.minutes
private val READ_TIMEOUT = 30.minutes
