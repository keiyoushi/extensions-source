package eu.kanade.tachiyomi.extension.pt.diskusscan

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiskusScan : MangaThemesia(
    "Diskus Scan",
    "https://diskusscan.online",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {

    // Changed their theme from Madara to MangaThemesia.
    override val versionId = 2

    private var challengeHeaders: Headers? = null

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            if (challengeHeaders != null) {
                val request = chain.request().newBuilder()
                    .headers(challengeHeaders!!)
                    .build()

                val response = chain.proceed(request)

                if (response.isSuccessful) {
                    return@addInterceptor response
                }
            }

            chain.proceed(resolveChallenge(chain.request()))
        }
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveChallenge(origin: Request): Request {
        val latch = CountDownLatch(1)
        val headers = Headers.Builder()

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(Injekt.get<Application>())
            with(webView.settings) {
                javaScriptEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String) {
                    view?.apply {
                        evaluateJavascript("document.documentElement.outerHTML") { html ->
                            if ("challenge" in html) {
                                return@evaluateJavascript
                            }
                            latch.countDown()
                        }
                    }
                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val ignore = listOf(".css", ".js", ".php", ".ico", "google", "fonts")
                    val url = request.url.toString()
                    if (ignore.any { url.contains(it, ignoreCase = true) }) {
                        return emptyResource()
                    }

                    if (request.isOriginRequest()) {
                        for ((key, value) in request.requestHeaders) {
                            headers[key] = value
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                private fun WebResourceRequest.isOriginRequest(): Boolean =
                    this.url.toString().equals(origin.url.toString(), true)

                private fun emptyResource() = WebResourceResponse(null, null, null)
            }
            webView.loadUrl(origin.url.toString())
        }

        latch.await(client.callTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)

        challengeHeaders = origin.headers.newBuilder().let {
            for ((key, value) in headers.build()) {
                it[key] = value
            }
            it.build()
        }

        return origin.newBuilder()
            .headers(challengeHeaders!!)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .set("Accept-Language", "pt-BR,en-US;q=0.7,en;q=0.3")
        .set("Alt-Used", baseUrl.substringAfterLast("/"))
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Sec-Fetch-User", "?1")

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrlDirectory)
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override val seriesAuthorSelector = ".infotable tr:contains(Autor) td:last-child"
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] > *:not([class^=disku])"

    override fun String?.parseStatus() = when (orEmpty().trim().lowercase()) {
        "ativa" -> SManga.ONGOING
        "finalizada" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
}
