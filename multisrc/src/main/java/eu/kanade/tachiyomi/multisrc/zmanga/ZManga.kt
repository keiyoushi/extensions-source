package eu.kanade.tachiyomi.multisrc.zmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

abstract class ZManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    protected fun pagePathSegment(page: Int): String = if (page > 1) "page/$page/" else ""

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/advanced-search/${pagePathSegment(page)}?order=popular")
    }

    override fun popularMangaSelector() = "div.flexbox2-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.flexbox2-content a").attr("href"))
            title = element.select("div.flexbox2-title > span").first()!!.text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "div.pagination .next"

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/advanced-search/${pagePathSegment(page)}?order=update")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/advanced-search/${pagePathSegment(page)}".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("title", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url = "$baseUrl$projectPageString/page/$page".toHttpUrlOrNull()!!.newBuilder()
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    open val projectPageString = "/project-list"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("div.series-thumb img").attr("abs:src")
            author = document.select(".series-infolist li:contains(Author) span").text()
            artist = document.select(".series-infolist li:contains(Artist) span").text()
            status = parseStatus(document.select(".series-infoz .status").firstOrNull()?.ownText())
            description = document.select("div.series-synops").text()
            genre = document.select("div.series-genres a").joinToString { it.text() }

            // add series type(manga/manhwa/manhua/other) thinggy to genre
            document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
                if (it.isEmpty().not() && it != "-" && genre!!.contains(it, true).not()) {
                    genre += if (genre!!.isEmpty()) it else ", $it"
                }
            }

            // add alternative name to manga description
            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isBlank().not()) {
                    description = when {
                        description.isNullOrBlank() -> altName + it
                        else -> description + "\n\n$altName" + it
                    }
                }
            }
        }
    }

    open val seriesTypeSelector = "div.block span.type"
    open val altNameSelector = ".series-title span"
    open val altName = "Alternative Name" + ": "

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    // chapters
    // careful not to include download links
    override fun chapterListSelector() = "ul.series-chapterlist div.flexch-infoz a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("span").first()!!.ownText()
            date_upload = parseDate(element.select("span.date").text())
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-area img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    open val hasProjectPage = false

    // filters

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("You can combine filter."),
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreList(getGenreList()),
        )
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header("NOTE: cant be used with other filter!"),
                    Filter.Header("$name Project List page"),
                    ProjectFilter(),
                ),
            )
        }
        return FilterList(filters)
    }

    protected class ProjectFilter : UriPartFilter(
        "Filter Project",
        arrayOf(
            Pair("Show all manga", ""),
            Pair("Show only project manga", "project-filter-on"),
        ),
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("One-Shot", "One-Shot"),
            Pair("Doujin", "Doujin"),
        ),
    )
    private class OrderByFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating"),
        ),
    )

    private fun getGenreList() = listOf(
        Tag("4-koma", "4-Koma"),
        Tag("4-koma-comedy", "4-Koma Comedy"),
        Tag("action", "Action"),
        Tag("adult", "Adult"),
        Tag("adventure", "Adventure"),
        Tag("comedy", "Comedy"),
        Tag("demons", "Demons"),
        Tag("drama", "Drama"),
        Tag("ecchi", "Ecchi"),
        Tag("fantasy", "Fantasy"),
        Tag("game", "Game"),
        Tag("gender-bender", "Gender bender"),
        Tag("gore", "Gore"),
        Tag("harem", "Harem"),
        Tag("historical", "Historical"),
        Tag("horror", "Horror"),
        Tag("isekai", "Isekai"),
        Tag("josei", "Josei"),
        Tag("loli", "Loli"),
        Tag("magic", "Magic"),
        Tag("manga", "Manga"),
        Tag("manhua", "Manhua"),
        Tag("manhwa", "Manhwa"),
        Tag("martial-arts", "Martial Arts"),
        Tag("mature", "Mature"),
        Tag("mecha", "Mecha"),
        Tag("military", "Military"),
        Tag("monster-girls", "Monster Girls"),
        Tag("music", "Music"),
        Tag("mystery", "Mystery"),
        Tag("one-shot", "One Shot"),
        Tag("parody", "Parody"),
        Tag("police", "Police"),
        Tag("psychological", "Psychological"),
        Tag("romance", "Romance"),
        Tag("school", "School"),
        Tag("school-life", "School Life"),
        Tag("sci-fi", "Sci-Fi"),
        Tag("socks", "Socks"),
        Tag("seinen", "Seinen"),
        Tag("shoujo", "Shoujo"),
        Tag("shoujo-ai", "Shoujo Ai"),
        Tag("shounen", "Shounen"),
        Tag("shounen-ai", "Shounen Ai"),
        Tag("slice-of-life", "Slice of Life"),
        Tag("smut", "Smut"),
        Tag("sports", "Sports"),
        Tag("super-power", "Super Power"),
        Tag("supernatural", "Supernatural"),
        Tag("survival", "Survival"),
        Tag("thriller", "Thriller"),
        Tag("tragedy", "Tragedy"),
        Tag("vampire", "Vampire"),
        Tag("webtoons", "Webtoons"),
        Tag("yuri", "Yuri"),
    )

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
}
