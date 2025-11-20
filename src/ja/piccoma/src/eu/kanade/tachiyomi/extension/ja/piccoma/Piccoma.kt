package eu.kanade.tachiyomi.extension.ja.piccoma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Piccoma : HttpSource() {
    override val name = "Piccoma"
    override val baseUrl = "https://piccoma.com"
    override val lang = "ja"
    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/web/ranking/K/P/0", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.PCM-productRanking li > a").map { mangaElement ->
            SManga.create().apply {
                url = mangaElement.attr("href")
                title = mangaElement.selectFirst(".PCM-rankingProduct_title p")!!.text()
                thumbnail_url = mangaElement.selectFirst("img.js_lazy")?.attr("data-original")?.let { "https:$it" }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/web/search/result_ajax/list".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("tab_type", "T")
            .build()
        return GET(
            url,
            super.headers.newBuilder().add("x-requested-with", "XMLHttpRequest").build(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponseDto>()
        val mangas = result.data.products.map {
            SManga.create().apply {
                url = "/web/product/${it.id}"
                title = it.title
                thumbnail_url = "https:${it.img}"
            }
        }
        val currentPage = response.request.url.queryParameter("page").toInt()
        val hasNextPage = currentPage < result.data.totalPage
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.PCM-productTitle")!!.text()
            author = document.select("ul.PCM-productAuthor li a").joinToString { it.text() }
            genre = document.select("ul.PCM-productGenre li a, .PCM-productDesc_tagList li a").joinToString { it.text() }
            description = document.selectFirst("div.PCM-productDesc > p")?.text()
            thumbnail_url = document.selectFirst("img.PCM-productThum_img")?.attr("src")?.let { "https:$it" }
            status = when {
                document.selectFirst("ul.PCM-productStatus")?.text()
                    ?.contains("連載中") == true -> SManga.ONGOING

                document.selectFirst("ul.PCM-productStatus")?.text()
                    ?.contains("完結") == true -> SManga.COMPLETED

                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val segments = response.request.url.pathSegments
        val productIndex = segments.indexOf("product")
        if (productIndex == -1 || segments.size <= productIndex + 1) {
            return emptyList()
        }
        val productId = segments[productIndex + 1]

        val isFullListPage = response.request.url.toString().contains("/episodes")
        val episodesDocument = if (isFullListPage) {
            response.asJsoup()
        } else {
            val episodesUrl = "$baseUrl/web/product/$productId/episodes?etype=E"
            client.newCall(GET(episodesUrl, headers)).execute().asJsoup()
        }

        return episodesDocument.select("ul.PCM-epList li a").mapNotNull { chapterElement ->
            val episodeId =
                chapterElement.attr("data-episode_id").ifEmpty { return@mapNotNull null }
            SChapter.create().apply {
                url = "/web/viewer/$productId/$episodeId"
                name = chapterElement.selectFirst("h2")!!.text()
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        //TODO
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
