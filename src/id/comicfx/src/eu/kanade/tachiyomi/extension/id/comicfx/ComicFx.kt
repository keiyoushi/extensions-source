package eu.kanade.tachiyomi.extension.id.comicfx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ComicFx : ParsedHttpSource() {

    override val name = "Comic Fx"
    override val baseUrl = "https://comicfx.net"
    override val lang = "id"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/filterList?page=$page&sortBy=name&asc=true", headers)
    }

    // combining selector for "popular" and "latest" to be used for "search" selector as it need both selector
    override fun popularMangaSelector() = "div.media, div.daftar-komik .komika"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".media-left a img, .komik-img a .batas img").attr("abs:data-src")
        manga.title = element.select(".media-body .media-heading a strong, .komik-des a h3").text()
        val item = element.select(".media-left a, div.komik-img a")
        manga.setUrlWithoutDomain(item.attr("href"))

        return manga
    }

    override fun popularMangaNextPageSelector() = ".pagination li a[rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-release?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        // Text search cannot use filters
        if (query.isNotEmpty()) {
            return client.newCall(GET("$baseUrl/search?query=$query"))
                .asObservableSuccess()
                .map { response ->
                    parseSearchApiResponse(response)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    private val json: Json by injectLazy()

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val results = json.parseToJsonElement(response.body.string()).jsonObject["suggestions"]!!.jsonArray
        val manga = results.map {
            SManga.create().apply {
                title = it.jsonObject["value"]!!.jsonPrimitive.content
                url = "/komik/${it.jsonObject["data"]!!.jsonPrimitive.content}"
            }
        }
        return MangasPage(manga, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val url = "$baseUrl/filterList".toHttpUrlOrNull()!!.newBuilder()

        for (filter in filterList) {
            when (filter) {
                is GenreFilter -> url.addQueryParameter("cat", filter.toUriPart())
                is SortFilter -> {
                    url.addQueryParameter("sortBy", filter.toUriPart())
                    url.addQueryParameter("asc", filter.state!!.ascending.toString())
                }
                is StatusFilter -> url.addQueryParameter("cstatus", filter.toUriPart())
                is TypeFilter -> url.addQueryParameter("ctype", filter.toUriPart())
                is AuthorFilter -> url.addQueryParameter("author", filter.state)
                is ArtistFilter -> url.addQueryParameter("artist", filter.state)

                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url.setPathSegment(0, "/latest-project".substring(1))
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }

        url.addQueryParameter("page", page.toString())
        // Unimplemented parameters: "alpha" (For filtering by alphabet) and "tag" (idk)
        return GET(url.toString())
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.select("#author").text()
        artist = document.select(".infolengkap span:contains(Artist) a, #artist").text()
        status = parseStatus(document.select(".infolengkap span:contains(status) i").text())
        description = document.select("div.sinopsis p").text()

        // Add series type (manga/manhwa/manhua/other) to genre
        val genres = document.select(".genre-komik a").map { it.text() }.toMutableList()
        document.selectFirst(".infokomik .type")?.ownText().takeIf { it.isNullOrBlank().not() }?.let { genres.add(it) }
        genre = genres.map { genre ->
            genre.lowercase(Locale.forLanguageTag(lang)).replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase(Locale.forLanguageTag(lang))
                } else {
                    char.toString()
                }
            }
        }
            .joinToString { it.trim() }

        thumbnail_url = document.select(".thumb img").attr("abs:src")
    }

    private fun parseStatus(element: String?): Int = when {
        element == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { it.contains(element, ignoreCase = true) } -> SManga.ONGOING
        listOf("completed").any { it.contains(element, ignoreCase = true) } -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListSelector() = "div.chaplist li .pull-left a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        // Add timestamp to latest chapter, taken from "Updated On". so source which not provide chapter timestamp will have atleast one
        val updateOn = document.select(".infokomik .infolengkap span:contains(update) b").text()
        val date = document.select(".infokomik .infolengkap span:contains(update)").text().substringAfter(updateOn)
        val checkChapter = document.select(chapterListSelector()).firstOrNull()
        if (date != "" && checkChapter != null && chapters[0].date_upload == 0L) {
            chapters[0].date_upload = SimpleDateFormat("dd mmm yyyy", Locale.ENGLISH).parse(date)?.time ?: 0L
        }

        return chapters
    }

    private fun parseDate(date: String): Long {
        return when (date) {
            "hari ini" -> System.currentTimeMillis()
            "kemarin" -> System.currentTimeMillis() - (1000 * 60 * 60 * 24) // yesterday
            else -> {
                try {
                    SimpleDateFormat("dd-mm-yyyy", Locale.ENGLISH).parse(date)?.time ?: 0L
                } catch (_: ParseException) {
                    0L
                }
            }
        }
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("span.chapternum")!!.text()
        date_upload = parseDate(element.selectFirst("span.chapterdate")!!.text())
    }

    // Pages
    override fun imageUrlParse(document: Document) = ""

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("#all img").mapIndexed { i, element ->
            val image = element.attr("abs:src")
            if (image != "") {
                pages.add(Page(i, "", image))
            }
        }

        return pages
    }

    // filters
    override fun getFilterList() = FilterList(
        SortFilter(sortList),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        ArtistFilter("Artist"),
        AuthorFilter("Author"),
        Filter.Separator(),
        Filter.Header("NOTE: Can't be used with other filter!"),
        Filter.Header("$name Project List page"),
        ProjectFilter(),
    )

    private class ArtistFilter(name: String) : Filter.Text(name)
    private class AuthorFilter(name: String) : Filter.Text(name)

    private class SortFilter(val sortables: List<Pair<String, String>>) : Filter.Sort("Sort", sortables.map { it.second }.toTypedArray(), Selection(1, false)) {
        fun toUriPart(): String {
            return sortables[this.state!!.index].first
        }
    }

    private val sortList = listOf(
        Pair("name", "Alphabetical"),
        Pair("views", "Popular"),
    )

    private class GenreFilter : UriPartFilter(
        "Select Genre",
        arrayOf(
            Pair("", "<select>"),
            Pair("1", "Action"),
            Pair("2", "Adventure"),
            Pair("3", "Comedy"),
            Pair("4", "Doujinshi"),
            Pair("5", "Drama"),
            Pair("6", "Ecchi"),
            Pair("7", "Fantasy"),
            Pair("8", "Gender Bender"),
            Pair("9", "Harem"),
            Pair("10", "Historical"),
            Pair("11", "Horror"),
            Pair("12", "Josei"),
            Pair("13", "Martial Arts"),
            Pair("14", "Mature"),
            Pair("15", "Mecha"),
            Pair("16", "Mystery"),
            Pair("17", "One Shot"),
            Pair("18", "Psychological"),
            Pair("19", "Romance"),
            Pair("20", "School Life"),
            Pair("21", "Sci-Fi"),
            Pair("22", "Seinen"),
            Pair("23", "Shoujo"),
            Pair("24", "Shoujo Ai"),
            Pair("25", "Shounen"),
            Pair("26", "Shounen Ai"),
            Pair("27", "Slice of Life"),
            Pair("28", "Sports"),
            Pair("29", "Supernatural"),
            Pair("30", "Tragedy"),
            Pair("34", "Smut"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("", "All"),
            Pair("1", "Ongoing"),
            Pair("2", "Complete"),
        ),
    )

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("", "All"),
            Pair("1", "Manhua (Chinese)"),
            Pair("2", "Manhwa (Korean)"),
            Pair("3", "Manga"),
            Pair("4", "Oneshot"),
        ),
    )

    private class ProjectFilter : UriPartFilter(
        "Filter Project",
        arrayOf(
            Pair("", "Show all manga"),
            Pair("project-filter-on", "Show only project manga"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }
}
