package eu.kanade.tachiyomi.multisrc.pam

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
internal sealed interface Step<out T> {
    data object Wait : Step<Nothing>

    class Done<T>(val value: T) : Step<T>

    /** A native action on the WebView; the page is read again once it calls `done`. */
    class Act(val run: (done: () -> Unit) -> Unit) : Step<Nothing>
}

/**
 * Opens the site's pages in a WebView shown on its own display, reads what they render and acts on them only with
 * touches, so nothing is added to the page.
 */
internal class SiteView(
    baseUrl: String,
    private val userAgent: () -> String,
) {
    private val baseHttpUrl = baseUrl.toHttpUrl()

    // The reader marks its pages with an image from the www host
    private val siteOrigins = setOf("${baseHttpUrl.scheme}://${baseHttpUrl.host}")

    private val allowedHosts = setOf(baseHttpUrl.host, "www.${baseHttpUrl.host.removePrefix("www.")}", TURNSTILE_HOST)

    private val pacing = Mutex()

    private var lastLoad: TimeMark? = null

    /** Set when the site refused or rate limited us; loads fail fast for [BACKOFF]. */
    @Volatile
    private var refusedAt: TimeMark? = null

    /**
     * Loads [url] on a [width] x [height] display (the screen's when null) and calls [step] with what [read] (a call of
     * an asset script, returning null until the page is ready) reads from it, until a step is done.
     */
    @OptIn(NativeWebViewApi::class)
    suspend fun <T> drive(
        url: String,
        read: String,
        width: Int? = null,
        height: Int? = null,
        timeout: Duration = WEBVIEW_TIMEOUT,
        step: WebViewScope<T>.(Surface, JsonObject) -> Step<T>,
    ): T {
        pacing.withLock {
            if (refusedAt?.let { it.elapsedNow() < BACKOFF } == true) throw IOException("The site refused the last request, try again in a few minutes")
            lastLoad?.let { delay(LOAD_INTERVAL - it.elapsedNow()) }
            lastLoad = TimeSource.Monotonic.markNow()
        }

        val bridge = randomName()
        val world = randomName()
        val script = "(${asset("world.js")})(\"$bridge\", () => (${asset("page.js")})(() => $read))"
        var challengeSince: TimeMark? = null
        var loaded = false
        val result = try {
            runWebView<T>(timeout = timeout) {
                userAgent = this@SiteView.userAgent()
                var surface: Surface? = null
                withNativeWebView { webView ->
                    // Ads can't open windows (a pop-under), with no window handler to give them one
                    webView.settings.setSupportMultipleWindows(true)
                    webView.settings.javaScriptCanOpenWindowsAutomatically = false
                    // The scroll indicators would be in the frames
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false
                    val metrics = webView.resources.displayMetrics
                    surface = Surface.host(webView, width ?: metrics.widthPixels, height ?: metrics.heightPixels)
                }
                // The site's hosts and its challenge only
                // The page stays where it was opened: no redirect away from it, by an ad or a stray tap
                val page = url.toHttpUrl().encodedPath
                interceptRequest { request ->
                    val host = request.url.host
                    when {
                        host != null && host !in allowedHosts -> blockedResponse()
                        request.isForMainFrame && host != null && request.url.encodedPath != page -> blockedResponse()
                        else -> null
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException(error.description.toString()))
                }
                // The page's reads run in a world of their own: they share its DOM but none of its JavaScript
                var acting = false
                webMessageBridge(bridge, siteOrigins, world) { message, reply ->
                    // Unanswered reads are posted again, so one that arrives mid-action can wait
                    if (acting) return@webMessageBridge
                    val page = message.parseAs<PageRead>()
                    when {
                        page.challenge -> {
                            val since = challengeSince ?: TimeSource.Monotonic.markNow().also { challengeSince = it }
                            if (since.elapsedNow() > CHALLENGE_WAIT) return@webMessageBridge reject(IOException(SOLVE_IN_WEBVIEW))
                            reply.postMessage("")
                        }
                        page.status != null -> {
                            if (page.status == 403 || page.status == 429) refusedAt = TimeSource.Monotonic.markNow()
                            reject(IOException("HTTP error ${page.status}"))
                        }
                        else -> {
                            challengeSince = null
                            loaded = true
                            when (val next = step(surface!!, page.result!!)) {
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
            throw IOException(if (loaded) "The site's page did not finish loading" else "The site's page did not load, try opening it in WebView", e)
        } catch (e: UnsupportedOperationException) {
            throw IOException("Update Android System WebView to read this site", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        return result
    }

    private fun randomName() = "k" + ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

    fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun blockedResponse() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
}

private const val TURNSTILE_HOST = "challenges.cloudflare.com"
private val LOAD_INTERVAL = 2.seconds
private val BACKOFF = 5.minutes
private val CHALLENGE_WAIT = 30.seconds
private val WEBVIEW_TIMEOUT = 60.seconds
internal const val SOLVE_IN_WEBVIEW = "Open in WebView to pass the site's verification"
