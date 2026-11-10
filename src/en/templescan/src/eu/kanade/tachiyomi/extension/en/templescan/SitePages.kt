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
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Loads the site's pages in a WebView, one at a time, and reads what they render with an asset script. */
internal class SitePages(
    baseUrl: String,
    private val client: OkHttpClient,
    private val userAgent: () -> String,
    private val passSiteCheck: suspend (siteKey: String) -> Unit,
) {

    private val host = baseUrl.toHttpUrl().host

    private val mutex = Mutex()

    /** When Cloudflare's challenge last failed to pass by itself; until [BACKOFF] runs out, the next one goes to the user. */
    private var failedAt: TimeMark? = null

    /** When the site's own Turnstile check last failed to pass, and why; rethrown until [SITE_CHECK_BACKOFF] runs out. */
    private var siteCheckFailure: Pair<TimeMark, Exception>? = null

    /**
     * Loads [url] and polls `(script)(args, state)` from `assets/[scriptName]` until it returns a result, passing the
     * site's own Turnstile check first when the page is sent to it.
     */
    suspend fun read(url: String, scriptName: String, args: String = "{}"): String = mutex.withLock {
        try {
            load(url, scriptName, args)
        } catch (check: SiteCheckException) {
            siteCheckFailure?.takeIf { it.first.elapsedNow() < SITE_CHECK_BACKOFF }?.let { throw it.second }
            try {
                passSiteCheck(check.siteKey)
                load(url, scriptName, args)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                siteCheckFailure = TimeSource.Monotonic.markNow() to e
                throw e
            }
        }
    }

    /** The script returns null while it is still working; `state` keeps its progress between polls. */
    private suspend fun load(url: String, scriptName: String, args: String): String {
        val stateName = List(16) { ('a'..'z').random() }.joinToString("")
        val call = "(${asset("run.js")})(${asset(scriptName)}, $args, \"$stateName\")"
        var challengeSeenAt: TimeMark? = null
        var siteCheckSeenAt: TimeMark? = null
        val siteKey = AtomicReference<String?>()

        val result = try {
            runWebView<String>(timeout = TIMEOUT) {
                userAgent = this@SitePages.userAgent()
                // The site refuses WebView's X-Requested-With, which the requests left to the WebView carry; only its check needs them
                if (originMatchedHeaderSupported) setOriginMatchedHeader("X-Requested-With", "XMLHttpRequest", setOf("https://$host"))
                interceptRequest { request ->
                    // A key from Cloudflare's own challenge page, before it moved on to the site's
                    if (request.isForMainFrame) siteKey.set(null)
                    val requestHost = request.url.host
                    val path = request.url.path.orEmpty()
                    when {
                        requestHost == host -> when {
                            // Stylesheets and fonts
                            path.startsWith("/_next/static/css/") || path.startsWith("/_next/static/media/") -> blocked()
                            // Not the X-Requested-With set for the requests left to the WebView
                            else -> proxyRequest(request, client) { removeHeader("X-Requested-With") }
                        }
                        requestHost == null -> null
                        requestHost == TURNSTILE_HOST -> {
                            // The site's Turnstile key, from the widget its check page loads
                            TURNSTILE_KEY.find(path)?.let { siteKey.set(it.value) }
                            null
                        }
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
                            page.siteCheck -> {
                                val seenAt = siteCheckSeenAt ?: TimeSource.Monotonic.markNow().also { siteCheckSeenAt = it }
                                val key = siteKey.get()
                                when {
                                    key != null -> reject(SiteCheckException(key))
                                    seenAt.elapsedNow() > CHALLENGE_WAIT -> reject(IOException(SITE_CHECK_MISSING))
                                }
                            }
                            page.challenge -> {
                                // The site's check usually passes by itself within a few seconds
                                val recentlyFailed = failedAt?.let { it.elapsedNow() < BACKOFF } == true
                                val seenAt = challengeSeenAt ?: TimeSource.Monotonic.markNow().also { challengeSeenAt = it }
                                if (recentlyFailed || seenAt.elapsedNow() > CHALLENGE_WAIT) {
                                    failedAt = TimeSource.Monotonic.markNow()
                                    reject(IOException(if (originMatchedHeaderSupported) SOLVE_MANUALLY else UPDATE_WEBVIEW))
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
            throw IOException("The site's page did not load", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        failedAt = null
        return result
    }

    private class SiteCheckException(val siteKey: String) : IOException("The site's verification did not pass")

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

        private const val UPDATE_WEBVIEW = "Update Android System WebView to pass the site's verification"

        private val BACKOFF = 5.minutes

        private val SITE_CHECK_BACKOFF = 30.seconds

        private const val SITE_CHECK_MISSING = "The site's verification did not load"

        private val TURNSTILE_KEY = Regex("""0x4[A-Za-z0-9_-]{20,}""")
    }
}
