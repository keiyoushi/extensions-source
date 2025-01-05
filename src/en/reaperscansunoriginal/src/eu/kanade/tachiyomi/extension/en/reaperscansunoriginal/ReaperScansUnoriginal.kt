package eu.kanade.tachiyomi.extension.en.reaperscansunoriginal

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class ReaperScansUnoriginal : ParsedHttpSource() {
    override val baseUrl = "https://reaper-scans.com"

    override val name = "Reaper Scans (unoriginal)"

    override val lang = "en"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    // Popular
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", OrderFilter.POPULAR)

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(page, "", OrderFilter.LATEST)

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // Search
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select(".poster-image-wrapper > img").attr("src")
        title = element.select(".info > a").text()
        setUrlWithoutDomain(element.selectFirst(".info a")!!.attr("href"))
    }

    override fun searchMangaNextPageSelector() = "a[rel=\"next\"]"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")
            addQueryParameter("s", query)
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".inner"

    override fun searchMangaParse(response: Response): MangasPage {
        if (genres.isEmpty()) {
            genres = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }

        return super.searchMangaParse(response)
    }

    // Chapter
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("title")
        date_upload = parseRelativeDate(element.selectFirst("span + span")?.text())
    }

    private fun parseRelativeDate(date: String?): Long {
        if (date == null) {
            return 0L
        }

        val trimmedDate = date.split(" ")
        if (trimmedDate.size != 3 && trimmedDate[2] != "ago") return 0L
        val number = trimmedDate[0].toIntOrNull() ?: return 0L
        val unit = trimmedDate[1].removeSuffix("s") // Remove 's' suffix
        val now = Calendar.getInstance()

        val javaUnit = when (unit) {
            "year", "yr" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "week", "wk" -> Calendar.WEEK_OF_MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hour", "hr" -> Calendar.HOUR
            "minute", "min" -> Calendar.MINUTE
            "second", "sec" -> Calendar.SECOND
            else -> return 0L
        }

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    override fun chapterListSelector() = "a.cairo"

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst("div.serie-info")?.let { info ->
            description = info.selectFirst("div.description-content")?.text()
            author = info.selectFirst("span:containsOwn(Author) + span")?.text()
            artist = info.selectFirst("span:containsOwn(Artist) + span")?.text()
            status = info.selectFirst("span:containsOwn(Status) + span")?.text().toStatus()
            genre = info.select("div.genre-link").joinToString { it.text() }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing") -> SManga.ONGOING
        this.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val chapterUrl = document.location()
        return document.select("div.image-skeleton img")
            .filterNot { it.attr("data-src").isEmpty() }
            .mapIndexed { i, img -> Page(i, chapterUrl, img.attr("data-src")) }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // Filter
    override fun getFilterList() = FilterList(
        TypeFilter(),
        Filter.Header("Press \"Reset\" to attempt to load genres"),
        GenreFilter(genres),
        YearFilter(),
        StatusFilter(),
        OrderFilter(),
    )

    private var genres = emptyList<Pair<String, String>>()

    private fun parseGenres(document: Document): List<Pair<String, String>> {
        return document.select("li:has(input[name=\"genre[]\"])")
            .map {
                Pair(it.selectFirst("label")!!.text(), it.selectFirst("input")!!.attr("value"))
            }
    }
}
