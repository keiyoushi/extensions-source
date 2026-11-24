package eu.kanade.tachiyomi.extension.es.catharsisworld

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

internal class ChapterReader(
    private val url: String,
    private val first: Int,
    val hd: Boolean,
    private val view: SiteView,
    private val dir: File,
    private val scope: CoroutineScope,
    private val pageSelector: String,
    private val textSelector: String?,
    private val refusal: (String) -> String?,
    private val readerPass: suspend () -> Unit,
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
            view.drive(
                url,
                "(${view.asset("reader.js")})(${pageSelector.toJsonString()}, ${textSelector.toJsonString()}, $hd)",
                timeout = READ_TIMEOUT,
                keep = { snapshot -> snapshot.parseAs<ReaderPage>().image?.let(::save) },
            ) { snapshot -> step(snapshot.parseAs()) }
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

    private var pagesAtPass = -1
    private var gateTaps = 0
    private var since: TimeMark = TimeSource.Monotonic.markNow()
    private var waitingFor = -1
    private var retries = 0
    private var shown = false
    private var lastState: State? = null

    private enum class State { GATE, CHECK, TURNSTILE, EMPTY, PAGES }

    private fun save(image: PageImage) {
        val file = File(dir, "${image.index}.jpg")
        file.writeBytes(Base64.decode(image.data.substringAfter(','), Base64.DEFAULT))
        done += image.index
        pages.getOrPut(image.index) { CompletableDeferred() }.complete(file)
    }

    @OptIn(NativeWebViewApi::class)
    private fun WebViewScope<Unit>.step(reader: ReaderPage): Step<Unit> {
        reader.image?.let(::save)

        val state = when {
            reader.gate != null -> State.GATE
            reader.check -> State.CHECK
            reader.turnstile -> State.TURNSTILE
            reader.pages.isEmpty() -> State.EMPTY
            else -> State.PAGES
        }
        if (state != lastState) {
            lastState = state
            since = TimeSource.Monotonic.markNow()
            waitingFor = -1
        }

        reader.gate?.let {
            if (++gateTaps > MAX_GATE_TAPS) throw IOException("Algo tapa el lector del sitio; ábrelo en WebView")
            return touch(reader.scale, reader.width, reader.height, it)
        }

        if (reader.check) {
            if (pagesAtPass != done.size) {
                pagesAtPass = done.size
                return Step.Act { done ->
                    scope.launch {
                        runCatching { readerPass() }
                        withNativeWebView { it.reload() }
                        later(RELOAD_WAIT_MS, done)
                    }
                }
            }
            if (!shown && since.elapsedNow() > CHECK_ALONE) shown = showToUser(CHECK_AREA)
            if (since.elapsedNow() > CHECK_WAIT + USER_WAIT) throw IOException(SOLVE_IN_WEBVIEW)
            return Step.Wait
        }

        if (reader.turnstile) {
            if (!shown && since.elapsedNow() > TURNSTILE_ALONE) shown = showToUser(TURNSTILE_AREA)
            if (since.elapsedNow() > CHECK_WAIT + USER_WAIT) throw IOException(SOLVE_IN_WEBVIEW)
            return Step.Wait
        }
        if (shown) {
            hideFromUser()
            shown = false
            withNativeWebView { webView ->
                val metrics = webView.resources.displayMetrics
                Surface.host(webView, metrics.widthPixels, metrics.heightPixels)
            }
        }

        if (reader.textOnly) throw IOException("Este capítulo es solo texto; léelo en WebView")

        if (reader.pages.isEmpty()) {
            reader.text?.let(refusal)?.let { throw IOException(it) }
            if (since.elapsedNow() > PAGES_WAIT) throw IOException("El lector del sitio no mostró páginas")
            return Step.Wait
        }
        if (!pageCount.isCompleted) pageCount.complete(reader.pages.size)

        val target = reader.pages.firstOrNull { it.index >= first && it.index !in done } ?: return Step.Done(Unit)
        if (target.index > wanted + PREFETCH) return if (since.elapsedNow() > IDLE) Step.Done(Unit) else Step.Wait
        if (target.index != waitingFor) {
            waitingFor = target.index
            since = TimeSource.Monotonic.markNow()
            retries = 0
        }

        // The page in view: its top in the upper part of the screen, or the screen within it
        if (target.top > reader.height * 0.3f || target.top + target.height < reader.height * 0.5f) {
            val grip = reader.grip ?: return Step.Wait
            val travel = target.top - reader.height * 0.1f
            return Step.Act { done ->
                if (travel > reader.height * FLING_SCREENS) {
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
private const val MAX_GATE_TAPS = 30
private const val MAX_RETRIES = 2
private const val RELOAD_WAIT_MS = 1500L

private const val CHECK_AREA = "div:has(> cap-widget)"
private val CHECK_ALONE = 3.seconds
private val CHECK_WAIT = 60.seconds
private val TURNSTILE_ALONE = 12.seconds
private val USER_WAIT = 2.minutes
private val PAGES_WAIT = 45.seconds
private val RETRY_WAIT = 5.seconds
private val PAGE_WAIT = 45.seconds
private val IDLE = 1.minutes
private val READ_TIMEOUT = 30.minutes
