package eu.kanade.tachiyomi.extension.en.templescan

import android.util.Base64
import android.webkit.WebResourceResponse
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
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
internal class SitePages(baseUrl: String, private val userAgent: () -> String) {

    private val host = baseUrl.toHttpUrl().host

    private val mutex = Mutex()

    /** When the site's check last failed to pass by itself; until [BACKOFF] runs out, the next one goes to the user. */
    private var failedAt: TimeMark? = null

    /**
     * Loads [url] and polls `(script)(args, state)` from `assets/[scriptName]` until it returns a result.
     * The script returns null while it is still working; `state` keeps its progress between polls.
     */
    suspend fun read(url: String, scriptName: String, args: String = "{}"): String = mutex.withLock {
        val stateName = List(16) { ('a'..'z').random() }.joinToString("")
        val call = "(${asset("run.js")})(${asset(scriptName)}, $args, \"$stateName\")"
        var challengeSeenAt: TimeMark? = null

        val result = try {
            runWebView<String>(timeout = TIMEOUT) {
                userAgent = this@SitePages.userAgent()
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
                        val json = output.parseAs<String?>() ?: return@evaluateJs
                        val page = json.parseAs<PageStatus?>() ?: return@evaluateJs
                        when {
                            page.challenge -> {
                                // The site's check usually passes by itself within a few seconds
                                val recentlyFailed = failedAt?.let { it.elapsedNow() < BACKOFF } == true
                                val seenAt = challengeSeenAt ?: TimeSource.Monotonic.markNow().also { challengeSeenAt = it }
                                if (recentlyFailed || seenAt.elapsedNow() > CHALLENGE_WAIT) {
                                    failedAt = TimeSource.Monotonic.markNow()
                                    reject(IOException(SOLVE_MANUALLY))
                                }
                            }
                            page.status != null -> reject(IOException("HTTP error ${page.status}"))
                            page.error != null -> reject(IOException(page.error))
                            else -> resolve(json)
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
            throw IOException("The site's page did not load, try opening it in WebView", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        failedAt = null
        result
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

        private val CHALLENGE_WAIT = 30.seconds

        private const val SOLVE_MANUALLY = "Open in WebView to pass the site's verification"

        private val BACKOFF = 5.minutes
    }
}
