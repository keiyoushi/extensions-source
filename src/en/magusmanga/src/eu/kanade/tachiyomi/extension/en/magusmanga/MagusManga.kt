package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import org.jsoup.Jsoup

class MagusManga : Keyoapp(
    "Magus Manga",
    "https://magustoon.com",
    "en",
) {
    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::captchaInterceptor)
        .rateLimitHost(baseUrl.toHttpUrl(), 1)
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
}
