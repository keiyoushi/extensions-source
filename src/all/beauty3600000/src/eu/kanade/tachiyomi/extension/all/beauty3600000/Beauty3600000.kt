package eu.kanade.tachiyomi.extension.all.beauty3600000

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Beauty3600000 : ParsedHttpSource() {
    override val baseUrl = "https://3600000.xyz"
    override val lang = "all"
    override val name = "3600000 Beauty"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Latest
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    // Popular
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("a img.ls_lazyimg").attr("file")
        title = element.select(".entry-title").text()
        setUrlWithoutDomain(element.select(".entry-title > a").attr("abs:href"))
        status = SManga.COMPLETED
    }

    override fun popularMangaNextPageSelector() = ".next"
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page/", headers)
    override fun popularMangaSelector() = "#blog-entries > article"

    // Search
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val tagFilter = filterList.findInstance<TagFilter>()!!
        val categoryFilter = filterList.findInstance<CategoryFilter>()!!
        var searchQuery = query
        val searchPath: String = when {
            tagFilter.state.isNotEmpty() -> "$baseUrl/tag/${tagFilter.state}/page/$page/"
            categoryFilter.state != 0 -> "$baseUrl/category/${categoryFilter.toUriPart()}/page/$page/"
            query.startsWith("tag:") -> {
                tagFilter.state = searchQuery.substringAfter("tag:")
                searchQuery = ""
                "$baseUrl/tag/${tagFilter.state}/page/$page/"
            }
            else -> "$baseUrl/page/$page/"
        }
        return when {
            searchQuery.isNotEmpty() -> GET(
                searchPath.toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", searchQuery)
                }.build(),
                headers,
            )
            else -> GET(searchPath, headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val main = document.selectFirst("#main")!!
        title = main.select(".entry-title").text()
        description = main.select(".entry-title").text()
        genre = getGenres(document).joinToString(", ")
        thumbnail_url = main.select(".entry-content img.ls_lazyimg").attr("file")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun getGenres(element: Element): List<String> {
        val genres = mutableListOf<String>()
        element.select(".cat-links a").forEach {
            genres.add(it.text())
        }
        element.select(".tags-links a").forEach {
            val tag = it.attr("href").toHttpUrl().pathSegments[1]
            genres.add("tag:$tag")
        }
        return genres
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("link[rel=\"shortlink\"]").attr("href"))
        name = "Gallery"
        date_upload = getDate(element.select("#main time").attr("datetime"))
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("noscript").remove()
        document.select(".entry-content img").forEachIndexed { i, it ->
            val itUrl = it.select("img.ls_lazyimg").attr("file")
            pages.add(Page(i, imageUrl = itUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

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
            Pair("Gravure", "gravure"),
            Pair("Aidol", "aidol"),
            Pair("Magazine", "magazine"),
            Pair("Korea", "korea"),
            Pair("Thailand", "thailand"),
            Pair("Chinese", "chinese"),
            Pair("Japan", "japan"),
            Pair("China", "china"),
            Pair("Uncategorized", "uncategorized"),
            Pair("Magazine", "magazine"),
            Pair("Photobook", "photobook"),
            Pair("Western", "western"),
        ),
    )

    class TagFilter : Filter.Text("Tag")

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun getDate(str: String): Long {
        return try {
            DATE_FORMAT.parse(str).time
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
    }
}
