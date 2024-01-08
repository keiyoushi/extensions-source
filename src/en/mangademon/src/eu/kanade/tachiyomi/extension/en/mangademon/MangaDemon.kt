package eu.kanade.tachiyomi.extension.en.mangademon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDemon : ParsedHttpSource() {

    override val lang = "en"
    override val supportsLatest = true
    override val name = "Manga Demon"
    override val baseUrl = "https://demoncomics.org"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("Accept-Encoding")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .addInterceptor(::dynamicUrlInterceptor)
        .build()

    // Cache suffix
    private var dynamicUrlSuffix = ""
    private var dynamicUrlSuffixUpdated: Long = 0
    private val dynamicUrlSuffixValidity: Long = 10 * 60 // 10 minutes

    private fun dynamicUrlInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val timeNow = System.currentTimeMillis() / 1000

        // Check if request requires an up-to-date suffix
        if (request.url.pathSegments[0] == "manga") {
            // Force update suffix if required
            if (timeNow - dynamicUrlSuffixUpdated > dynamicUrlSuffixValidity) {
                client.newCall(GET(baseUrl)).execute()
                if (timeNow - dynamicUrlSuffixUpdated > dynamicUrlSuffixValidity) {
                    throw IOException("Failed to update dynamic url suffix")
                }
            }

            val newPath = request.url
                .encodedPath
                .replaceAfterLast("-", dynamicUrlSuffix)

            val newUrl = request.url.newBuilder()
                .encodedPath(newPath)
                .build()

            val newRequest = request.newBuilder()
                .url(newUrl)
                .build()

            return chain.proceed(newRequest)
        }

        // Always update suffix
        val response = chain.proceed(request)
        val document = Jsoup.parse(
            response.peekBody(Long.MAX_VALUE).string(),
            request.url.toString(),
        )

        val links = document.select("a[href^='/manga/']")

        // Get the most popular suffix after last `-`
        val suffix = links.map { it.attr("href").substringAfterLast("-") }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key

        if (suffix != null) {
            dynamicUrlSuffix = suffix
            dynamicUrlSuffixUpdated = timeNow
        }

        return response
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/updates.php?list=$page", headers)
    }

    override fun latestUpdatesNextPageSelector() = ".pagination a:contains(Next)"

    override fun latestUpdatesSelector() = "div.leftside"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a").apply {
            title = attr("title")
            val url = URLEncoder.encode(attr("href"), "UTF-8")
            setUrlWithoutDomain(url)
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse.php?list=$page", headers)
    }

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.isNotEmpty()) {
            super.fetchSearchManga(page, query, filters)
        } else {
            client.newCall(filterSearchRequest(page, filters))
                .asObservableSuccess()
                .map(::filterSearchParse)
        }
    }

    private fun filterSearchRequest(page: Int, filters: FilterList): Request {
        val url = "$baseUrl/browse.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("list", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.checked.forEach { genre ->
                            addQueryParameter("genre[]", genre)
                        }
                    }
                    is StatusFilter -> {
                        addQueryParameter("status", filter.selected)
                    }
                    is SortFilter -> {
                        addQueryParameter("orderby", filter.selected)
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    private fun filterSearchParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = getFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "a.boxsizing"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.text()
        val url = URLEncoder.encode(element.attr("href"), "UTF-8")
        setUrlWithoutDomain(url)
        val urlSorter = title.replace(":", "%20")
        thumbnail_url = ("https://readermc.org/images/thumbnails/$urlSorter.webp")
    }

    override fun searchMangaNextPageSelector() = null

    // Manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("article")

        return SManga.create().apply {
            title = infoElement.select("h1.novel-title").text()
            author = infoElement.select("div.author").text().drop(7)
            status = parseStatus(infoElement.select("span:has(small:containsOwn(Status))").text())
            genre = infoElement.select("a.property-item").joinToString { it.text() }
            description = infoElement.select("p.description").text()
            thumbnail_url = infoElement.select("img#thumbonail").attr("src")
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chapter-list li"

    // Get Chapters
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let { urlElement ->
                val url = URLEncoder.encode(urlElement.attr("href"), "UTF-8")
                setUrlWithoutDomain(url)
                name = element.select("strong.chapter-title").text()
            }
            val date = element.select("time.chapter-update").text()
            date_upload = parseDate(date)
        }
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }

        private val loadMoreEndpointRegex by lazy { Regex("""GET[^/]+([^=]+)""") }
    }

    override fun pageListParse(document: Document): List<Page> {
        val baseImages = document.select("img.imgholder")
            .map { it.attr("abs:src") }
            .toMutableList()

        baseImages.addAll(loadMoreImages(document))

        return baseImages.mapIndexed { i, img -> Page(i, "", img) }
    }

    private fun loadMoreImages(document: Document): List<String> {
        val buttonHtml = document.selectFirst("img.imgholder ~ button")
            ?.attr("onclick")?.replace("\"", "\'")
            ?: return emptyList()

        val id = buttonHtml.substringAfter("\'").substringBefore("\'").trim()
        val funcName = buttonHtml.substringBefore("(").trim()

        val endpoint = document.selectFirst("script:containsData($funcName)")
            ?.data()
            ?.let { loadMoreEndpointRegex.find(it)?.groupValues?.get(1) }
            ?: return emptyList()

        val response = client.newCall(GET("$baseUrl$endpoint=$id", headers)).execute()

        if (!response.isSuccessful) {
            response.close()
            return emptyList()
        }

        return response.use { it.asJsoup() }
            .select("img")
            .map { it.attr("abs:src") }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
