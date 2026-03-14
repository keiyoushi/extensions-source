package eu.kanade.tachiyomi.extension.es.hmangakyomi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Hmangakyomi :
    MangaThemesia(
        "Hmangakyomi",
        "https://hmangakyomi.online",
        "es",
        mangaUrlDirectory = "/manga",
    ) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(::thumbnailInterceptor)
        .build()

    private fun thumbnailInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return if (request.url.host.matches(Regex("i[0-9]+\\.wp\\.com"))) {
            chain.proceed(
                request.newBuilder()
                    .header("Referer", "$baseUrl/")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("post_type", "manga")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", "popular")
            .build(),
        headers,
    )

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("post_type", "manga")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", "update")
            .build(),
        headers,
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "manga")
            .addQueryParameter("page", page.toString())
            .build(),
        headers,
    )
}
