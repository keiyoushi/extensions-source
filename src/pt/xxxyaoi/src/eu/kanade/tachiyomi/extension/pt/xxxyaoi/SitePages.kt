package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.util.Base64
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.network.HttpException
import keiyoushi.utils.InjectionEvent
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class SiteRead(val document: PageDocument, val imageHeaders: Map<String, Map<String, String>>)

internal class SitePages(
    private val baseUrl: () -> String,
    private val userAgent: () -> String,
) {

    private val mutex = Mutex()

    private var lastLoad: TimeMark? = null

    @Volatile
    private var readerSession: Map<String, String> = emptyMap()

    /** The document at [url], once `assets/[readScript]` called with [args] returns it. */
    suspend fun document(url: String, readScript: String, args: String = ""): Document = read(url, readScript, args).document.let { it.html.asJsoup(it.url) }

    /** The page at [url], once `assets/[readScript]` called with [args] returns it; [carrySession] keeps the last such page's session storage. */
    suspend fun read(url: String, readScript: String, args: String = "", carrySession: Boolean = false): SiteRead = mutex.withLock {
        lastLoad?.let { delay(LOAD_GAP - it.elapsedNow()) }
        lastLoad = TimeSource.Monotonic.markNow()
        load(url, readScript, args, carrySession)
    }

    @OptIn(NativeWebViewApi::class)
    private suspend fun load(url: String, readScript: String, args: String, carrySession: Boolean): SiteRead {
        val host = baseUrl().toHttpUrl().host
        val origins = setOf("https://$host")
        val bridge = randomName()
        val world = randomName()
        val read = "(${asset("world.js")})(\"$bridge\", (${asset("page.js")})((${asset(readScript)})($args)))"
        val script = if (carrySession) "(${asset("session.js")})(${readerSession.toJsonString()}), $read" else read
        var checkSince: TimeMark? = null
        var shown = false
        val posts = CopyOnWriteArrayList<String>()
        val imageHeaders = ConcurrentHashMap<String, Map<String, String>>()

        val page = try {
            runWebView<PageRead>(timeout = TIMEOUT) {
                userAgent = this@SitePages.userAgent()
                setOriginMatchedHeader("X-Requested-With", "", origins)
                withNativeWebView { webView ->
                    val metrics = webView.resources.displayMetrics
                    Surface.host(webView, metrics.widthPixels, metrics.heightPixels)
                }
                interceptRequest { request ->
                    val requestHost = request.url.host
                    when {
                        request.isForMainFrame -> posts.clear()
                        request.method == "POST" && requestHost == host -> posts += request.url.toString()
                        requestHost != null && requestHost.isHostOrSubdomainOf(host) && request.url.path.orEmpty().contains(IMAGE_PATH) ->
                            imageHeaders[request.url.toString()] = request.requestHeaders.filterKeys { it.startsWith("X-", ignoreCase = true) && !it.equals("X-Requested-With", ignoreCase = true) }
                    }
                    when {
                        requestHost == null || requestHost.isHostOrSubdomainOf(host) || requestHost.isHostOrSubdomainOf(TURNSTILE_HOST) || requestHost == CDNJS_HOST -> null
                        else -> stub(request.url.lastPathSegment.orEmpty())
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException(error.description.toString()))
                }
                webMessageBridge(bridge, origins, world) { message, reply ->
                    val snapshot = message.parseAs<PageRead>()
                    if (snapshot.check) {
                        val since = checkSince ?: TimeSource.Monotonic.markNow().also { checkSince = it }
                        if (!shown && since.elapsedNow() > SHOW_AFTER) shown = showToUser(TURNSTILE_AREA)
                        if (!shown && since.elapsedNow() > CHECK_WAIT) return@webMessageBridge reject(IOException(SOLVE_IN_WEBVIEW))
                        return@webMessageBridge reply.postMessage("")
                    }
                    checkSince = null
                    when {
                        snapshot.status != null -> reject(HttpException(snapshot.status))
                        snapshot.waitsFor(posts) -> reply.postMessage("")
                        else -> resolve(snapshot)
                    }
                }
                addJavaScriptOnEvent(script, InjectionEvent.DOCUMENT_START, world, origins)
                loadUrl(url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: WebViewTimeoutException) {
            throw IOException(if (checkSince != null) SOLVE_IN_WEBVIEW else "A página do site não terminou de carregar", e)
        } catch (e: UnsupportedOperationException) {
            throw IOException("Atualize o Android System WebView para ler este site", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        val result = page.result!!
        if (carrySession) readerSession = result.session
        return SiteRead(result, imageHeaders)
    }

    private fun PageRead.waitsFor(posts: List<String>): Boolean = posts.groupingBy { it }.eachCount().any { (url, started) ->
        val ended = finished.filter { it.url == url }
        ended.size < started || ended.any { it.ago < SETTLE_MS }
    }

    private fun randomName() = "k" + ByteArray(8).also(SecureRandom()::nextBytes).toHexString()

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun stub(file: String): WebResourceResponse {
        val (type, body) = when (file.substringAfterLast('.', "").lowercase()) {
            "css" -> "text/css" to "\n".toByteArray()
            "gif", "png", "jpg", "jpeg", "webp", "svg" -> "image/gif" to PIXEL
            else -> "application/javascript" to "\n".toByteArray()
        }
        return WebResourceResponse(type, "utf-8", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(body))
    }

    private fun String.isHostOrSubdomainOf(domain: String) = this == domain || endsWith(".$domain")

    companion object {
        const val SOLVE_IN_WEBVIEW = "Abra o WebView para passar pela verificação do site"

        private const val TURNSTILE_HOST = "challenges.cloudflare.com"

        private const val CDNJS_HOST = "cdnjs.cloudflare.com"

        private const val IMAGE_PATH = "/WP-manga/data/"

        private val PIXEL = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)

        private val TIMEOUT = 2.minutes

        private val LOAD_GAP = 2.seconds

        private val SHOW_AFTER = 8.seconds

        private val CHECK_WAIT = 30.seconds

        private const val SETTLE_MS = 500
    }
}
