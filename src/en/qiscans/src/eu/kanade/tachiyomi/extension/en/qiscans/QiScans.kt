package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class QiScans :
    Iken(
        "Qi Scans",
        "en",
        "https://qimanhwa.com",
        "https://api.qimanhwa.com",
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override val usePopularMangaApi = true

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            // 'query' instead of 'posts'
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", Iken.PER_PAGE.toString())
            addQueryParameter("orderBy", "updatedAt")
        }.build()
        return GET(url, headers)
    }

    @Serializable
    class PageParseDto(
        val url: String,
        val order: Int,
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst("svg.lucide-lock") != null) {
            throw Exception("Unlock chapter in webview")
        }

        return document.getNextJson("images").parseAs<List<PageParseDto>>().sortedBy { it.order }.mapIndexed { idx, p ->
            Page(idx, imageUrl = p.url)
        }
    }
}
