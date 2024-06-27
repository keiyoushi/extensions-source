package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik : Madara(
    "MG Komik",
    "https://mgkomik.id",
    "id",
    SimpleDateFormat("dd MMM yy", Locale.US),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "komik"

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Sec-Fetch-Dest", "document")
        add("Sec-Fetch-Mode", "navigate")
        add("Sec-Fetch-Site", "same-origin")
        add("Upgrade-Insecure-Requests", "1")
        add("X-Requested-With", randomString((1..20).random())) // added for webview, and removed in interceptor for normal use
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(9, 2)
        .build()

    override fun popularMangaNextPageSelector() = ".wp-pagenavi span.current + a"

    override fun searchMangaNextPageSelector() = "a.page.larger"

    override fun latestUpdatesRequest(page: Int): Request =
        if (useLoadMoreRequest()) {
            loadMoreRequest(page, popular = false)
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}", headers)
        }

    override val chapterUrlSuffix = ""

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('.')
        return List(length) { charPool.random() }.joinToString("")
    }
}
