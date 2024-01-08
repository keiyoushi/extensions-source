package eu.kanade.tachiyomi.extension.es.bokugentranslation

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch

class BokugenTranslation : Madara(
    "BokugenTranslation",
    "https://bokugents.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    private var loadWebView = true
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (loadWebView) {
                val handler = Handler(Looper.getMainLooper())
                val latch = CountDownLatch(1)
                var webView: WebView? = null
                handler.post {
                    val webview = WebView(Injekt.get<Application>())
                    webView = webview
                    webview.settings.domStorageEnabled = true
                    webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    webview.settings.useWideViewPort = false
                    webview.settings.loadWithOverviewMode = false

                    webview.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress == 100) {
                                latch.countDown()
                            }
                        }
                    }
                    webview.loadUrl(url)
                }

                latch.await()
                loadWebView = false
                handler.post { webView?.destroy() }
            }
            chain.proceed(request)
        }
        .rateLimit(1, 1)
        .build()

    override val useNewChapterEndpoint = true
}
