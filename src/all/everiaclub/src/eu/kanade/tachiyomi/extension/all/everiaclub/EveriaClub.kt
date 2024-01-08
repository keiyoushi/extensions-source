package eu.kanade.tachiyomi.extension.all.everiaclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class EveriaClub() : ParsedHttpSource() {
    override val baseUrl = "https://everia.club"
    override val lang = "all"
    override val name = "Everia.club"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val Element.imgSrc: String
        get() = attr("data-lazy-src")
            .ifEmpty { attr("data-src") }
            .ifEmpty { attr("src") }

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img")!!.imgSrc
        manga.title = element.select(".entry-title").text()
        manga.setUrlWithoutDomain(element.select(".entry-title > a").attr("abs:href"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".next"
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page/")
    }

    override fun latestUpdatesSelector() = "#blog-entries > article"

    // Popular
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img")!!.imgSrc
        manga.title = element.select("h3").text()
        manga.setUrlWithoutDomain(element.select("h3 > a").attr("abs:href"))
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaSelector() = ".wli_popular_posts-class li"

    // Search
    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val tagFilter = filterList.findInstance<TagFilter>()!!
        val categoryFilter = filterList.findInstance<CategoryFilter>()!!
        return when {
            query.isEmpty() && categoryFilter.state != 0 -> GET("$baseUrl/category/${categoryFilter.toUriPart()}/page/$page/")
            query.isEmpty() && tagFilter.state.isNotEmpty() -> GET("$baseUrl/tag/${tagFilter.state}/page/$page/")
            query.isNotEmpty() && categoryFilter.state != 0 -> GET("$baseUrl/category/${categoryFilter.toUriPart()}/?paged=$page&s=$query")
            query.isNotEmpty() && tagFilter.state.isNotEmpty() -> GET("$baseUrl/tag/${tagFilter.state}/?paged=$page&s=$query")
            query.isNotEmpty() -> GET("$baseUrl/?paged=$page&s=$query")
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaSelector() = "#content > article"

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".entry-title").text()
        manga.description = document.select(".entry-title").text()
        val genres = mutableListOf<String>()
        document.select(".post-tags > a").forEach {
            genres.add(it.text())
        }
        manga.genre = genres.joinToString(", ")
        manga.status = SManga.COMPLETED
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("link[rel=\"canonical\"]").attr("href"))
        chapter.chapter_number = -2f
        chapter.name = "Gallery"
        chapter.date_upload = getDate(element.select("link[rel=\"canonical\"]").attr("href"))
        return chapter
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("noscript").remove()
        document.select(".entry-content img").forEachIndexed { i, it ->
            val itUrl = it.imgSrc
            pages.add(Page(i, itUrl, itUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
        fun toUriPart() = valuePair[state].second
    }

    class CategoryFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("Any", ""),
            Pair("Gravure", "/gravure"),
            Pair("Aidol", "/aidol"),
            Pair("Magazine", "/magazine"),
            Pair("Korea", "/korea"),
            Pair("Thailand", "/thailand"),
            Pair("Chinese", "/chinese"),
            Pair("Cosplay", "/cosplay"),
        ),
    )

    class TagFilter : Filter.Text("Tag")

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun getDate(str: String): Long {
        // At this point(4/12/22), this works with every everiaclub doc
        val regex = "[0-9]{4}\\/[0-9]{2}\\/[0-9]{2}".toRegex()
        val match = regex.find(str)
        return runCatching { DATE_FORMAT.parse(match!!.value)?.time }.getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy/MM/dd", Locale.US)
        }
    }
}
