package eu.kanade.tachiyomi.extension.en.mangareadercc

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Source
abstract class MangaReaderIN : HttpSource() {

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .addInterceptor(::chapterListInterceptor)
        .build()

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular-manga?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).mapNotNull { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaSelector() = "div.anipost"

    private fun popularMangaFromElement(element: Element): SManga? {
        val a = element.selectFirst("a:has(h3)") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(a.absUrl("href"))
            title = a.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }
    }

    private fun popularMangaNextPageSelector(): String? = "a[rel=next]"

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-manga?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).mapNotNull { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesSelector() = popularMangaSelector()

    private fun latestUpdatesFromElement(element: Element): SManga? = popularMangaFromElement(element)

    private fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        GET("$baseUrl/search?s=$query&post_type=manga&page=$page", headers)
    } else {
        val url = "$baseUrl/genres/".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addPathSegment(filter.toUriPart())
                is OrderFilter -> url.addQueryParameter("orderby", filter.toUriPart())
                else -> {}
            }
        }
        url.addQueryParameter("page", page.toString())
        GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).mapNotNull { searchMangaFromElement(it) }
        val hasNextPage = searchMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaSelector() = popularMangaSelector()

    private fun searchMangaFromElement(element: Element): SManga? = popularMangaFromElement(element)

    private fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".animeinfo .rm h1")!!.text()
        thumbnail_url = document.selectFirst(".animeinfo .lm img")?.attr("abs:src")
        document.select(".listinfo li").forEach { element ->
            with(element.text()) {
                when {
                    startsWith("Author") -> author = substringAfter(":").trim()
                    startsWith("Artist") -> artist = substringAfter(":").trim().replace(";", ",")
                    startsWith("Genre") -> genre = substringAfter(":").trim().replace(";", ",")
                    startsWith("Status") -> status = substringAfter(":").trim().toStatus()
                }
            }
        }
        description = document.select("#noidungm").joinToString("\n") { it.text() }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================

    private fun chapterListInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.fragment != "chapterList") {
            return chain.proceed(request)
        }

        val htmlRequest = request.newBuilder()
            .url(request.url.newBuilder().fragment(null).build())
            .build()
        val htmlResponse = chain.proceed(htmlRequest)
        val document = htmlResponse.asJsoup()
        val mangaId = document.selectFirst("script:containsData(var mangaID)")
            ?.data()
            ?.substringAfter("var mangaID = '")
            ?.substringBefore("';")
            ?: return htmlResponse.newBuilder()
                .body("".toResponseBody(htmlResponse.body.contentType()))
                .build()

        val mangaTitle = document.selectFirst("div.manga-detail h1")?.text() ?: ""
        val xhrRequest = GET(
            url = "$baseUrl/ajax-list-chapter?mangaID=$mangaId",
            headers = headers.newBuilder().add("X-Manga-Title", mangaTitle).build(),
        )

        return chain.proceed(xhrRequest)
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "#chapterList", headers)

    private fun chapterListSelector() = "li"

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaTitle = response.request.header("X-Manga-Title") ?: ""
        return response.asJsoup()
            .select(chapterListSelector())
            .mapNotNull { chapterFromElement(it, mangaTitle) }
            .distinctBy { it.url }
    }

    private fun chapterFromElement(element: Element, mangaTitle: String): SChapter? {
        val leftoff = element.selectFirst(".leftoff") ?: return null
        val a = leftoff.selectFirst("a") ?: return null
        return SChapter.create().apply {
            name = leftoff.text().substringAfter("$mangaTitle ")
            setUrlWithoutDomain(a.absUrl("href"))
            date_upload = element.selectFirst(".rightoff")?.text().toDate()
        }
    }

    protected fun String?.toDate(): Long {
        this ?: return 0L
        return try {
            when {
                contains("yesterday", ignoreCase = true) -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis

                contains("ago", ignoreCase = true) -> {
                    val trimmedDate = substringBefore(" ago").removeSuffix("s").split(" ")
                    val num = trimmedDate[0].toIntOrNull() ?: 1
                    val calendar = Calendar.getInstance()
                    when (trimmedDate[1]) {
                        "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -num) }
                        "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -num) }
                        "minute" -> calendar.apply { add(Calendar.MINUTE, -num) }
                        "second" -> calendar.apply { add(Calendar.SECOND, -num) }
                        else -> null
                    }?.timeInMillis ?: 0L
                }

                else -> {
                    val dateString = "${substringBefore(",")} $currentYear"
                    dateFormat.tryParse(dateString)
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    open fun pageListParse(document: Document): List<Page> = document.selectFirst("#arraydata")?.text()
        ?.takeIf { it.isNotEmpty() }
        ?.split(",")
        ?.mapIndexed { i, url -> Page(i, imageUrl = url) }
        ?: emptyList()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        OrderFilter(),
        GenreFilter(),
    )

    // ============================= Utilities =============================
    companion object {
        private val currentYear by lazy { Calendar.getInstance(Locale.US)[Calendar.YEAR].toString().takeLast(2) }

        private val dateFormat by lazy {
            SimpleDateFormat("MMM d yy", Locale.US)
        }
    }
}
