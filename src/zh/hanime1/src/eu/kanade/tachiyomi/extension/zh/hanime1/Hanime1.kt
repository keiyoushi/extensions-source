package eu.kanade.tachiyomi.extension.zh.hanime1

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class Hanime1 : HttpSource() {
    override val baseUrl: String
        get() = "https://hanimeone.me"
    override val lang: String
        get() = "zh"
    override val name: String
        get() = "Hanime1.me"
    override val supportsLatest: Boolean
        get() = true

    private val comicHomepage = "$baseUrl/comics"

    override fun chapterListParse(response: Response): List<SChapter> {
        val requestUrl = response.request.url.toString()
        val document = response.asJsoup()
        val chapterList =
            document.select("h3:containsOwn(相關集數列表) ~ div.comic-rows-videos-div")
                .map { element ->
                    SChapter.create().apply {
                        val comicUrl = element.select("a").attr("abs:href")
                        setUrlWithoutDomain("$comicUrl/1")
                        val title = element.select("div.comic-rows-videos-title").text()
                        if (requestUrl == comicUrl) {
                            name = "當前：$title"
                        } else {
                            name = "關聯：$title"
                        }
                    }
                }
        if (chapterList.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    setUrlWithoutDomain("$requestUrl/1")
                    name = "單章節"
                },
            )
        }
        return chapterList
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("h3:containsOwn(最新上傳) ~ div.comic-rows-videos-div")
            .map { comicDivToManga(it) }
        val hasNextPage = document.select("ul.pagination a[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$comicHomepage?page=$page")

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val brief = document.select("h3.title.comics-metadata-top-row").first()?.parent()
        return SManga.create().apply {
            brief?.select(".title.comics-metadata-top-row")?.first()?.text()?.let { title = it }
            thumbnail_url =
                brief?.parent()?.select("div.col-md-4 img")?.attr("data-srcset")?.extraSrc()
            author = selectInfo("作者：", brief) ?: selectInfo("社團：", brief)
            genre = selectInfo("分類：", brief)
        }
    }

    private fun selectInfo(key: String, brief: Element?): String? = brief?.select(":containsOwn($key)")?.select("div.no-select")?.text()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val currentImage = document.select("img#current-page-image")
        val dataExtension = currentImage.attr("data-extension")
        val dataPrefix = currentImage.attr("data-prefix")
        val pageSize = document.select(".comic-show-content-nav").attr("data-pages").toInt()
        return List(pageSize) { index ->
            Page(index, imageUrl = "$dataPrefix${index + 1}.$dataExtension")
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("h3:containsOwn(發燒漫畫) ~ div.comic-rows-videos-div")
            .map { comicDivToManga(it) }
        return MangasPage(mangas, false)
    }

    override fun popularMangaRequest(page: Int) = GET(comicHomepage)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div#comics-search-tag-top-row + div div.comic-rows-videos-div")
            .map { comicDivToManga(it) }
        val hasNextPage = document.select("ul.pagination a[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = comicHomepage.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("query", query)
            .addQueryParameter("page", "$page")
        filters.firstInstanceOrNull<SortFilter>()?.selected?.let {
            searchUrl.addQueryParameter("sort", it)
        }
        return GET(searchUrl.build())
    }

    private fun comicDivToManga(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("abs:href"))
        title = element.select("div.comic-rows-videos-title").text()
        thumbnail_url = element.select("img").attr("data-srcset").extraSrc()
    }

    private fun String.extraSrc(): String = split(",").first()

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
    )
}
