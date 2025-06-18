package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Zazhimi : HttpSource() {

    override val baseUrl = "https://android2024.zazhimi.net/api"
    override val lang = "zh"
    override val name = "杂志迷"
    override val supportsLatest = false

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/index.php?p=$page&s=20")

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<IndexResponse>()
        return MangasPage(result.focus.map(IndexItem::toSManga), true)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search.php")
            .addQueryParameter("k", query)
            .addQueryParameter("p", page.toString())
            .addQueryParameter("s", "20")
        return GET(url.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        return MangasPage(result.magazine.map(SearchItem::toSManga), true)
    }

    // Manga Detail Page

    // override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ShowResponse>()
        if (result.content.isEmpty()) return SManga.create()
        return SManga.create().apply {
            result.content[0].let {
                title = it.magName
                author = it.magName.split(" ")[0]
                thumbnail_url = it.magPic
                url = "/show.php?a=${it.magId}"
            }
        }
    }

    // Manga Detail Page / Chapters Page (Separate)

    // override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ShowResponse>()
        if (result.content.isEmpty()) return emptyList()
        return listOf(
            SChapter.create().apply {
                result.content[0].let {
                    url = "/show.php?a=${it.magId}"
                    name = it.magName
                    chapter_number = 1F
                }
            },
        )
    }

    // Manga View Page

    // override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ShowResponse>()
        return result.content.mapIndexed { i, it -> it.toPage(i) }
    }

    // Image

    // override fun imageRequest(page: Page) = GET(page.url, headers)

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
