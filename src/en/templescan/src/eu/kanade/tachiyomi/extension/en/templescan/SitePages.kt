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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Loads the site's pages in a WebView, one at a time, as its own readers do. */
internal class SitePages(baseUrl: String) {

    private val host = baseUrl.toHttpUrl().host

    private val mutex = Mutex()

    private var failedAt: Long? = null

    suspend fun get(url: String): Document = mutex.withLock {
        var challenged = false

        // A challenge that just failed to solve would fail again; leave it to the user.
        fun WebViewScope<RenderedPage>.onChallenge() {
            challenged = true
            failedAt?.let { if (System.nanoTime() - it < BACKOFF.inWholeNanoseconds) reject(IOException(SOLVE_MANUALLY)) }
        }

        val page = try {
            runWebView<RenderedPage>(timeout = 60.seconds) {
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
                    evaluateJs(RENDERED_PAGE_JS) { result ->
                        val rendered = result.parseAs<RenderedPage>()
                        // Cloudflare's challenge page solves itself and reloads the real one.
                        if (rendered.cloudflareChallenge) onChallenge() else resolve(rendered)
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
        Jsoup.parse(page.html, url)
    }

    private fun String.isAllowed() = this == host || endsWith(".$host") || this == TURNSTILE_HOST

    private fun blocked() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))

    private fun HttpUrl.isChallenge() = encodedPath == "/challenge"

    companion object {
        private const val TURNSTILE_HOST = "challenges.cloudflare.com"

        // responseStatus needs WebView 109+
        private const val RENDERED_PAGE_JS = "({ " +
            "status: performance.getEntriesByType('navigation')[0]?.responseStatus || 200, " +
            "cloudflareChallenge: !!window._cf_chl_opt, " +
            "html: document.documentElement.outerHTML })"

        private const val SOLVE_MANUALLY = "Open in WebView to pass the site's verification"

        private val BACKOFF = 5.minutes
    }
}
