package eu.kanade.tachiyomi.extension.ar.remanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class REManga : ParsedHttpSource() {

    override val name = "RE Manga"

    override val baseUrl = "https://re-manga.com"

    override val lang = "ar"

    override val supportsLatest = true

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga-list/?title=&order=popular&status=&type=")

    override fun popularMangaSelector() = "article.animpost"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(element.select("a").attr("abs:href"))
            element.select("img").let {
                thumbnail_url = it.attr("abs:src")
                title = it.attr("title")
            }
        }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga-list/?title=&order=update&status=&type=")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list/?".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("order", filter.toUriPart())

                is StatusFilter -> url.addQueryParameter("status", filter.toUriPart())

                is TypeFilter -> url.addQueryParameter("type", filter.toUriPart())

                is GenreFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }

                is YearFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { url.addQueryParameter("years[]", it.id) }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div.infox").first()!!.let { info ->
                title = info.select("h1").text()
            }
            description = document.select("div.desc > div > p").text()
            genre = document.select("div.spe > span:contains(نوع), div.genre-info > a").joinToString { it.text() }
            document.select("div.spe > span:contains(الحالة)").first()?.text()?.also { statusText ->
                when {
                    statusText.contains("مستمر", true) -> status = SManga.ONGOING
                    else -> status = SManga.COMPLETED
                }
            }
        }
    }

    // Chapters

    override fun chapterListSelector() = ".lsteps li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.select("a").first()!!.attr("abs:href"))

            val chNum = element.select(".eps > a").first()!!.text()
            val chTitle = element.select(".lchx > a").first()!!.text()

            name = when {
                chTitle.startsWith("الفصل ") -> chTitle
                else -> "الفصل $chNum - $chTitle"
            }

            element.select(".date").first()?.text()?.let { date ->
                date_upload = DATE_FORMATTER.parse(date)?.time ?: 0L
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-area img").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        SortFilter(getSortFilters()),
        StatusFilter(getStatusFilters()),
        TypeFilter(getTypeFilter()),
        Filter.Separator(),
        Filter.Header("exclusion not available for This source"),
        GenreFilter(getGenreFilters()),
        YearFilter(getYearFilters()),
    )

    private class SortFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Sort by", vals)

    private class TypeFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Type", vals)

    private class StatusFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Status", vals)

    class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    class Year(name: String, val id: String = name) : Filter.TriState(name)
    private class YearFilter(years: List<Year>) : Filter.Group<Year>("Year", years)

    private fun getSortFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("title", "A-Z"),
        Pair("titlereverse", "Z-A"),
        Pair("update", "Latest Update"),
        Pair("latest", "Latest Added"),
        Pair("popular", "Popular"),
    )

    private fun getStatusFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("", "All"),
        Pair("Publishing", "مستمر"),
        Pair("Finished", "تاريخ انتهي"),
    )

    private fun getTypeFilter(): Array<Pair<String?, String>> = arrayOf(
        Pair("", "All"),
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
    )

    private fun getGenreFilters(): List<Genre> = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Dementia", "dementia"),
        Genre("Demons", "demons"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Military", "military"),
        Genre("Mystery", "mystery"),
        Genre("Parody", "parody"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Samurai", "samurai"),
        Genre("School", "school"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Vampire", "vampire"),
    )

    private fun getYearFilters(): List<Year> = listOf(
        Year("1970", "1970"),
        Year("1986", "1986"),
        Year("1989", "1989"),
        Year("1995", "1995"),
        Year("1997", "1997"),
        Year("1998", "1998"),
        Year("1999", "1999"),
        Year("2000", "2000"),
        Year("2002", "2002"),
        Year("2003", "2003"),
        Year("2004", "2004"),
        Year("2005", "2005"),
        Year("2006", "2006"),
        Year("2007", "2007"),
        Year("2008", "2008"),
        Year("2009", "2009"),
        Year("2010", "2010"),
        Year("2011", "2011"),
        Year("2012", "2012"),
        Year("2013", "2013"),
        Year("2014", "2014"),
        Year("2016", "2016"),
        Year("2017", "2017"),
        Year("2018", "2018"),
        Year("2019", "2019"),
        Year("2020", "2020"),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String?, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMM d, yyy", Locale("ar"))
        }
    }
}
