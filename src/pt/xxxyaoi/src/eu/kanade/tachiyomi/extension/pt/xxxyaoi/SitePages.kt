package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.network.HttpException
import keiyoushi.utils.InjectionEvent
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class SiteRead(
    val document: PageDocument,
    val imageHeaders: Map<String, Map<String, String>>,
    private val imageStarts: Map<String, ByteArray>,
) {

    /** The image urls of [document]'s pages, in page order. */
    fun pageUrls(): List<String>? {
        val unused = synchronized(imageStarts) { LinkedHashMap(imageStarts) }
        return document.pages.map { page ->
            val url = unused.entries.firstOrNull { (_, start) -> start.copyOf(page.length).sha256().toHexString() == page.hash }?.key ?: return null
            url.also(unused::remove)
        }
    }
}

internal class SitePages(
    private val baseUrl: () -> String,
    private val client: OkHttpClient,
    private val userAgent: () -> String,
) {

    private val mutex = Mutex()

    private val imageClient = client.newBuilder().addInterceptor(::recordImageStart).build()

    private var lastLoad: TimeMark? = null

    @Volatile
    private var readerSession: Map<String, String> = emptyMap()

    /** The document at [url], once `assets/[readScript]` called with [args] returns it. */
    suspend fun document(url: String, readScript: String, args: String = ""): Document = read(url, readScript, args).document.let { it.html.asJsoup(it.url) }

    /** The page at [url] opened from [referer], once `assets/[readScript]` called with [args] returns it; [carrySession] keeps the last such page's session storage. */
    suspend fun read(url: String, readScript: String, args: String = "", carrySession: Boolean = false, referer: String? = null): SiteRead = mutex.withLock {
        lastLoad?.let { delay(LOAD_GAP - it.elapsedNow()) }
        lastLoad = TimeSource.Monotonic.markNow()
        load(url, readScript, args, carrySession, referer)
    }

    @OptIn(NativeWebViewApi::class)
    private suspend fun load(url: String, readScript: String, args: String, carrySession: Boolean, referer: String?): SiteRead {
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
        val imageStarts = Collections.synchronizedMap(LinkedHashMap<String, ByteArray>())

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
                    }
                    if (requestHost == host && request.url.path.orEmpty().contains(IMAGE_PATH)) {
                        val imageUrl = request.url.toString()
                        imageHeaders[imageUrl] = request.requestHeaders.filterKeys { it.startsWith("X-", ignoreCase = true) }
                        imageStarts.putIfAbsent(imageUrl, ByteArray(0))
                        proxyRequest(request, imageClient) { tag(ImageStart::class.java, ImageStart { imageStarts[imageUrl] = it }) }
                    } else {
                        null
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException(error.description.toString()))
                }
                webMessageBridge(bridge, origins, world) { message, reply ->
                    val snapshot = message.parseAs<PageRead>()
                    if (snapshot.check) {
                        val since = checkSince ?: TimeSource.Monotonic.markNow().also { checkSince = it }
                        if (!shown) shown = showToUser(TURNSTILE_AREA)
                        if (!shown && since.elapsedNow() > CHECK_WAIT) return@webMessageBridge reject(IOException(SOLVE_IN_WEBVIEW))
                        return@webMessageBridge reply.postMessage("")
                    }
                    checkSince = null
                    if (shown) {
                        hideFromUser()
                        shown = false
                    }
                    when {
                        snapshot.status != null -> reject(snapshot.title?.ifBlank { null }?.let { IOException("$it (HTTP ${snapshot.status})") } ?: HttpException(snapshot.status))
                        snapshot.result?.reload == true && imageHeaders.isNotEmpty() -> reply.postMessage("")
                        snapshot.waitsFor(posts) -> reply.postMessage("")
                        else -> resolve(snapshot)
                    }
                }
                addJavaScriptOnEvent(script, InjectionEvent.DOCUMENT_START, world, origins)
                loadUrl(url, referer?.let { mapOf("Referer" to it) }.orEmpty())
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
        return SiteRead(result, imageHeaders, imageStarts)
    }

    private fun PageRead.waitsFor(posts: List<String>): Boolean = posts.groupingBy { it }.eachCount().any { (url, started) ->
        val ended = finished.filter { it.url == url }
        ended.size < started || ended.any { it.ago < SETTLE_MS }
    }

    private fun recordImageStart(chain: Interceptor.Chain): Response {
        val record = chain.request().tag(ImageStart::class.java) ?: return chain.proceed(chain.request())
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) record.onStart(response.peekBody(IMAGE_START_SIZE.toLong()).bytes())
        return response
    }

    private fun randomName() = "k" + ByteArray(8).also(SecureRandom()::nextBytes).toHexString()

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    companion object {
        const val SOLVE_IN_WEBVIEW = "Abra o WebView para passar pela verificação do site"

        const val IMAGE_START_SIZE = 16384

        private const val IMAGE_PATH = "/WP-manga/data/"

        private val TIMEOUT = 2.minutes

        private val LOAD_GAP = 2.seconds

        private val CHECK_WAIT = 30.seconds

        private const val SETTLE_MS = 500
    }
}

private class ImageStart(val onStart: (ByteArray) -> Unit)
