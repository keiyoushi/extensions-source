package eu.kanade.tachiyomi.extension.en.mangafox

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaFox : ParsedHttpSource() {

    override val name: String = "MangaFox"

    override val baseUrl: String = "https://fanfox.net"

    private val mobileUrl: String = "https://m.fanfox.net"

    override val lang: String = "en"

    override val supportsLatest: Boolean = true

    private val json by injectLazy<Json>()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 1)
        // Force readway=2 cookie to get all page URLs at once
        .cookieJar(
            object : CookieJar {
                private val cookieManager by lazy { CookieManager.getInstance() }

                init {
                    cookieManager.setCookie(mobileUrl.toHttpUrl().host, "readway=2")
                    cookieManager.setCookie(baseUrl.toHttpUrl().host, "isAdult=1")
                }

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val urlString = url.toString()
                    cookies.forEach { cookieManager.setCookie(urlString, it.toString()) }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookies = cookieManager.getCookie(url.toString())

                    return if (cookies != null && cookies.isNotEmpty()) {
                        cookies.split(";").mapNotNull {
                            Cookie.parse(url, it)
                        }
                    } else {
                        emptyList()
                    }
                }
            },
        )
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "$page.html" else ""
        return GET("$baseUrl/directory/$pageStr", headers)
    }

    override fun popularMangaSelector(): String = "ul.manga-list-1-list li"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("a").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.attr("title")
            thumbnail_url = it.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String = ".pager-list-left a.active + a + a"

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page != 1) "$page.html" else ""
        return GET("$baseUrl/directory/$pageStr?latest", headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = mutableListOf<Int>()
        val genresEx = mutableListOf<Int>()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("title", query)
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is UriPartFilter -> addQueryParameter(filter.query, filter.toUriPart())
                    is GenreFilter -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> genres.add(it.id)
                            Filter.TriState.STATE_EXCLUDE -> genresEx.add(it.id)
                            else -> {}
                        }
                    }
                    is FilterWithMethodAndText -> {
                        val method = filter.state[0] as UriPartFilter
                        val text = filter.state[1] as TextSearchFilter
                        addQueryParameter(method.query, method.toUriPart())
                        addQueryParameter(text.query, text.state)
                    }
                    is RatingFilter -> filter.state.forEach {
                        addQueryParameter(it.query, it.toUriPart())
                    }
                    is TextSearchFilter -> addQueryParameter(filter.query, filter.state)
                    else -> {}
                }
            }
            addQueryParameter("genres", genres.joinToString(","))
            addQueryParameter("nogenres", genresEx.joinToString(","))
            addQueryParameter("sort", "")
            addQueryParameter("stype", "1")
        }.build().toString()
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = "ul.manga-list-4-list li"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        document.select(".detail-info-right").first()!!.let {
            author = it.select(".detail-info-right-say a").joinToString(", ") { it.text() }
            genre = it.select(".detail-info-right-tag-list a").joinToString(", ") { it.text() }
            description = it.select("p.fullcontent").first()?.text()
            status = it.select(".detail-info-right-title-tip").first()?.text().orEmpty().let { parseStatus(it) }
            thumbnail_url = document.select(".detail-info-cover-img").first()?.attr("abs:src")
        }
    }

    override fun chapterListSelector() = "ul.detail-main-list li a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select(".detail-main-list-main p").first()?.text().orEmpty()
        date_upload = element.select(".detail-main-list-main p").last()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String): Long {
        return if ("Today" in date || " ago" in date) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else if ("Yesterday" in date) {
            Calendar.getInstance().apply {
                add(Calendar.DATE, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            runCatching {
                SimpleDateFormat("MMM d,yyyy", Locale.ENGLISH).parse(date)?.time
            }.getOrNull() ?: 0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mobilePath = chapter.url.replace("/manga/", "/roll_manga/")

        val headers = headersBuilder().set("Referer", "$mobileUrl/").build()

        return GET("$mobileUrl$mobilePath", headers)
    }

    override fun pageListParse(document: Document): List<Page> =
        document.select("#viewer img").mapIndexed { idx, it ->
            Page(idx, imageUrl = it.attr("abs:data-original"))
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(): FilterList = FilterList(
        NameFilter(),
        EntryTypeFilter(),
        CompletedFilter(),
        AuthorFilter(),
        ArtistFilter(),
        RatingFilter(),
        YearFilter(),
        GenreFilter(getGenreList()),
    )

    open class UriPartFilter(
        name: String,
        val query: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    open class TextSearchMethodFilter(name: String, query: String) :
        UriPartFilter(name, query, arrayOf(Pair("contain", "cw"), Pair("begin", "bw"), Pair("end", "ew")))

    open class TextSearchFilter(name: String, val query: String) : Filter.Text(name)

    open class FilterWithMethodAndText(name: String, state: List<Filter<*>>) :
        Filter.Group<Filter<*>>(name, state)

    private class NameFilter : TextSearchFilter("Name", "name")

    private class EntryTypeFilter : UriPartFilter(
        "Type",
        "type",
        arrayOf(
            Pair("Any", "0"),
            Pair("Japanese Manga", "1"),
            Pair("Korean Manhwa", "2"),
            Pair("Chinese Manhua", "3"),
            Pair("European Manga", "4"),
            Pair("American Manga", "5"),
            Pair("HongKong Manga", "6"),
            Pair("Other Manga", "7"),
        ),
    )

    private class AuthorMethodFilter : TextSearchMethodFilter("Method", "author_method")

    private class AuthorTextFilter : TextSearchFilter("Author", "author")

    private class AuthorFilter : FilterWithMethodAndText("Author", listOf(AuthorMethodFilter(), AuthorTextFilter()))

    private class ArtistMethodFilter : TextSearchMethodFilter("Method", "artist_method")

    private class ArtistTextFilter : TextSearchFilter("Artist", "artist")

    private class ArtistFilter : FilterWithMethodAndText("Artist", listOf(ArtistMethodFilter(), ArtistTextFilter()))

    private class RatingMethodFilter : UriPartFilter(
        "Method",
        "rating_method",
        arrayOf(
            Pair("is", "eq"),
            Pair("less than", "lt"),
            Pair("more than", "gt"),
        ),
    )

    private class RatingValueFilter : UriPartFilter(
        "Rating",
        "rating",
        arrayOf(
            Pair("any star", ""),
            Pair("no star", "0"),
            Pair("1 star", "1"),
            Pair("2 stars", "2"),
            Pair("3 stars", "3"),
            Pair("4 stars", "4"),
            Pair("5 stars", "5"),
        ),
    )

    private class RatingFilter : Filter.Group<UriPartFilter>("Rating", listOf(RatingMethodFilter(), RatingValueFilter()))

    private class YearMethodFilter : UriPartFilter(
        "Method",
        "released_method",
        arrayOf(
            Pair("on", "eq"),
            Pair("before", "lt"),
            Pair("after", "gt"),
        ),
    )

    private class YearTextFilter : TextSearchFilter("Release year", "released")

    private class YearFilter : FilterWithMethodAndText("Release year", listOf(YearMethodFilter(), YearTextFilter()))

    private class CompletedFilter : UriPartFilter(
        "Completed Series",
        "st",
        arrayOf(
            Pair("Either", "0"),
            Pair("Yes", "2"),
            Pair("No", "1"),
        ),
    )

    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    // console.log([...document.querySelectorAll(".tag-box a")].map(e => `Genre("${e.innerHTML}", ${e.dataset.val})`).join(",\n"))
    private fun getGenreList() = listOf(
        Genre("Action", 1),
        Genre("Adventure", 2),
        Genre("Comedy", 3),
        Genre("Drama", 4),
        Genre("Fantasy", 5),
        Genre("Martial Arts", 6),
        Genre("Shounen", 7),
        Genre("Horror", 8),
        Genre("Supernatural", 9),
        Genre("Harem", 10),
        Genre("Psychological", 11),
        Genre("Romance", 12),
        Genre("School Life", 13),
        Genre("Shoujo", 14),
        Genre("Mystery", 15),
        Genre("Sci-fi", 16),
        Genre("Seinen", 17),
        Genre("Tragedy", 18),
        Genre("Ecchi", 19),
        Genre("Sports", 20),
        Genre("Slice of Life", 21),
        Genre("Mature", 22),
        Genre("Shoujo Ai", 23),
        Genre("Webtoons", 24),
        Genre("Doujinshi", 25),
        Genre("One Shot", 26),
        Genre("Smut", 27),
        Genre("Yaoi", 28),
        Genre("Josei", 29),
        Genre("Historical", 30),
        Genre("Shounen Ai", 31),
        Genre("Gender Bender", 32),
        Genre("Adult", 33),
        Genre("Yuri", 34),
        Genre("Mecha", 35),
        Genre("Lolicon", 36),
        Genre("Shotacon", 37),
    )
}
