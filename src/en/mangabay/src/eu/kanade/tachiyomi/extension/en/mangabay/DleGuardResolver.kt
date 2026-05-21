package eu.kanade.tachiyomi.extension.en.mangabay

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Solves the DLE Guard `_c` / `_v` proof-of-work challenge by loading the
 * site in a hidden [WebView]. The site's JS computes the PoW, posts to `/_v`,
 * and a `__guard_trust` cookie is set in the shared [CookieManager], which the
 * OkHttp cookie jar picks up on subsequent requests.
 */
object DleGuardResolver {

    private const val TIMEOUT_SECONDS = 30L
    private const val POLL_INTERVAL_MS = 250L
    private const val PARALLEL_TRUST_WINDOW_MS = 5_000L
    private const val TRUST_COOKIE = "__guard_trust"

    @Volatile
    private var failedOnce = false

    @Volatile
    private var lastSolveAt = 0L

    /**
     * Returns an [Interceptor] that detects the `_c` redirect, solves the challenge
     * in a [WebView], and retries the original request once. Throws [IOException] if
     * the challenge cannot be solved (e.g. WebView unavailable or PoW timed out).
     */
    fun interceptor(baseUrl: String): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        if (response.request.url.pathSegments.firstOrNull() != "_c") {
            return@Interceptor response
        }
        response.close()
        if (!resolve("$baseUrl/", originalRequest.header("User-Agent"))) {
            throw IOException("Open in WebView to bypass site protection")
        }
        chain.proceed(originalRequest)
    }

    @Synchronized
    @SuppressLint("SetJavaScriptEnabled")
    private fun resolve(siteUrl: String, userAgent: String?): Boolean {
        if (failedOnce) return false
        // Parallel callers that piled up behind a fresh solve don't need to re-run.
        if (System.currentTimeMillis() - lastSolveAt < PARALLEL_TRUST_WINDOW_MS) return true

        val cookieManager = CookieManager.getInstance()
        // Clear any existing __guard_trust cookie: caller hit /_c, so whatever is stored is stale.
        cookieManager.setCookie(siteUrl, "$TRUST_COOKIE=; Max-Age=0; Path=/")
        cookieManager.flush()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        lateinit var poll: Runnable

        handler.post {
            val wv = WebView(applicationContext)
            webView = wv

            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
                if (!userAgent.isNullOrBlank()) userAgentString = userAgent
            }

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = WebViewClient()

            poll = Runnable {
                if (latch.count == 0L) return@Runnable
                if (hasTrust(cookieManager, siteUrl)) {
                    latch.countDown()
                } else {
                    handler.postDelayed(poll, POLL_INTERVAL_MS)
                }
            }

            wv.loadUrl(siteUrl)
            handler.postDelayed(poll, POLL_INTERVAL_MS)
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        handler.post {
            handler.removeCallbacks(poll)
            webView?.stopLoading()
            webView?.destroy()
        }

        val solved = hasTrust(cookieManager, siteUrl)
        if (solved) {
            lastSolveAt = System.currentTimeMillis()
        } else {
            failedOnce = true
        }
        return solved
    }

    private fun hasTrust(cookieManager: CookieManager, url: String): Boolean {
        val cookies = cookieManager.getCookie(url) ?: return false
        return cookies.split(';').any { it.trim().startsWith("$TRUST_COOKIE=") }
    }
}
