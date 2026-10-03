package eu.kanade.tachiyomi.extension.en.kaliscancom

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class KaliScanCom : KeiSource() {

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    // Intercepts chapter image requests that have a fallback URL encoded in the fragment.
    // If the primary CDN returns a failure, we retry with the fallback URL.
    // The fallback is encoded as a fragment so the parser doesn't need to pre-decide
    // which URL to use — the network layer handles it transparently.
    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()
        val fragment = request.url.fragment

        if (fragment != null && (fragment.startsWith("https://") || fragment.startsWith("http://"))) {
            val cleanUrl = request.url.newBuilder().fragment(null).build()
            val response = chain.proceed(request.newBuilder().url(cleanUrl).build())
            if (!response.isSuccessful) {
                response.close()
                return@addInterceptor chain.proceed(request.newBuilder().url(fragment).build())
            }
            return@addInterceptor response
        }
        chain.proceed(request)
    }
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
        }
        // chapter list API is heavily rate limited
        .rateLimit(1, 12.seconds) { it.encodedPath.startsWith("/service/backend/chaplist/") }
        .rateLimit(1, 1.seconds)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(OrderFilter(0)))

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(OrderFilter(1)))

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
                                list.forEach { genre -> url.addQueryParameter(filter.key, genre.id) }
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

        val document = client.get(url.build()).asJsoup()
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaSelector(): String = ".book-detailed-item"

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("a")!!.attr("title")
        element.selectFirst(".summary")?.text()?.let { description = it }
        element.select(".genres > *").joinToString { it.text() }.takeIf { it.isNotEmpty() }?.let { genre = it }
        thumbnail_url = element.selectFirst("img")!!.attr("abs:data-src") + "#image-request"
    }

    /*
     * Only some sites use the next/previous buttons, so instead we check for the next link
     * after the active one. We use the :not() selector to exclude the optional next button
     */
    private fun searchMangaNextPageSelector(): String = ".paginator > a.active + a:not([rel=next])"

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) mangaDetailsParse(client.get(getMangaUrl(manga)).asJsoup()) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".detail h1")!!.text()
        author = document.select(".detail .meta > p > strong:contains(Authors) ~ a").joinToString { it.text().trim(',', ' ') }
        genre = document.select(".detail .meta > p > strong:contains(Genres) ~ a").joinToString { it.text().trim(',', ' ') }
        thumbnail_url = document.selectFirst("#cover img")!!.attr("abs:data-src") + "#image-request"

        val altNames = document.selectFirst(".detail h2")?.text()
            ?.split(',', ';')
            ?.mapNotNull { it.trim().takeIf { it != title && it.isNotEmpty() } }
            ?: emptyList()

        description = buildString {
            append(document.select(".summary .content, .summary .content ~ p").text())
            if (altNames.isNotEmpty()) {
                append("\n\nAlt name(s): ")
                append(altNames.joinToString())
            }
        }

        val statusText = document.selectFirst(".detail .meta > p > strong:contains(Status) ~ a")!!.text()
        status = when (statusText.lowercase(Locale.ENGLISH)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters ==============================

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> = chapterListParse(client.get(chapterListUrl(manga)).asJsoup())

    private fun chapterListUrl(manga: SManga): String = MANGA_ID_REGEX.find(manga.url)?.groupValues?.get(1)?.let {
        "$baseUrl/service/backend/chaplist/".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", it)
            .addQueryParameter("manga_name", manga.title)
            .build()
            .toString()
    } ?: (baseUrl + manga.url)

    private suspend fun chapterListParse(document: Document): List<SChapter> {
        val requestUrl = document.location()

        if (requestUrl.contains("/service/backend/chaplist/")) {
            return document.select(chapterListSelector())
                .map { chapterFromElement(it) }
                .distinctBy { it.url }
        }

        var chaptersList = document.select(chapterListSelector()).map { chapterFromElement(it) }

        val fetchApi = document.selectFirst("div#show-more-chapters > span")
            ?.attr("onclick")?.equals("getChapters()")
            ?: false

        if (fetchApi) {
            val script = document.selectFirst("script:containsData(bookId)")
                ?: throw Exception("Cannot find script")
            val bookId = script.data().substringAfter("bookId = ").substringBefore(";")

            val apiChapters = client.get(buildChapterUrl(bookId)).asJsoup()
                .select(chapterListSelector()).map { element -> chapterFromElement(element) }

            val cutIndex = chaptersList.indexOfFirst { chapter ->
                apiChapters.any { it.url == chapter.url }
            }.takeIf { it != -1 } ?: chaptersList.size

            chaptersList = (chaptersList.subList(0, cutIndex) + apiChapters)
        }

        // distinctBy acts as a foolproof safeguard against any malformed URLs that fail to merge properly
        return chaptersList.distinctBy { it.url }
    }

    private fun chapterListSelector(): String = "#chapter-list > li"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        // Not using setUrlWithoutDomain() to support external chapters
        val rawUrl = element.selectFirst("a")!!.absUrl("href")

        // Strip the baseUrl and heavily normalize double slashes to prevent duplicate mismatching
        url = if (rawUrl.startsWith(baseUrl)) {
            rawUrl.substringAfter(baseUrl).replace(Regex("/{2,}"), "/")
        } else {
            rawUrl
        }

        name = element.selectFirst(".chapter-title")!!.text()
        date_upload = parseChapterDate(element.selectFirst(".chapter-update")?.text())
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // External chapter
        val chapterUrl = if (chapter.url.toHttpUrlOrNull() != null) chapter.url else baseUrl + chapter.url
        val document = client.get(chapterUrl).asJsoup()
        val mangaId = MANGA_ID_REGEX.find(document.location())?.groupValues?.get(1)
        val chapterId = CHAPTER_ID_REGEX.find(document.html())?.groupValues?.get(1)

        val html = if (mangaId != null && chapterId != null) {
            client.get("$baseUrl/service/backend/chapterServer/?server_id=1&chapter_id=$chapterId").body.string()
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
                        // from the JavaScript string.
                        return chapterImagesFromJs.mapIndexed { index, path ->
                            Page(index, imageUrl = path)
                        }
                    }
                }
            }

            // No fancy CDN, all images are available directly in <img> tags (hopefully)
            return chapterImagesFromHtml.mapIndexed { index, element ->
                Page(index, imageUrl = element.resolveImageUrl())
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

    override fun imageRequest(page: Page): Request = Request.Builder().url("${page.imageUrl}#image-request").headers(headers).build()

    // ============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = parseGenres(client.get("$baseUrl/search").asJsoup()).toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genreData = data?.parseAs<GenreData>()
        return FilterList(
            // TODO: Filters for sites that support it:
            // excluded genres
            // genre inclusion mode
            // bookmarks
            // author
            listOfNotNull(
                genreData?.let { GenreFilter(it.key, it.genres.map { (name, id) -> Genre(name, id) }) },
                StatusFilter(),
                OrderFilter(),
            ),
        )
    }

    @Serializable
    private class GenreData(val key: String, val genres: List<Pair<String, String>>)

    private class GenreFilter(val key: String, genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    private class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                Pair("All", "all"),
                Pair("Ongoing", "ongoing"),
                Pair("Completed", "completed"),
            ),
        )

    private class OrderFilter(state: Int = 0) :
        UriPartFilter(
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

    private open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    // ============================= Utilities =============================

    private fun buildChapterUrl(mangaId: String): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("api")
        addPathSegment("manga")
        addPathSegment(mangaId)
        addPathSegment("chapters")
        addQueryParameter("source", "detail")
    }.build()

    /**
     * Resolves the best available image URL from a chapter image element.
     *
     * For all images, we attempt to extract the site's own onerror fallback URL and
     * encode it as the fragment (mainUrl#fallbackUrl). The OkHttp interceptor will
     * then transparently retry with the fallback if the primary request fails.
     *
     * For known broken CDN servers (currently s20), the onerror fallback is returned
     * as the primary URL directly — no point trying s20 at all.
     *
     * Note: the fallback URL has a different path structure than data-src
     * (it omits the /toonily/ path segment), so we cannot simply swap the domain —
     * we must extract it from the onerror attribute directly.
     *
     * Example:
     *   data-src → https://s20.toonilycdnv2.xyz/toonily/manga/.../page.jpg
     *   onerror  → //sb.toonilycdnv2.xyz/manga/.../page.jpg?v=1  (different path!)
     */
    private fun Element.resolveImageUrl(): String {
        val dataSrc = attr("abs:data-src")
        val raw = attr("onerror")
            .substringAfter("this.src='", "")
            .substringBefore("'")
        val fallback = when {
            raw.startsWith("https://") || raw.startsWith("http://") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> return dataSrc // no onerror available, use data-src as-is
        }
        // For known broken servers, use the fallback as the primary URL directly.
        // For everything else, encode fallback as fragment for the interceptor to retry with on failure.
        return if ("://s20." in dataSrc) fallback else "$dataSrc#$fallback"
    }

    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        return when {
            " ago" in date -> {
                parseRelativeDate(date)
            }
            else -> dateFormat.tryParseDate(date)
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
    private fun parseGenres(document: Document): GenreData {
        val wrappers = document.selectFirst(".checkbox-group.genres")!!.select(".checkbox-wrapper")
        val key = wrappers.firstOrNull()?.selectFirst("input")?.attr("name")?.takeIf { it.isNotEmpty() } ?: "genre[]"
        val genres = wrappers.map {
            it.selectFirst(".radio__label")!!.text() to it.selectFirst("input")!!.`val`()
        }
        return GenreData(key, genres)
    }

    companion object {
        private val MANGA_ID_REGEX = """/manga/(\d+)-""".toRegex()
        private val CHAPTER_ID_REGEX = """chapterId\s*=\s*(\d+)""".toRegex()
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
