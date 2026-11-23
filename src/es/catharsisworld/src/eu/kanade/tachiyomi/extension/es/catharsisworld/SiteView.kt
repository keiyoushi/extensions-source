package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.webkit.WebResourceResponse
import keiyoushi.utils.InjectionEvent
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What a page reader does after looking at the page. */
sealed interface Step<out T> {
    data object Wait : Step<Nothing>

    class Done<T>(val value: T) : Step<T>

    /** A native action on the WebView; the page is read again once it calls `done`. */
    class Act(val run: (done: () -> Unit) -> Unit) : Step<Nothing>
}

class SiteView(
    private val baseUrl: () -> String,
    private val prelude: String,
    private val userAgent: () -> String,
) {

    private val pacing = Mutex()

    private var lastLoad: TimeMark? = null

    @Volatile
    private var refusedAt: TimeMark? = null

    /**
     * Loads [url] and calls [step] with what [read] (a script giving the function that reads the page, which returns null
     * until the page is ready) reads from it, until a step is done. A read that comes while a step's action runs only
     * goes to [keep].
     */
    @OptIn(NativeWebViewApi::class)
    suspend fun <T> drive(
        url: String,
        read: String,
        timeout: Duration = WEBVIEW_TIMEOUT,
        keep: (JsonObject) -> Unit = {},
        step: WebViewScope<T>.(JsonObject) -> Step<T>,
    ): T {
        pacing.withLock {
            if (refusedAt?.let { it.elapsedNow() < BACKOFF } == true) throw IOException("El sitio rechazó la última petición; inténtalo en unos minutos")
            lastLoad?.let { delay(LOAD_INTERVAL - it.elapsedNow()) }
            lastLoad = TimeSource.Monotonic.markNow()
        }

        val siteOrigins = setOf("https://${baseUrl().toHttpUrl().host}")
        val bridge = randomName()
        val world = randomName()
        val reads = "(${asset("world.js")})(\"$bridge\", (${asset("page.js")})($read))"
        val script = "(${asset(prelude)})(), $reads"
        var challengeSince: TimeMark? = null
        var obstacles = 0
        var loaded = false
        val result = try {
            runWebView<T>(timeout = timeout) {
                userAgent = this@SiteView.userAgent()
                setOriginMatchedHeader("X-Requested-With", "", siteOrigins)
                withNativeWebView { webView ->
                    webView.settings.setSupportMultipleWindows(true)
                    webView.settings.javaScriptCanOpenWindowsAutomatically = false
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false
                    val metrics = webView.resources.displayMetrics
                    Surface.host(webView, metrics.widthPixels, metrics.heightPixels)
                }
                val page = url.toHttpUrl().encodedPath
                interceptRequest { request ->
                    val host = request.url.host
                    when {
                        request.isForMainFrame && host != null && request.url.encodedPath != page -> blockedResponse()
                        else -> null
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException(error.description.toString()))
                }
                var acting = false
                webMessageBridge(bridge, siteOrigins, world) { message, reply ->
                    if (acting) {
                        message.parseAs<PageRead>().result?.let(keep)
                        return@webMessageBridge
                    }
                    val page = message.parseAs<PageRead>()
                    when {
                        page.challenge -> {
                            val since = challengeSince ?: TimeSource.Monotonic.markNow().also { challengeSince = it }
                            if (since.elapsedNow() > CHALLENGE_WAIT) return@webMessageBridge reject(IOException(SOLVE_IN_WEBVIEW))
                            reply.postMessage("")
                        }
                        page.obstacle != null -> {
                            if (++obstacles > MAX_OBSTACLES) return@webMessageBridge reject(IOException("Algo tapa la página del sitio; ábrela en WebView"))
                            acting = true
                            tap(page.obstacle.scale, page.obstacle.x, page.obstacle.y)
                            later(OBSTACLE_WAIT_MS) {
                                acting = false
                                reply.postMessage("")
                            }
                        }
                        page.status != null -> {
                            if (page.status == 403 || page.status == 429) refusedAt = TimeSource.Monotonic.markNow()
                            reject(IOException("Error HTTP ${page.status}"))
                        }
                        else -> {
                            challengeSince = null
                            loaded = true
                            when (val next = step(page.result!!)) {
                                Step.Wait -> reply.postMessage("")
                                is Step.Done -> resolve(next.value)
                                is Step.Act -> {
                                    acting = true
                                    next.run {
                                        acting = false
                                        reply.postMessage("")
                                    }
                                }
                            }
                        }
                    }
                }
                addJavaScriptOnEvent(script, InjectionEvent.DOCUMENT_START, world, siteOrigins)
                loadUrl(url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: WebViewTimeoutException) {
            if (challengeSince != null) throw IOException(SOLVE_IN_WEBVIEW, e)
            throw IOException(if (loaded) "La página del sitio no terminó de cargar" else "La página del sitio no cargó; ábrela en WebView", e)
        } catch (e: UnsupportedOperationException) {
            throw IOException("Actualiza Android System WebView para leer este sitio", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        return result
    }

    private fun randomName() = "k" + ByteArray(8).also(SecureRandom()::nextBytes).toHexString()

    fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun blockedResponse() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
}

private const val MAX_OBSTACLES = 30
private const val OBSTACLE_WAIT_MS = 1200L
private val LOAD_INTERVAL = 2.seconds
private val BACKOFF = 5.minutes
private val CHALLENGE_WAIT = 30.seconds
private val WEBVIEW_TIMEOUT = 60.seconds
internal const val SOLVE_IN_WEBVIEW = "Abre WebView para pasar la verificación del sitio"
