package eu.kanade.tachiyomi.extension.en.mangajar

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.Single
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaJar : ParsedHttpSource() {

    override val name = "MangaJar"

    override val baseUrl = "https://mangajar.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga?sortBy=popular&page=$page")

    override fun popularMangaSelector() = "article[class*=flex-item]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("img").attr("title")
        thumbnail_url = element.select("img").let {
            if (it.hasAttr("data-src")) {
                it.attr("data-src")
            } else {
                it.attr("src")
            }
        }
    }

    override fun popularMangaNextPageSelector() = "a.page-link[rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga?sortBy=-last_chapter_at&page=$page")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.findInstance<GenreList>()
        val genre = genreFilter?.let { f -> f.values[f.state] }

        val url = (if (genre!!.isEmpty()) "$baseUrl/search" else "$baseUrl/genre/$genre").toHttpUrlOrNull()!!.newBuilder()

        url.addQueryParameter("q", query)
        url.addQueryParameter("page", page.toString())

        for (filter in filterList) {
            when (filter) {
                is OrderBy -> {
                    url.addQueryParameter("sortBy", filter.toUriPart())
                }
                is SortBy -> {
                    url.addQueryParameter("sortAscending", filter.toUriPart())
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("div.manga-description.entry").text()
        thumbnail_url = document.select("div.row > div > img").attr("src")
        genre = document.select("div.post-info > span > a[href*=genre]").joinToString { it.text() }
        status = parseStatus(document.select("span:has(b)")[1].text())
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Ended") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    /** For the first page. Pagination is done in [findChapters] */
    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/chaptersList")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return findChapters(chapterListRequest(manga)).toObservable()
    }

    private fun findChapters(request: Request): Single<List<SChapter>> {
        return client.newCall(request).asObservableSuccess().toSingle().flatMap { response ->
            val document = response.asJsoup()
            val thisPage = document.select(chapterListSelector()).map { chapter ->
                SChapter.create().apply {
                    val link = chapter.select("a")
                    url = link.attr("href")
                    name = link.text()
                    date_upload = parseChapterDate(chapter.select("span.chapter-date").text().trim())
                }
            }
            val nextPageLink = document.select("a.page-link[rel=\"next\"]").firstOrNull()
            if (nextPageLink == null) {
                Single.just(thisPage)
            } else {
                findChapters(GET("$baseUrl${nextPageLink.attr("href")}")).map { remainingChapters ->
                    thisPage + remainingChapters
                }
            }
        }
    }

    override fun chapterListSelector() = "li.list-group-item.chapter-item"

    override fun chapterFromElement(element: Element) = throw Exception("Not Used")

    private fun parseChapterDate(string: String): Long {
        return if ("ago" in string) {
            parseRelativeDate(string)
        } else {
            dateFormat.parse(string)?.time ?: 0L
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" ago").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "second" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    // Page List

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[data-page]").mapIndexed { i, element ->
            Page(i, "", if (element.hasAttr("data-src")) element.attr("abs:data-src") else element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    // Filters

    override fun getFilterList() = FilterList(
        OrderBy(),
        SortBy(),
        GenreList(),
    )

    private class SortBy : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Descending", "0"),
            Pair("Ascending", "1"),
        ),
    )

    private class OrderBy : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("Popularity", "popular"),
            Pair("Year", "year"),
            Pair("Alphabet", "name"),
            Pair("Date added", "published_at"),
            Pair("Date updated", "last_chapter_at"),
        ),
    )

    private class GenreList : Filter.Select<String>(
        "Select Genre",
        arrayOf(
            "",
            "Fantasy",
            "Adventure",
            "Martial Arts",
            "Action",
            "Demons",
            "Shounen",
            "Drama",
            "Isekai",
            "School Life",
            "Harem",
            "Horror",
            "Supernatural",
            "Mystery",
            "Sci-Fi",
            "Webtoons",
            "Romance",
            "Magic",
            "Slice of Life",
            "Seinen",
            "Historical",
            "Ecchi",
            "Comedy",
            "Sports",
            "Tragedy",
            "Shounen Ai",
            "Yaoi",
            "Shoujo",
            "Super Power",
            "Food",
            "Psychological",
            "Gender Bender",
            "Smut",
            "Shoujo Ai",
            "Yuri",
            "4-koma",
            "Mecha",
            "Adult",
            "Mature",
            "Military",
            "Vampire",
            "Kids",
            "Space",
            "Police",
            "Music",
            "One Shot",
            "Parody",
            "Josei",
        ),
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // The following date related code is taken directly from Genkan.kt
    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        }
    }
}
