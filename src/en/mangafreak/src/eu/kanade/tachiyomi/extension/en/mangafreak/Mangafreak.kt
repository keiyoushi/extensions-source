package eu.kanade.tachiyomi.extension.en.mangafreak

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
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Mangafreak : ParsedHttpSource() {
    override val name: String = "Mangafreak"

    override val lang: String = "en"

    override val baseUrl: String = "https://w15.mangafreak.net"

    override val supportsLatest: Boolean = true

    private val floatLetterPattern = Regex("""(\d+)(\.\d+|[a-i]+\b)?""")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private fun mangaFromElement(element: Element, urlSelector: String): SManga {
        return SManga.create().apply {
            thumbnail_url = element.select("img").attr("abs:src")
            element.select(urlSelector).apply {
                title = text()
                url = attr("href")
            }
        }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/Genre/All/$page", headers)
    }
    override fun popularMangaNextPageSelector(): String = "a.next_p"
    override fun popularMangaSelector(): String = "div.ranking_item"
    override fun popularMangaFromElement(element: Element): SManga = mangaFromElement(element, "a")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/Latest_Releases/$page", headers)
    }
    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = "div.latest_releases_item"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src").replace("mini", "manga").substringBeforeLast("/") + ".jpg"
        element.select("a").apply {
            title = first()!!.text()
            url = attr("href")
        }
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()

        if (query.isNotBlank()) {
            url.addPathSegments("Find/$query")
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genres = filter.state.joinToString("") {
                        when (it.state) {
                            Filter.TriState.STATE_IGNORE -> "0"
                            Filter.TriState.STATE_INCLUDE -> "1"
                            Filter.TriState.STATE_EXCLUDE -> "2"
                            else -> "0"
                        }
                    }
                    url.addPathSegments("Genre/$genres")
                }
                is StatusFilter -> url.addPathSegments("Status/${filter.toUriPart()}")
                is TypeFilter -> url.addPathSegments("Type/${filter.toUriPart()}")
                else -> {}
            }
        }

        return GET(url.toString(), headers)
    }
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector(): String = "div.manga_search_item , div.mangaka_search_item"
    override fun searchMangaFromElement(element: Element): SManga = mangaFromElement(element, "h3 a, h5 a")

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.manga_series_image img").attr("abs:src")
        title = document.select("div.manga_series_data h5").text()
        status = when (document.select("div.manga_series_data > div:eq(2)").text()) {
            "ON-GOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = document.select("div.manga_series_data > div:eq(3)").text()
        artist = document.select("div.manga_series_data > div:eq(4)").text()
        genre = document.select("div.series_sub_genre_list a").joinToString { it.text() }
        description = document.select("div.manga_series_description p").text()
    }

    // Chapter

    // HTML response does not actually include a tbody tag, must select tr directly
    override fun chapterListSelector(): String = "div.manga_series_list tr:has(a)"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("td:eq(0)").text()

        /*
         * 123 -> 123
         * 123.4 -> 123.4
         * 123e -> 123.5 (a=1, b=2, ...)
         * j-z is undefined, assume straight substitution
         */
        val match = floatLetterPattern.find(name)
        chapter_number = if (match == null) {
            -1f
        } else {
            if (match.groupValues[2].isEmpty() || match.groupValues[2][0] == '.') {
                match.value.toFloat()
            } else {
                val sb = StringBuilder("0.")
                for (x in match.groupValues[2]) {
                    sb.append(x.code - 'a'.code + 1)
                }
                val p2 = sb.toString().toFloat()
                val p1 = match.groupValues[1].toFloat()

                p1 + p2
            }
        }

        setUrlWithoutDomain(element.select("a").attr("href"))
        date_upload = parseDate(element.select("td:eq(1)").text())
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(date)?.time ?: 0L
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("img#gohere[src]").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("abs:src")))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw Exception("Not Used")
    }

    // Filter

    private class Genre(name: String) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("Filters do not work if search bar is empty"),
        GenreFilter(getGenreList()),
        TypeFilter(),
        StatusFilter(),
    )
    private fun getGenreList() = listOf(
        Genre("Act"),
        Genre("Adult"),
        Genre("Adventure"),
        Genre("Ancients"),
        Genre("Animated"),
        Genre("Comedy"),
        Genre("Demons"),
        Genre("Drama"),
        Genre("Ecchi"),
        Genre("Fantasy"),
        Genre("Gender Bender"),
        Genre("Harem"),
        Genre("Horror"),
        Genre("Josei"),
        Genre("Magic"),
        Genre("Martial Arts"),
        Genre("Mature"),
        Genre("Mecha"),
        Genre("Military"),
        Genre("Mystery"),
        Genre("One Shot"),
        Genre("Psychological"),
        Genre("Romance"),
        Genre("School Life"),
        Genre("Sci Fi"),
        Genre("Seinen"),
        Genre("Shoujo"),
        Genre("Shoujoai"),
        Genre("Shounen"),
        Genre("Shounenai"),
        Genre("Slice Of Life"),
        Genre("Smut"),
        Genre("Sports"),
        Genre("Super Power"),
        Genre("Supernatural"),
        Genre("Tragedy"),
        Genre("Vampire"),
        Genre("Yaoi"),
        Genre("Yuri"),
    )

    private class TypeFilter : UriPartFilter(
        "Manga Type",
        arrayOf(
            Pair("Both", "0"),
            Pair("Manga", "2"),
            Pair("Manhwa", "1"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Manga Status",
        arrayOf(
            Pair("Both", "0"),
            Pair("Completed", "1"),
            Pair("Ongoing", "2"),
        ),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
