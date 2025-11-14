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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class MadTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 1, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val response = chain.proceed(request)
            if (!response.isSuccessful && url.fragment == "image-request") {
                response.close()
                val newUrl = url.newBuilder()
                    .host("sb.mbcdn.xyz")
                    .encodedPath(url.encodedPath.replaceFirst("/res/", "/"))
                    .fragment(null)
                    .build()

                return@addInterceptor chain.proceed(request.newBuilder().url(newUrl).build())
            }
            response
        }.build()

    protected open val useLegacyApi = false

    protected open val useSlugSearch = false

    // TODO: better cookie sharing
    // TODO: don't count cached responses against rate limit
    private val chapterClient: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 12, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
    }

    private var genreKey = "genre[]"

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
                                list.forEach { genre -> url.addQueryParameter(genreKey, genre.id) }
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

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = ".book-detailed-item"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        title = element.selectFirst("a")!!.attr("title")
        element.selectFirst(".summary")?.text()?.let { description = it }
        element.select(".genres > *").joinToString { it.text() }.takeIf { it.isNotEmpty() }?.let { genre = it }
        thumbnail_url = element.selectFirst("img")!!.attr("abs:data-src") + "#image-request"
    }

    /*
     * Only some sites use the next/previous buttons, so instead we check for the next link
     * after the active one. We use the :not() selector to exclude the optional next button
     */
    override fun searchMangaNextPageSelector(): String? = ".paginator > a.active + a:not([rel=next])"

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".detail h1")!!.text()
        author = document.select(".detail .meta > p > strong:contains(Authors) ~ a").joinToString { it.text().trim(',', ' ') }
        genre = document.select(".detail .meta > p > strong:contains(Genres) ~ a").joinToString { it.text().trim(',', ' ') }
        thumbnail_url = document.selectFirst("#cover img")!!.attr("abs:data-src") + "#image-request"

        val altNames = document.selectFirst(".detail h2")?.text()
            ?.split(',', ';')
            ?.mapNotNull { it.trim().takeIf { it != title } }
            ?: listOf()

        description = document.select(".summary .content, .summary .content ~ p").text() +
            (altNames.takeIf { it.isNotEmpty() }?.let { "\n\nAlt name(s): ${it.joinToString()}" } ?: "")

        val statusText = document.selectFirst(".detail .meta > p > strong:contains(Status) ~ a")!!.text()
        status = when (statusText.lowercase(Locale.ENGLISH)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
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
        if (response.request.url.fragment == "idFound") {
            return super.chapterListParse(response)
        }

        val document = response.asJsoup()

        val script = document.selectFirst("script:containsData(bookId)")
            ?: throw Exception("Cannot find script")
        val bookId = script.data().substringAfter("bookId = ").substringBefore(";")
        val bookSlug = script.data().substringAfter("bookSlug = \"").substringBefore("\";")

        var chaptersList = document.select(chapterListSelector()).map { chapterFromElement(it) }

        val fetchApi = document.selectFirst("div#show-more-chapters > span")
            ?.attr("onclick")?.equals("getChapters()")
            ?: false

        if (fetchApi) {
            val apiChapters = client.newCall(GET(buildChapterUrl(bookId, bookSlug), headers)).execute()
                .asJsoup().select(chapterListSelector()).map { chapterFromElement(it) }

            val cutIndex = chaptersList.indexOfFirst { chapter ->
                apiChapters.any { it.url == chapter.url }
            }.takeIf { it != -1 } ?: chaptersList.size

            chaptersList = (chaptersList.subList(0, cutIndex) + apiChapters)
        }

        return chaptersList
    }

    private fun buildChapterUrl(mangaId: String, mangaSlug: String): HttpUrl {
        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            addPathSegment("manga")
            addPathSegment(if (useSlugSearch) mangaSlug else mangaId)
            addPathSegment("chapters")
            addQueryParameter("source", "detail")
        }.build()
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (useLegacyApi) {
            val mangaId = MANGA_ID_REGEX.find(manga.url)?.groupValues?.get(1)
            val url = mangaId?.let {
                "$baseUrl/service/backend/chaplist/".toHttpUrl().newBuilder()
                    .addQueryParameter("manga_id", it)
                    .addQueryParameter("manga_name", manga.title)
                    .fragment("idFound")
                    .build()
                    .toString()
            } ?: (baseUrl + manga.url)

            return GET(url, headers)
        }
        return GET(baseUrl + manga.url, headers)
    }

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

        name = element.selectFirst(".chapter-title")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".chapter-update")?.text())
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val mangaId = MANGA_ID_REGEX.find(document.location())?.groupValues?.get(1)
        val chapterId = CHAPTER_ID_REGEX.find(document.html())?.groupValues?.get(1)

        val html = if (mangaId != null && chapterId != null) {
            val url = GET("$baseUrl/service/backend/chapterServer/?server_id=1&chapter_id=$chapterId", headers)
            client.newCall(url).execute().body.string()
        } else {
            document.html()
        }
        val realDocument = Jsoup.parse(html, document.location())

        if (!html.contains("var mainServer = \"")) {
            val chapterImagesFromHtml = realDocument.select("#chapter-images img, .chapter-image[data-src]")

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

    override fun imageRequest(page: Page): Request {
        return GET("${page.imageUrl}#image-request", headers)
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

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
            " ago" in date -> {
                parseRelativeDate(date)
            }
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = NUMBER_REGEX.find(date)?.groupValues?.getOrNull(0)?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("year") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            date.contains("month") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }

    // Dynamic genres
    private fun parseGenres(document: Document): List<Genre>? {
        return document.selectFirst(".checkbox-group.genres")?.select(".checkbox-wrapper")?.run {
            firstOrNull()?.selectFirst("input")?.attr("name")?.takeIf { it.isNotEmpty() }?.let { genreKey = it }
            map {
                Genre(it.selectFirst(".radio__label")!!.text(), it.selectFirst("input")!!.`val`())
            }
        }
    }

    // Filters
    override fun getFilterList() = FilterList(
        // TODO: Filters for sites that support it:
        // excluded genres
        // genre inclusion mode
        // bookmarks
        // author
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
            // Pair("Number of Chapters", "total_chapters"),
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

    companion object {
        private val MANGA_ID_REGEX = """/manga/(\d+)-""".toRegex()
        private val CHAPTER_ID_REGEX = """chapterId\s*=\s*(\d+)""".toRegex()
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
