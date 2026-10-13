package eu.kanade.tachiyomi.multisrc.pam

import android.webkit.WebResourceResponse
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class SitePage(
    val page: JsonObject,
    val fetched: JsonElement? = null,
    val chapters: List<ChapterListItem>? = null,
    val reader: ReaderResult? = null,
    val pageHeaders: Map<String, String> = emptyMap(),
) {
    fun fetched() = fetched ?: throw IOException("Missing API response")
}

/** Loads the site's pages in a WebView, one at a time, as its own readers do, and reads the site's data from them. */
internal class SitePages(
    baseUrl: String,
    private val client: () -> OkHttpClient,
    private val userAgent: () -> String,
) {
    private val baseHttpUrl = baseUrl.toHttpUrl()

    private val mutex = Mutex()

    private var lastLoad: TimeMark? = null

    private class Failure(val at: TimeMark, val challengeClearance: String?)

    /** Set when the site blocked or challenged us; loads fail fast for [BACKOFF], or until a challenge is passed in WebView. */
    @Volatile
    private var failure: Failure? = null

    private fun clearance() = client().cookieJar.loadForRequest(baseHttpUrl).firstOrNull { it.name == "cf_clearance" }?.value

    private fun failed(challenge: Boolean) {
        failure = Failure(TimeSource.Monotonic.markNow(), if (challenge) clearance().orEmpty() else null)
    }

    private fun Failure.isActive(): Boolean {
        if (at.elapsedNow() >= BACKOFF) return false
        return challengeClearance == null || clearance().let { it == null || it == challengeClearance }
    }

    /** Loads [url] and reads its Inertia page data; with [reader], also the reader v2 manifest, page key and page request headers. */
    suspend fun load(url: String, request: SiteRequest = SiteRequest(), reader: Boolean = false): SitePage = mutex.withLock {
        if (failure?.isActive() == true) throw IOException(SOLVE_IN_WEBVIEW)
        lastLoad?.let { delay(LOAD_INTERVAL - it.elapsedNow()) }
        lastLoad = TimeSource.Monotonic.markNow()

        val bridge = "k" + ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val readScript = "(${asset("site.js")})(\"$bridge\", ${request.toJsonString()})"
        val readerScript = if (reader) "(${asset("reader.js")})(\"$bridge\")" else null
        var site: SitePage? = null
        var challengePolls = 0
        val released = CountDownLatch(1)
        val result = try {
            runWebView<SitePage>(timeout = WEBVIEW_TIMEOUT) {
                userAgent = this@SitePages.userAgent()
                blockImages = true
                val lock = Any()
                var readerV2 = false
                var readerResult: ReaderResult? = null
                val pageRequests = mutableListOf<Pair<String, Map<String, String>>>()
                fun resolveWhenReady() {
                    val page = site ?: return
                    if (!readerV2) return resolve(page)
                    val captured = readerResult ?: return
                    val pageHeaders = pageRequests.firstOrNull { (path, _) -> path.startsWith(captured.manifest.base) }?.second ?: return
                    resolve(SitePage(page.page, reader = captured, pageHeaders = pageHeaders))
                }
                // Site host and Turnstile only; reader page requests stay open until the WebView closes
                interceptRequest { webRequest ->
                    val requestUrl = webRequest.url
                    val requestHost = requestUrl.host
                    if (requestHost != null && requestHost != baseHttpUrl.host && requestHost != TURNSTILE_HOST) return@interceptRequest blockedResponse()
                    val path = requestUrl.path.orEmpty()
                    if (UNUSED_PATHS.any(path::startsWith)) return@interceptRequest blockedResponse()
                    // Styles and fonts only for the reader's fingerprint
                    if (!reader && STYLE_EXTENSIONS.any(path::endsWith)) return@interceptRequest emptyResponse()
                    if (!reader || !path.startsWith("/p/")) return@interceptRequest null
                    synchronized(lock) {
                        pageRequests += path to webRequest.requestHeaders
                        resolveWhenReady()
                    }
                    heldResponse(released)
                }
                jsBridge(bridge) { posted ->
                    val message = posted.parseAs<SiteMessage>()
                    synchronized(lock) {
                        when {
                            message.reader != null -> readerResult = message.reader
                            message.error != null -> {
                                if (message.status == 403 || message.status == 429) failed(challenge = false)
                                return@jsBridge reject(IOException(message.error))
                            }
                            else -> {
                                val page = message.page ?: return@jsBridge reject(IOException("Missing page data"))
                                readerV2 = reader && page.parseAs<PageListResponse>().props.readerV2
                                site = SitePage(page, message.fetched, message.chapters)
                            }
                        }
                        resolveWhenReady()
                    }
                }
                if (readerScript != null) {
                    onPageStarted { evaluateJs(readerScript) }
                    onPageFinished { evaluateJs(readerScript) }
                }
                onReceivedError { webRequest, error ->
                    if (webRequest.isForMainFrame) reject(IOException(error.description.toString()))
                }
                // Challenges that pass by themselves take a few seconds; the rest need the user
                poll(POLL_INTERVAL) {
                    evaluateJs(readScript) {
                        if (it != "\"challenge\"") {
                            challengePolls = 0
                        } else if (POLL_INTERVAL * ++challengePolls > CHALLENGE_WAIT) {
                            failed(challenge = true)
                            reject(IOException(SOLVE_IN_WEBVIEW))
                        }
                    }
                }
                loadUrl(url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: WebViewTimeoutException) {
            if (challengePolls > 0) {
                failed(challenge = true)
                throw IOException(SOLVE_IN_WEBVIEW, e)
            }
            val message = if (site != null) "The site's reader did not load, try opening the chapter in WebView" else "The site's page did not load, try opening it in WebView"
            throw IOException(message, e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        } finally {
            released.countDown()
        }
        failure = null
        result
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    /** A response that never sends its body before [released]. */
    private fun heldResponse(released: CountDownLatch) = WebResourceResponse(
        "application/octet-stream",
        null,
        200,
        "OK",
        emptyMap(),
        object : InputStream() {
            override fun read(): Int {
                try {
                    released.await(WEBVIEW_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                }
                return -1
            }
        },
    )

    // Empty stylesheet, not an error
    private fun emptyResponse() = WebResourceResponse("text/css", null, 200, "OK", null, ByteArrayInputStream(ByteArray(0)))

    private fun blockedResponse() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
}

private const val TURNSTILE_HOST = "challenges.cloudflare.com"
private val POLL_INTERVAL = 250.milliseconds
private val LOAD_INTERVAL = 2.seconds
private val BACKOFF = 5.minutes
private val CHALLENGE_WAIT = 15.seconds
private val WEBVIEW_TIMEOUT = 60.seconds

// Comment stickers, Cloudflare's prefetch hints, the favicon and the site's analytics
private val UNUSED_PATHS = listOf("/api/v1/stickers", "/cdn-cgi/speculation", "/favicon.ico", "/gmetrics/")
private val STYLE_EXTENSIONS = listOf(".css", ".woff2", ".woff", ".ttf")
private const val SOLVE_IN_WEBVIEW = "Open in WebView to pass the site's verification"
