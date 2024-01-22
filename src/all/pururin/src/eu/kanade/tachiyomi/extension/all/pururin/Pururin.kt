package eu.kanade.tachiyomi.extension.all.pururin

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

abstract class Pururin(
    override val lang: String = "all",
    private val searchLang: String? = null,
    private val langPath: String = "",
) : ParsedHttpSource() {
    override val name = "Pururin"

    override val baseUrl = "https://pururin.to"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse$langPath?sort=most-popular&page=$page", headers)
    }

    override fun popularMangaSelector(): String = "a.card"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.attr("title")
            setUrlWithoutDomain(element.attr("abs:href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String = ".page-item [rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/browse$langPath?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // Search

    private fun List<String>.toValue(): String {
        return "[${this.joinToString(",")}]"
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val includeTags = mutableListOf<String>()
        val excludeTags = mutableListOf<String>()
        var pagesMin: Int
        var pagesMax: Int

        if (searchLang != null) includeTags.add(searchLang)

        filters.filterIsInstance<TagGroup<*>>().map { group ->
            group.state.map {
                if (it.isIncluded()) includeTags.add(it.id)
                if (it.isExcluded()) excludeTags.add(it.id)
            }
        }

        filters.find<PagesGroup>().range.let {
            pagesMin = it.first
            pagesMax = it.last
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", query)
            addQueryParameter("start_page", pagesMin.toString())
            addQueryParameter("last_page", pagesMax.toString())
            if (includeTags.isNotEmpty()) addQueryParameter("included_tags", includeTags.toValue())
            if (excludeTags.isNotEmpty()) addQueryParameter("excluded_tags", excludeTags.toValue())
            if (page > 1) addQueryParameter("page", page.toString())
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select(".box-gallery").let { e ->
                initialized = true
                title = e.select(".title").text()
                author = e.select("[itemprop=author]").text()
                description = e.select(".box-gallery .table-info tr")
                    .joinToString("\n") { tr ->
                        tr.select("td")
                            .joinToString(": ") { it.text() }
                    }
                thumbnail_url = e.select("img").attr("abs:src")
            }
        }
    }

    // Chapters

    override fun chapterListSelector(): String = ".table-collection tbody tr a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.text()
            setUrlWithoutDomain(element.attr("abs:href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select(chapterListSelector())
            .map { chapterFromElement(it) }
            .reversed()
            .let { list ->
                list.ifEmpty {
                    listOf(
                        SChapter.create().apply {
                            setUrlWithoutDomain(response.request.url.toString())
                            name = "Chapter"
                        },
                    )
                }
            }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".gallery-preview a img")
            .mapIndexed { i, img ->
                Page(i, "", (if (img.hasAttr("abs:src")) img.attr("abs:src") else img.attr("abs:data-src")).replace("t.", "."))
            }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        CategoryGroup(),
        PagesGroup(),
    )
}
