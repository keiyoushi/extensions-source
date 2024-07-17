package eu.kanade.tachiyomi.extension.tr.mangaokutr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOkuTr : MangaThemesia(
    "Manga Oku TR",
    "https://mangaokutr.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::statusCodeInterceptor)
        .build()

    private fun statusCodeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 500) return response
        if (response.header("cf-mitigated") != null) return response
        if (request.url.host != baseUrl.toHttpUrl().host) return response

        // Browse is normally error 500 (???)
        return response.newBuilder()
            .code(200)
            .build()
    }

    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tür) a"
    override val seriesDescriptionSelector = "h2 + .entry-content > p:not(:contains(Kategoriler: ))"
}
