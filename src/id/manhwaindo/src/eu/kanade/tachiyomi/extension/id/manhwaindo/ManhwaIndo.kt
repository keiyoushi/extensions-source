package eu.kanade.tachiyomi.extension.id.manhwaindo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaIndo :
    MangaThemesia(
        "Manhwa Indo",
        "https://www.manhwaindo.my",
        "id",
        "/series",
        SimpleDateFormat("MMMM dd, yyyy", Locale.US),
    ) {
    override val hasProjectPage = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")

    override val client = network.client.newBuilder()
        .addInterceptor(::headersInterceptor)
        .rateLimit(4)
        .build()

    override fun pageListParse(response: Response) = super.pageListParse(response).distinctBy {
        it.imageUrl!!
    }

    private fun headersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val urlString = url.toString()
        val newHeaders = request.headers.newBuilder().apply {
            removeAll("X-Requested-With")
            if (urlString.contains("gmbr.pro") || urlString.contains("manhwaindo.my")) {
                set("Sec-Fetch-Site", if (urlString.contains("manhwaindo.my")) "same-origin" else "cross-site")
                set("Sec-Fetch-Mode", "no-cors")
                set("Sec-Fetch-Dest", "image")
            }
        }.build()

        val newRequest = if (url.scheme == "http" && (urlString.contains("gmbr.pro") || urlString.contains("manhwaindo.my"))) {
            request.newBuilder()
                .url(url.newBuilder().scheme("https").build())
                .headers(newHeaders)
                .build()
        } else {
            request.newBuilder()
                .headers(newHeaders)
                .build()
        }

        return chain.proceed(newRequest)
    }
}
