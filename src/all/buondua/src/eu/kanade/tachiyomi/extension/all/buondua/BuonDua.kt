package eu.kanade.tachiyomi.extension.all.buondua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class BuonDua() : ParsedHttpSource() {
    override val baseUrl = "https://buondua.com"
    override val lang = "all"
    override val name = "Buon Dua"
    override val supportsLatest = true

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        manga.title = element.select(".item-content .item-link").text()
        manga.setUrlWithoutDomain(element.select(".item-content .item-link").attr("abs:href"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".pagination-next:not([disabled])"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?start=${20 * (page - 1)}")
    }

    override fun latestUpdatesSelector() = ".blog > div"

    // Popular
    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hot?start=${20 * (page - 1)}")
    }
    override fun popularMangaSelector() = latestUpdatesSelector()

    // Search

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.findInstance<TagFilter>()!!
        return when {
            query.isNotEmpty() -> GET("$baseUrl/?search=$query&start=${20 * (page - 1)}")
            tagFilter.state.isNotEmpty() -> GET("$baseUrl/tag/${tagFilter.state}&start=${20 * (page - 1)}")
            else -> popularMangaRequest(page)
        }
    }
    override fun searchMangaSelector() = latestUpdatesSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".article-header").text()
        manga.description = document.select(".article-info > strong").text().trim()
        val genres = mutableListOf<String>()
        document.select(".article-tags").first()!!.select(".tags > .tag").forEach {
            genres.add(it.text().substringAfter("#"))
        }
        manga.genre = genres.joinToString(", ")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select(".is-current").first()!!.attr("abs:href"))
        chapter.chapter_number = 0F
        chapter.name = element.select(".article-header").text()
        chapter.date_upload = SimpleDateFormat("H:m DD-MM-yyyy", Locale.US).parse(element.select(".article-info > small").text())?.time ?: 0L
        return chapter
    }

    override fun chapterListSelector() = "html"

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val numpages = document.selectFirst(".pagination-list")!!.select(".pagination-link")
        val pages = mutableListOf<Page>()

        numpages.forEachIndexed { index, page ->
            val doc = when (index) {
                0 -> document
                else -> client.newCall(GET(page.attr("abs:href"))).execute().asJsoup()
            }
            doc.select(".article-fulltext img").forEach {
                val itUrl = it.attr("abs:src")
                pages.add(Page(pages.size, "", itUrl))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        TagFilter(),
    )

    class TagFilter : Filter.Text("Tag ID")

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
