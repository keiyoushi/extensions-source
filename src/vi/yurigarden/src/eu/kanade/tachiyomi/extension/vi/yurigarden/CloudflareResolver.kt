package eu.kanade.tachiyomi.extension.vi.yurigarden

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Solves a Cloudflare Turnstile / challenge page by loading the target URL in a
 * hidden, fully-laid-out [WebView]. Turnstile fingerprints the rendering pipeline
 * (screen/canvas size), so the view is forced to a realistic layout to let the
 * invisible/managed widget auto-solve in most cases.
 *
 * On success the `cf_clearance` cookie is set in the shared [CookieManager], which
 * is picked up by the OkHttp cookie jar on subsequent requests.
 */
object CloudflareResolver {

    private const val TIMEOUT_SECONDS = 45L
    private const val POLL_INTERVAL_MS = 500L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private const val CLEARANCE_COOKIE = "cf_clearance"

    @Synchronized
    @SuppressLint("SetJavaScriptEnabled")
    fun resolve(loadUrl: String, cookieUrl: String = loadUrl, userAgent: String? = null): Boolean {
        val cookieManager = CookieManager.getInstance()
        if (hasClearance(cookieManager, cookieUrl)) return true

        val context = Injekt.get<Application>()
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        lateinit var poll: Runnable

        handler.post {
            val wv = WebView(context)
            webView = wv

            wv.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            wv.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)

            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                blockNetworkImage = false
                mediaPlaybackRequiresUserGesture = false
                // cf_clearance is bound to the UA that solved it; keep both sides aligned.
                if (!userAgent.isNullOrBlank()) userAgentString = userAgent
            }

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = WebViewClient()

            poll = Runnable {
                if (latch.count == 0L) return@Runnable
                if (hasClearance(cookieManager, cookieUrl)) {
                    latch.countDown()
                } else {
                    handler.postDelayed(poll, POLL_INTERVAL_MS)
                }
            }

            wv.loadUrl(loadUrl)
            handler.postDelayed(poll, POLL_INTERVAL_MS)
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        handler.post {
            handler.removeCallbacks(poll)
            webView?.stopLoading()
            webView?.destroy()
        }

        return hasClearance(cookieManager, cookieUrl)
    }

    private fun hasClearance(cookieManager: CookieManager, url: String): Boolean {
        val cookies = cookieManager.getCookie(url) ?: return false
        return cookies.split(';').any { it.trim().startsWith("$CLEARANCE_COOKIE=") }
    }
}
