package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MagusManga : Keyoapp(
    "Magus Manga",
    "https://magustoon.com",
    "en",
) {
    private val cdnUrl = "https://cdn.magustoon.com"

    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::captchaInterceptor)
        .addInterceptor(::fallbackCdnInterceptor)
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
        .rateLimitHost(cdnUrl.toHttpUrl(), 1)
        .build()

    private fun captchaInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val document = Jsoup.parse(
                response.peekBody(Long.MAX_VALUE).string(),
                response.request.url.toString(),
            )

            if (document.selectFirst(".g-recaptcha") != null) {
                response.close()
                throw IOException("Solve Captcha in WebView")
            }
        }

        return response
    }

    override fun chapterListSelector(): String {
        return "${super.chapterListSelector()}:not(:has(img[src*=coin]))"
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#pages > img").mapIndexed { idx, img ->
            val uid = img.attr("uid")

            Page(idx, imageUrl = "$cdnUrl/x/$uid")
        }
    }

    private fun fallbackCdnInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val response = chain.proceed(request)

        return if (url.startsWith(cdnUrl) && !response.isSuccessful) {
            response.close()
            val newRequest = request.newBuilder()
                .url(
                    url.replace("/x/", "/v/"),
                )
                .build()

            chain.proceed(newRequest)
        } else {
            response
        }
    }
}
