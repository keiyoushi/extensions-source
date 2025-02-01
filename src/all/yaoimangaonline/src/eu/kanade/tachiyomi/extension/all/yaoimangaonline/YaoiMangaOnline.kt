package eu.kanade.tachiyomi.extension.all.yaoimangaonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class YaoiMangaOnline : ParsedHttpSource() {
    override val lang = "all"

    override val name = "Yaoi Manga Online"

    override val baseUrl = "https://yaoimangaonline.com"

    // Popular is actually latest
    override val supportsLatest = false

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/page/$page/", headers)

    override fun popularMangaFromElement(element: Element) =
        searchMangaFromElement(element)

    override fun searchMangaSelector() =
        ".post:not(.category-gay-movies):not(.category-yaoi-anime) > div > a"

    override fun searchMangaNextPageSelector() = ".herald-pagination > .next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        baseUrl.toHttpUrl().newBuilder().run {
            filters.forEach {
                when (it) {
                    is CategoryFilter -> if (it.state != 0) {
                        addQueryParameter("cat", it.toString())
                    }
                    is TagFilter -> if (it.state != 0) {
                        addEncodedPathSegments("tag/$it")
                    }
                    else -> {}
                }
            }
            addEncodedPathSegments("page/$page")
            addQueryParameter("s", query)
            GET(toString(), headers)
        }

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            title = element.attr("title")
            setUrlWithoutDomain(element.attr("href"))
            thumbnail_url = element.selectFirst("img")?.attr("src")
        }

    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            title = document.select("h1.entry-title").text()
            title = title.substringBeforeLast("by").trim()
            thumbnail_url = document
                .selectFirst(".herald-post-thumbnail img")?.attr("src")
            description = document.select(".entry-content > p:not(:has(img)):not(:contains(You need to login))")
                .let { elements ->
                    elements.joinToString("\n\n") { it.wholeText() }
                }
            genre = document.select(".meta-tags > a").joinToString { it.text() }
            author = document.select(".entry-content > p:contains(Mangaka:)").text()
                .substringAfter("Mangaka:")
                .substringBefore("Language:")
                .trim()
        }

    override fun chapterListSelector() = ".mpp-toc a"

    override fun chapterFromElement(element: Element) =
        SChapter.create().apply {
            name = element.ownText()
            setUrlWithoutDomain(
                element.attr("href") ?: element.baseUri(),
            )
        }

    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).ifEmpty {
            SChapter.create().apply {
                name = "Chapter"
                url = response.request.url.encodedPath
            }.let(::listOf)
        }.reversed()

    override fun pageListParse(document: Document) =
        document.select(".entry-content img").mapIndexed { idx, img ->
            Page(idx, "", img.attr("src"))
        }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException()

    override fun getFilterList() =
        FilterList(CategoryFilter(), TagFilter())
}
