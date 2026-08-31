package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONException
import java.util.UUID

class ReaderVerificationActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val pages = linkedSetOf<String>()
    private var receiver: ResultReceiver? = null
    private lateinit var pathPrefix: String
    private var webView: WebView? = null
    private var delivered = false

    private val deliverPages = Runnable {
        val result = synchronized(pages) {
            if (pages.isEmpty()) return@Runnable
            ArrayList(pages)
        }
        delivered = true
        val bundle = Bundle().apply {
            putStringArrayList(EXTRA_PAGES, result)
        }
        receiver?.send(RESULT_PAGES, bundle)
        finish()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val readerUrl = intent.getStringExtra(EXTRA_URL)
        val mangaId = intent.getStringExtra(EXTRA_MANGA_ID)
        val chapterNumber = intent.getStringExtra(EXTRA_CHAPTER_NUMBER)
        receiver = intent.getParcelableExtra(EXTRA_RECEIVER)
        if (readerUrl == null || mangaId == null || chapterNumber == null || receiver == null) {
            finish()
            return
        }
        if (Uri.parse(readerUrl).host != SITE_HOST) {
            finish()
            return
        }
        pathPrefix = "/obras/$mangaId/$chapterNumber/"

        val bridgeName = "bridge_${UUID.randomUUID().toString().replace("-", "")}"
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun post(value: String) {
                        try {
                            val values = JSONArray(value)
                            for (index in 0 until values.length()) {
                                addCandidate(values.getString(index))
                            }
                        } catch (_: JSONException) {
                        }
                    }
                },
                bridgeName,
            )
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    addCandidate(request.url.toString())
                    return null
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.isForMainFrame && request.url.host != SITE_HOST

                override fun onPageFinished(view: WebView, url: String) {
                    val script =
                        """
                        (() => {
                            if (window.__toonLivreCollector) return;
                            window.__toonLivreCollector = setInterval(() => {
                                const urls = [
                                    ...performance.getEntriesByType('resource').map(entry => entry.name),
                                    ...Array.from(document.images).map(image => image.currentSrc || image.src),
                                ];
                                window['$bridgeName'].post(JSON.stringify(urls));
                            }, 500);
                        })();
                        """.trimIndent()
                    view.evaluateJavascript(script, null)
                }
            }
            loadUrl(readerUrl)
        }
        setContentView(webView)
    }

    private fun addCandidate(candidate: String) {
        val cdnUrl = candidate.toCdnUrl() ?: return
        synchronized(pages) {
            if (!pages.add(cdnUrl)) return
        }
        handler.removeCallbacks(deliverPages)
        handler.postDelayed(deliverPages, SETTLE_DELAY_MS)
    }

    private fun String.toCdnUrl(): String? {
        val uri = Uri.parse(this)
        val cdnUri = when (uri.host) {
            CDN_HOST -> uri
            PROXY_HOST -> uri.getQueryParameter("url")?.let(Uri::parse)
            else -> null
        } ?: return null
        val path = cdnUri.path
        if (cdnUri.scheme != "https" || cdnUri.host != CDN_HOST || path == null || !path.startsWith(pathPrefix)) {
            return null
        }
        return cdnUri.toString()
    }

    override fun onDestroy() {
        handler.removeCallbacks(deliverPages)
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        if (!delivered && receiver != null && !isChangingConfigurations) {
            receiver?.send(RESULT_CANCELED, null)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "reader_url"
        const val EXTRA_MANGA_ID = "manga_id"
        const val EXTRA_CHAPTER_NUMBER = "chapter_number"
        const val EXTRA_RECEIVER = "result_receiver"
        const val EXTRA_PAGES = "pages"
        const val RESULT_PAGES = 1

        private const val SITE_HOST = "toonlivre.net"
        private const val CDN_HOST = "cdn.toonlivre.net"
        private const val PROXY_HOST = "slightly-free-mayfly.edgecompute.app"
        private const val SETTLE_DELAY_MS = 1_000L
    }
}
