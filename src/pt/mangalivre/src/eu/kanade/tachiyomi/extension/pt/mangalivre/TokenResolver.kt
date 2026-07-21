package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads a real page in a hidden WebView so the site's own JS can clear Cloudflare's edge
 * challenge and generate the toon_v verification cookie — both land in the shared
 * CookieManager/cookie jar the extension's OkHttp client reads from afterwards.
 */
object TokenResolver {

    private const val TIMEOUT_SECONDS = 25L
    private const val SETTLE_MS = 2_000L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920

    @SuppressLint("SetJavaScriptEnabled")
    fun prime(pageUrl: String, userAgent: String?) {
        val handler = Handler(Looper.getMainLooper())
        val pageFinishedLatch = CountDownLatch(1)
        var webView: WebView? = null

        handler.post {
            try {
                val view = WebView(applicationContext)
                webView = view

                view.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)

                with(view.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    if (!userAgent.isNullOrBlank()) userAgentString = userAgent
                }

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(view, true)
                }

                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        pageFinishedLatch.countDown()
                    }
                }

                view.loadUrl(pageUrl)
            } catch (_: Throwable) {
                pageFinishedLatch.countDown()
            }
        }

        pageFinishedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        Thread.sleep(SETTLE_MS)

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
        }
    }
}
