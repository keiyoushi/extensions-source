package eu.kanade.tachiyomi.extension.en.templescan

import android.util.Base64
import android.webkit.WebResourceResponse
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Loads the site's pages in a WebView, one at a time, and reads what they render with an asset script. */
internal class SitePages(
    baseUrl: String,
    private val userAgent: () -> String,
) {

    private val host = baseUrl.toHttpUrl().host

    private val mutex = Mutex()

    /** When a check last failed without the app on screen to show it; until [BACKOFF] runs out, the next one fails after [SHOW_AFTER]. */
    private var failedAt: TimeMark? = null

    /** When the last page load started; loads start at most every [LOAD_GAP], like a reader's. */
    private var lastLoad: TimeMark? = null

    /**
     * Loads [url] and polls `(script)(args, state)` from `assets/[scriptName]` until it returns a result; `state`
     * keeps the script's progress between polls. Cloudflare's and the site's own checks run in the page, shown to the
     * user when they want a tap.
     */
    suspend fun read(url: String, scriptName: String, args: String = "{}"): String = mutex.withLock {
        lastLoad?.let { delay(LOAD_GAP - it.elapsedNow()) }
        lastLoad = TimeSource.Monotonic.markNow()
        val stateName = List(16) { ('a'..'z').random() }.joinToString("")
        val call = "(${asset("run.js")})(${asset(scriptName)}, $args, \"$stateName\")"
        var checkSeenAt: TimeMark? = null

        try {
            runWebView<String>(timeout = TIMEOUT) {
                userAgent = this@SitePages.userAgent()
                // Empty: the site and its check refuse any X-Requested-With value
                if (!originMatchedHeaderSupported) throw IOException(UPDATE_WEBVIEW)
                setOriginMatchedHeader("X-Requested-With", "", setOf("https://$host"))
                interceptRequest { request ->
                    val requestHost = request.url.host
                    when {
                        requestHost == null || requestHost == host || requestHost == TURNSTILE_HOST -> null
                        // The reader loads a few pages at a time and mounts the next ones once those have loaded
                        requestHost.endsWith(".$host") -> pixel()
                        else -> blocked()
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException(error.description.toString()))
                }
                poll(POLL_INTERVAL) {
                    evaluateJs(call) { output ->
                        // Null while the page navigates
                        val json = output.parseAs<String?>() ?: return@evaluateJs
                        val page = json.parseAs<PageStatus?>()
                        // The check passed and the page moved on
                        if (page?.check != true && checkSeenAt != null) {
                            hideFromUser()
                            checkSeenAt = null
                        }
                        if (page == null) return@evaluateJs
                        when {
                            page.check -> {
                                // Cloudflare's and the site's checks usually pass by themselves within a few seconds
                                val seenAt = checkSeenAt ?: TimeSource.Monotonic.markNow().also { checkSeenAt = it }
                                if (seenAt.elapsedNow() > SHOW_AFTER && !showToUser(TURNSTILE_AREA)) {
                                    // Without the app on screen there's no one to tap it
                                    val recentlyFailed = failedAt?.let { it.elapsedNow() < BACKOFF } == true
                                    if (recentlyFailed || seenAt.elapsedNow() > CHECK_WAIT) {
                                        failedAt = TimeSource.Monotonic.markNow()
                                        reject(IOException(SOLVE_MANUALLY))
                                    }
                                }
                            }
                            page.status != null -> reject(IOException("HTTP error ${page.status}"))
                            page.error != null -> reject(IOException(page.error))
                            else -> {
                                failedAt = null
                                resolve(json)
                            }
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
            throw IOException("The site's page did not load", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    // An empty 200: a failed script load fires the site's onerror handlers
    private fun blocked() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun pixel() = WebResourceResponse("image/gif", null, ByteArrayInputStream(PIXEL))

    companion object {
        private const val TURNSTILE_HOST = "challenges.cloudflare.com"

        /** A 1x1 transparent GIF, served for the site's images instead of downloading them. */
        private val PIXEL = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)

        private val POLL_INTERVAL = 250.milliseconds

        private val TIMEOUT = 2.minutes

        private val LOAD_GAP = 2.seconds

        private val SHOW_AFTER = 5.seconds

        private val CHECK_WAIT = 30.seconds

        private val BACKOFF = 5.minutes

        private const val SOLVE_MANUALLY = "Open in WebView to pass the site's verification"

        private const val UPDATE_WEBVIEW = "Update Android System WebView to pass the site's verification"
    }
}
