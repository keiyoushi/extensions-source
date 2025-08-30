package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class PhiliaScans : HttpSource() {

    override val name = "Philia Scans"
    override val baseUrl = "https://philiascans.org"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId: Int = 3

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")
            addQueryParameter("s", "")
            addQueryParameter("sort", "most_viewed")
            addQueryParameter("paged", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.unit").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst("li.page-item:not(.disabled) a.page-link[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleLink = element.selectFirst("a.c-title")!!
        title = titleLink.text()
        setUrlWithoutDomain(titleLink.attr("href"))
        thumbnail_url = element.selectFirst("a.poster div.poster-image-wrapper > img")?.attr("abs:src")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/recently-updated/?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")
            addQueryParameter("s", query)
            addQueryParameter("paged", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is SortBy -> {
                        val sort = filter.toUriPart()
                        if (sort.isNotEmpty()) addQueryParameter("sort", sort)
                    }
                    is TypeList ->
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("type[]", it.value) }
                    is YearList ->
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("release[]", it.name) }
                    is GenreList ->
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("genre[]", it.id) }
                    is GenreInclusion -> {
                        if (filter.state) addQueryParameter("genre_mode", "and")
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.serie-title")!!.text()
            author = document.selectFirst(".stat-item:has(.stat-label:contains(Author)) .stat-value")?.text()
            artist = document.selectFirst(".stat-item:has(.stat-label:contains(Artist)) .stat-value")?.text()
            genre = document.select("div.genre-list a").joinToString { it.text() }
            status = parseStatus(document.selectFirst(".stat-item:has(.stat-label:contains(Status)) span:not(.stat-label)")?.text())
            description = document.selectFirst("div.description-content")?.text()
            thumbnail_url = imageFromElement(document.selectFirst("div.main-cover img.cover"))
        }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("Releasing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        status.contains("On Hold", true) -> SManga.ON_HIATUS
        status.contains("Canceled", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select("li.item")
            .toList()
            .filter { it.selectFirst("a")?.attr("href")?.contains("#") == false } // Filter Coin Chapters
            .map { chapterFromElement(it) }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a")!!
        setUrlWithoutDomain(urlElement.attr("href"))
        name = element.selectFirst("zebi")!!.text().trim()
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().select("div#ch-images img")
            .mapIndexed { index, element ->
                Page(index, imageUrl = imageFromElement(element))
            }
    }

    private fun imageFromElement(element: Element?): String? {
        return when {
            element == null -> null
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            else -> element.attr("abs:src")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("NOTE: Filters are applied when you search."),
            SortBy(),
            TypeList(getTypeList()),
            YearList(getYearList()),
            Filter.Separator(),
            GenreList(getGenreList()),
            GenreInclusion(),
        )
    }

    private class SortBy : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Default", ""),
            Pair("Newest", "recently_added"),
            Pair("Alphabetical", "title_az"),
            Pair("Most Viewed", "most_viewed"),
        ),
    )

    private class Type(name: String, val value: String) : Filter.CheckBox(name)
    private class TypeList(types: List<Type>) : Filter.Group<Type>("Type", types)
    private fun getTypeList() = listOf(
        Type("Manga", "manga"),
        Type("Manhua", "manhua"),
        Type("Manhwa", "manhwa"),
        Type("Seinen", "seinen"),
    )

    private class Year(name: String) : Filter.CheckBox(name)
    private class YearList(years: List<Year>) : Filter.Group<Year>("Year", years)
    private fun getYearList() = listOf(
        Year("2025"),
        Year("2024"),
        Year("2023"),
        Year("2022"),
        Year("2021"),
        Year("2020"),
        Year("2019"),
    )

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class GenreInclusion : Filter.CheckBox("Must include all selected genres")

    private fun getGenreList() = listOf(
        Genre("Action", "29"),
        Genre("Adventure", "38"),
        Genre("Comedy", "42"),
        Genre("Crime", "30"),
        Genre("Drama", "34"),
        Genre("Ecchi", "39"),
        Genre("Fantasy", "43"),
        Genre("Gore", "157"),
        Genre("Gourmet", "188"),
        Genre("Harem", "46"),
        Genre("Historical", "40"),
        Genre("Horror", "44"),
        Genre("Isekai", "31"),
        Genre("Josei", "173"),
        Genre("Josei-None", "302"),
        Genre("Josei-Seinen", "174"),
        Genre("Magic", "87"),
        Genre("Martial Arts", "61"),
        Genre("Medical", "32"),
        Genre("Monsters", "99"),
        Genre("Music", "303"),
        Genre("Mystery", "35"),
        Genre("Psychological", "62"),
        Genre("Regression", "122"),
        Genre("Romance", "33"),
        Genre("School Life", "47"),
        Genre("Sci-Fi", "36"),
        Genre("Seinen", "48"),
        Genre("Shoujo", "69"),
        Genre("Shounen", "55"),
        Genre("Slice of Life", "37"),
        Genre("Sports", "45"),
        Genre("Supernatural", "49"),
        Genre("Survival", "121"),
        Genre("Tragedy", "41"),
        Genre("Villainess", "253"),
        Genre("War", "120"),
        Genre("Yuri", "195"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
