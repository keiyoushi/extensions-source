package eu.kanade.tachiyomi.extension.en.manhwabuddy

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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaBuddy : ParsedHttpSource() {
    override val baseUrl = "https://manhwabuddy.com"
    override val lang = "en"
    override val name = "ManhwaBuddy"
    override val supportsLatest = true

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.selectFirst(".chapter-name")!!.text()
            date_upload = element.selectFirst(".ct-update")?.text().orEmpty().let { parseDate(it) }
        }
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
    }

    override fun chapterListSelector(): String = ".chapter-list a"

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
            title = element.selectFirst("h4")!!.text()
            thumbnail_url = element.selectFirst("img")?.attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector(): String = ".next"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page", headers)
    }

    override fun latestUpdatesSelector(): String = ".latest-list .latest-item"

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            val info = document.selectFirst(".main-info-right")!!
            author = info.selectFirst("li:contains(Author) a")?.text()
            status = when (info.selectFirst("li:contains(Status) span")?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            artist = info.selectFirst("li:contains(Artist) a")?.text()
            genre = info.select("li:contains(Genres) a").joinToString { it.text() }

            description = document.select(".short-desc-content p").joinToString("\n") { it.text() }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".loading").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
            title = element.selectFirst("h3")!!.text()
            thumbnail_url = element.selectFirst("img")?.attr("src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector(): String = ".item-move"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
            title = element.selectFirst("a")!!.attr("title")
            thumbnail_url = element.selectFirst("img")?.attr("src")
        }
    }

    override fun searchMangaNextPageSelector(): String = ".next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return GET(
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("search")
                    addQueryParameter("s", query)
                    addQueryParameter("page", page.toString())
                }.build(),
                headers,
            )
        }

        val genreFilter = filters.find { it is GenreFilter } as GenreFilter
        val genre = genreFilter.toUriPart()

        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("genre")
                addPathSegment(genre)
                addPathSegment("page")
                addPathSegment(page.toString())
            }.build(),
            headers,
        )
    }

    override fun searchMangaSelector(): String = ".latest-list .latest-item"

    // Filter
    override fun getFilterList() = FilterList(
        Filter.Header("Filter does not work with text search, reset it before filter"),
        Filter.Separator(),
        GenreFilter(),
    )

    // copy([...document.querySelectorAll(".nav-pc-list li a")].map((e) => `Pair("${e.textContent.trim()}", "${e.href.split("/").filter(Boolean).pop()}"),`).join("\n"))
    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("Action", "action"),
            Pair("Romance", "romance"),
            Pair("Drama", "drama"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("School Life", "school-life"),
            Pair("Smut", "smut"),
            Pair("Isekai", "isekai"),
            Pair("Thriller", "thriller"),
            Pair("Crime", "crime"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Horror", "horror"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Sports", "sports"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
