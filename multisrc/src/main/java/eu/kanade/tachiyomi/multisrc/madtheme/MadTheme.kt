package eu.kanade.tachiyomi.multisrc.madtheme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class MadTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd, yyy", Locale.US),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 1)
        .build()

    // TODO: better cookie sharing
    // TODO: don't count cached responses against rate limit
    private val chapterClient: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 12)
        .build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
    }

    private val json: Json by injectLazy()

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(OrderFilter(0)))

    override fun popularMangaParse(response: Response): MangasPage =
        searchMangaParse(response)

    override fun popularMangaSelector(): String =
        searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? =
        searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(OrderFilter(1)))

    override fun latestUpdatesParse(response: Response): MangasPage =
        searchMangaParse(response)

    override fun latestUpdatesSelector(): String =
        searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? =
        searchMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .let { list ->
                            if (list.isNotEmpty()) {
                                list.forEach { genre -> url.addQueryParameter("genre[]", genre.id) }
                            }
                        }
                }
                is StatusFilter -> {
                    url.addQueryParameter("status", filter.toUriPart())
                }
                is OrderFilter -> {
                    url.addQueryParameter("sort", filter.toUriPart())
                }
                else -> {}
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector(): String = ".book-detailed-item"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").first()!!.attr("abs:href"))
        title = element.select("a").first()!!.attr("title")
        description = element.select(".summary").first()?.text()
        genre = element.select(".genres > *").joinToString { it.text() }
        thumbnail_url = element.select("img").first()!!.attr("abs:data-src")
    }

    /*
     * Only some sites use the next/previous buttons, so instead we check for the next link
     * after the active one. We use the :not() selector to exclude the optional next button
     */
    override fun searchMangaNextPageSelector(): String? = ".paginator > a.active + a:not([rel=next])"

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select(".detail h1").first()!!.text()
        author = document.select(".detail .meta > p > strong:contains(Authors) ~ a").joinToString { it.text().trim(',', ' ') }
        genre = document.select(".detail .meta > p > strong:contains(Genres) ~ a").joinToString { it.text().trim(',', ' ') }
        thumbnail_url = document.select("#cover img").first()!!.attr("abs:data-src")

        val altNames = document.select(".detail h2").first()?.text()
            ?.split(',', ';')
            ?.mapNotNull { it.trim().takeIf { it != title } }
            ?: listOf()

        description = document.select(".summary .content").first()?.text() +
            (altNames.takeIf { it.isNotEmpty() }?.let { "\n\nAlt name(s): ${it.joinToString()}" } ?: "")

        val statusText = document.select(".detail .meta > p > strong:contains(Status) ~ a").first()!!.text()
        status = when (statusText.lowercase(Locale.US)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        // API is heavily rate limited. Use custom client
        return if (manga.status != SManga.LICENSED) {
            chapterClient.newCall(chapterListRequest(manga))
                .asObservable()
                .map { response ->
                    chapterListParse(response)
                }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.code in 200..299) {
            return super.chapterListParse(response)
        }

        // Try to show message/error from site
        response.body.let { body ->
            json.decodeFromString<JsonObject>(body.string())["message"]
                ?.jsonPrimitive
                ?.content
                ?.let { throw Exception(it) }
        }

        throw Exception("HTTP error ${response.code}")
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl/api/manga${manga.url}/chapters?source=detail", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        if (genresList == null) {
            genresList = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }
        return super.searchMangaParse(response)
    }

    override fun chapterListSelector(): String = "#chapter-list > li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        // Not using setUrlWithoutDomain() to support external chapters
        url = element.selectFirst("a")!!
            .absUrl("href")
            .removePrefix(baseUrl)

        name = element.select(".chapter-title").first()!!.text()
        date_upload = parseChapterDate(element.select(".chapter-update").first()?.text())
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()

        if (!html.contains("var mainServer = \"")) {
            val chapterImagesFromHtml = document.select("#chapter-images img")

            // 17/03/2023: Certain hosts only embed two pages in their "#chapter-images" and leave
            // the rest to be lazily(?) loaded by javascript. Let's extract `chapImages` and compare
            // the count against our select query. If both counts are the same, extract the original
            // images directly from the <img> tags otherwise pick the higher count. (heuristic)
            // First things first, let's verify `chapImages` actually exists.
            if (html.contains("var chapImages = '")) {
                val chapterImagesFromJs = html
                    .substringAfter("var chapImages = '")
                    .substringBefore("'")
                    .split(',')

                // Make sure chapter images we've got from javascript all have a host, otherwise
                // we've got no choice but to fallback to chapter images from HTML.
                // TODO: This might need to be solved one day ^
                if (chapterImagesFromJs.all { e ->
                    e.startsWith("http://") || e.startsWith("https://")
                }
                ) {
                    // Great, we can use these.
                    if (chapterImagesFromHtml.count() < chapterImagesFromJs.count()) {
                        // Seems like we've hit such a host, let's use the images we've obtained
                        // from the javascript string.
                        return chapterImagesFromJs.mapIndexed { index, path ->
                            Page(index, imageUrl = path)
                        }
                    }
                }
            }

            // No fancy CDN, all images are available directly in <img> tags (hopefully)
            return chapterImagesFromHtml.mapIndexed { index, element ->
                Page(index, imageUrl = element.attr("abs:data-src"))
            }
        }

        // While the site may support multiple CDN hosts, we have opted to ignore those
        val mainServer = html
            .substringAfter("var mainServer = \"")
            .substringBefore("\"")
        val schemePrefix = if (mainServer.startsWith("//")) "https:" else ""

        val chapImages = html
            .substringAfter("var chapImages = '")
            .substringBefore("'")
            .split(',')

        return chapImages.mapIndexed { index, path ->
            Page(index, imageUrl = "$schemePrefix$mainServer$path")
        }
    }

    // Image
    override fun pageListRequest(chapter: SChapter): Request {
        return if (chapter.url.toHttpUrlOrNull() != null) {
            // External chapter
            GET(chapter.url, headers)
        } else {
            super.pageListRequest(chapter)
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used.")

    // Date logic lifted from Madara
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            "ago".endsWith(date) -> {
                parseRelativeDate(date)
            }
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }

    // Dynamic genres
    private fun parseGenres(document: Document): List<Genre>? {
        return document.select(".checkbox-group.genres").first()?.select("label")?.map {
            Genre(it.select(".radio__label").first()!!.text(), it.select("input").`val`())
        }
    }

    // Filters
    override fun getFilterList() = FilterList(
        GenreFilter(getGenreList()),
        StatusFilter(),
        OrderFilter(),
    )

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private var genresList: List<Genre>? = null
    private fun getGenreList(): List<Genre> {
        // Filters are fetched immediately once an extension loads
        // We're only able to get filters after a loading the manga directory, and resetting
        // the filters is the only thing that seems to reinflate the view
        return genresList ?: listOf(Genre("Press reset to attempt to fetch genres", ""))
    }

    class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", "all"),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

    class OrderFilter(state: Int = 0) : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("Views", "views"),
            Pair("Updated", "updated_at"),
            Pair("Created", "created_at"),
            Pair("Name A-Z", "name"),
            Pair("Rating", "rating"),
        ),
        state,
    )

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }
}
