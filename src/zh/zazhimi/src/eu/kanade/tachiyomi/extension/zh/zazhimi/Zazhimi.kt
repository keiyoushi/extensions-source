package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.lang.IllegalStateException

class Zazhimi : HttpSource() {

    private val apiUrl = "https://android2026.zazhimi.net/api"

    override val baseUrl = "https://www.zazhimi.net"
    override val lang = "zh"
    override val name = "杂志迷"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder().set("User-Agent", "ZaZhiMi_6.0.0")

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/index.php?p=$page&s=20", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<IndexResponse>()
        val mangas = result.new.map(NewItem::toSManga)
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // Search

    override fun getFilterList() = FilterList(
        Filter.Header("筛选条件（搜索关键字时无效）"),
        TypeFilter(),
        BrandFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
        if (query.isEmpty()) {
            url.addPathSegment("lists.php")
                .addQueryParameter("c", filters[1].toString())
                .addQueryParameter("m", filters[2].toString())
        } else {
            url.addPathSegment("search.php").addQueryParameter("k", query)
        }
        url.addQueryParameter("p", page.toString()).addQueryParameter("s", "20")
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        return MangasPage(result.magazine.map(SearchItem::toSManga), true)
    }

    // Manga Detail Page

    override fun mangaDetailsRequest(manga: SManga) = GET(apiUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ShowResponse>()
        if (result.content.isEmpty()) throw IllegalStateException("内容解析为空！")
        val item = result.content[0]
        return SManga.create().apply {
            title = item.magName
            author = item.magName.split(" ")[0]
            thumbnail_url = item.magPic
            url = "/show.php?a=${item.magId}"
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // Manga Detail Page / Chapters Page (Separate)

    override fun chapterListRequest(manga: SManga) = GET(apiUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ShowResponse>()
        if (result.content.isEmpty()) return emptyList()
        val item = result.content[0]
        val chapter = SChapter.create().apply {
            url = "/show.php?a=${item.magId}"
            name = item.magName
            chapter_number = 1F
        }
        return listOf(chapter)
    }

    // Manga View Page

    override fun pageListRequest(chapter: SChapter) = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ShowResponse>()
        return result.content.mapIndexed { i, it -> it.toPage(i) }
    }

    // Image

    // override fun imageRequest(page: Page) = GET(page.url, headers)

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
