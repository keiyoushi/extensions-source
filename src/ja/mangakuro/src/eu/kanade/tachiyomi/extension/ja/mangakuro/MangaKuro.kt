package eu.kanade.tachiyomi.extension.ja.mangakuro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class MangaKuro : HttpSource() {

    override val name = "MangaKuro"

    override val baseUrl = "https://mangakuro.net"

    override val lang = "ja"

    override val supportsLatest = true

    private fun mangaRequestBuilder(type: String, page: Int, key: String, value: String) = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(type)
            addPathSegment(page.toString())
            addQueryParameter(key, value)
        }.build(),
        headers,
    )

    override fun popularMangaRequest(page: Int): Request = mangaRequestBuilder("all-manga", page, "sort", "views")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".story_item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                title = element.selectFirst(".mg_name a")?.text().orEmpty()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[title='Last Page']") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = mangaRequestBuilder("all-manga", page, "sort", "latest-updated")

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaRequestBuilder("search", page, "keyword", query)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            author = document.selectFirst("div:has(.lnr-user) + .info_value")?.text()
            status = parseStatus(document.selectFirst("div:has(.lnr-leaf) + .info_value")?.text().orEmpty())
            description = document.selectFirst(".detail_reviewContent")?.text()
            thumbnail_url = document.selectFirst(".detail_avatar img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("進行中") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter_box .item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.text().substringAfter("# ")
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val id = chapterIDRegex.find(html)?.groupValues?.get(1) ?: return emptyList()

        return client.newCall(GET("$baseUrl/ajax/image/list/chap/$id", headers)).execute().use { apiResponse ->
            imageRegex.findAll(apiResponse.body.string()).mapIndexed { idx, img ->
                Page(idx, imageUrl = img.groupValues[1])
            }.toList()
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    companion object {
        private val chapterIDRegex = """CHAPTER_ID\s*=\s*(\d+);""".toRegex()
        private val imageRegex = """src=\\"([^"]+)\\"""".toRegex()
    }
}
