package eu.kanade.tachiyomi.extension.en.templescan

import android.webkit.WebResourceResponse
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Loads the site's pages in a WebView, one at a time, as its own readers do, and reads the site's data from them. */
internal class SitePages(baseUrl: String, private val userAgent: () -> String) {

    private val host = baseUrl.toHttpUrl().host

    private val mutex = Mutex()

    private var failedAt: Long? = null

    /** Loads [url] and reads [kind] (`catalog`, `chapters` or `pages`) from the site's app state. */
    suspend fun get(url: String, kind: String): SitePage = mutex.withLock {
        var challenged = false
        val script = asset("read_page.js")
        var polling = false

        // A challenge that just failed to solve would fail again; leave it to the user.
        fun WebViewScope<SitePage>.onChallenge() {
            challenged = true
            failedAt?.let { if (System.nanoTime() - it < BACKOFF.inWholeNanoseconds) reject(IOException(SOLVE_MANUALLY)) }
        }

        val page = try {
            runWebView<SitePage>(timeout = 60.seconds) {
                userAgent = this@SitePages.userAgent()
                blockImages = true
                interceptRequest { request ->
                    val requestHost = request.url.host
                    if (requestHost == null || requestHost.isAllowed()) null else blocked()
                }
                onPageStarted { started ->
                    if (started.toHttpUrlOrNull()?.isChallenge() == true) onChallenge()
                }
                onPageFinished { finished ->
                    val finishedUrl = finished.toHttpUrlOrNull() ?: return@onPageFinished
                    if (finishedUrl.host != host || finishedUrl.isChallenge()) return@onPageFinished
                    if (polling) return@onPageFinished
                    polling = true
                    // The site's app fills in its state after the page has loaded.
                    var polls = 0
                    poll(POLL_INTERVAL) {
                        // Only a reader page can legitimately lack its data (a locked chapter)
                        val settled = kind == "pages" && POLL_INTERVAL * ++polls >= SETTLE_TIME
                        // Serialized in the page: the WebView writes large numbers in exponent form
                        evaluateJs("JSON.stringify(($script)(\"$kind\", $settled))") { result ->
                            val page = result.parseAs<String?>()?.parseAs<SitePage?>() ?: return@evaluateJs
                            // Cloudflare's challenge page solves itself and reloads the real one.
                            if (page.cloudflareChallenge) {
                                polls = 0
                                onChallenge()
                            } else {
                                resolve(page)
                            }
                        }
                    }
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException(error.description.toString()))
                }
                loadUrl(url)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: WebViewTimeoutException) {
            if (!challenged) throw IOException(e.message, e)
            failedAt = System.nanoTime()
            throw IOException(SOLVE_MANUALLY, e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        failedAt = null
        if (page.status >= 400) throw IOException("HTTP error ${page.status}")
        page
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun String.isAllowed() = this == host || endsWith(".$host") || this == TURNSTILE_HOST

    private fun blocked() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))

    private fun HttpUrl.isChallenge() = encodedPath == "/challenge"

    companion object {
        private const val TURNSTILE_HOST = "challenges.cloudflare.com"

        private val POLL_INTERVAL = 250.milliseconds

        private val SETTLE_TIME = 10.seconds

        private const val SOLVE_MANUALLY = "Open in WebView to pass the site's verification"

        private val BACKOFF = 5.minutes
    }
}
