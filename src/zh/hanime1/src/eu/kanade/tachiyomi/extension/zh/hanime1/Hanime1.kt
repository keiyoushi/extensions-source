package eu.kanade.tachiyomi.extension.zh.hanime1

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Hanime1 : ParsedHttpSource() {
    override val baseUrl: String
        get() = "https://hanime1.me"
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
                        val comicUrl = element.select("a").attr("href")
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

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = comicDivToManga(element)

    override fun latestUpdatesNextPageSelector() = "ul.pagination a[rel=next]"

    override fun latestUpdatesRequest(page: Int) = GET("$comicHomepage?page=$page")

    override fun latestUpdatesSelector() = "h3:containsOwn(最新上傳) ~ div.comic-rows-videos-div"

    override fun mangaDetailsParse(document: Document): SManga {
        val brief = document.select("h3.title.comics-metadata-top-row").first()?.parent()
        return SManga.create().apply {
            brief?.select(".title.comics-metadata-top-row")?.first()?.text()?.let { title = it }
            thumbnail_url =
                brief?.parent()?.select("div.col-md-4 img")?.attr("data-srcset")?.extraSrc()
            author = selectInfo("作者：", brief) ?: selectInfo("社團：", brief)
            genre = selectInfo("分類：", brief)
        }
    }

    private fun selectInfo(key: String, brief: Element?): String? {
        return brief?.select(":containsOwn($key)")?.select("div.no-select")?.text()
    }

    override fun pageListParse(document: Document): List<Page> {
        val currentImage = document.select("img#current-page-image")
        val dataExtension = currentImage.attr("data-extension")
        val dataPrefix = currentImage.attr("data-prefix")
        val pageSize = document.select(".comic-show-content-nav").attr("data-pages").toInt()
        return List(pageSize) { index ->
            Page(index, imageUrl = "$dataPrefix${index + 1}.$dataExtension")
        }
    }

    override fun popularMangaFromElement(element: Element) = comicDivToManga(element)

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaRequest(page: Int) = GET(comicHomepage)

    override fun popularMangaSelector() = "h3:containsOwn(發燒漫畫) ~ div.comic-rows-videos-div"

    override fun searchMangaFromElement(element: Element) = comicDivToManga(element)

    override fun searchMangaNextPageSelector() = "ul.pagination a[rel=next]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = comicHomepage.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("query", query)
            .addQueryParameter("page", "$page")
        filters.filterIsInstance<SortFilter>().firstOrNull()?.selected?.let {
            searchUrl.addQueryParameter("sort", it)
        }
        return GET(searchUrl.build())
    }

    override fun searchMangaSelector() = "div#comics-search-tag-top-row + div div.comic-rows-videos-div"

    private fun comicDivToManga(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("div.comic-rows-videos-title").text()
        thumbnail_url = element.select("img").attr("data-srcset").extraSrc()
    }

    private fun String.extraSrc(): String {
        return split(",").first()
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            SortFilter(),
        )
    }
}
