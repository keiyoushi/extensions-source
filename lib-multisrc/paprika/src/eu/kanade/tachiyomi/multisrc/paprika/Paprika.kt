package eu.kanade.tachiyomi.multisrc.paprika

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class Paprika(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular-manga?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).mapNotNull { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaSelector() = "div.media"

    open fun popularMangaFromElement(element: Element): SManga? {
        val a = element.selectFirst("a:has(h4)") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(a.absUrl("href"))
            title = a.text()
            thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }
    }

    open fun popularMangaNextPageSelector(): String? = "a[rel=next]"

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-manga?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).mapNotNull { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun latestUpdatesFromElement(element: Element): SManga? = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        GET("$baseUrl/search?q=$query&page=$page", headers)
    } else {
        val url = "$baseUrl/mangas/".toHttpUrl().newBuilder()
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

    open fun searchMangaSelector() = popularMangaSelector()

    open fun searchMangaFromElement(element: Element): SManga? = popularMangaFromElement(element)

    open fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.manga-detail h1")!!.text()
        thumbnail_url = document.selectFirst("div.manga-detail img")?.attr("abs:src")
        document.select("div.media-body p").html().split("<br>").forEach {
            with(Jsoup.parse(it).text()) {
                when {
                    startsWith("Author") -> author = substringAfter(":").trim()
                    startsWith("Artist") -> artist = substringAfter(":").trim()
                    startsWith("Genre") -> genre = substringAfter(":").trim().replace(";", ",")
                    startsWith("Status") -> status = substringAfter(":").trim().toStatus()
                }
            }
        }
        description = document.select("div.manga-content p").joinToString("\n") { it.text() }
    }

    protected fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.selectFirst("div.manga-detail h1")?.text() ?: ""
        return document.select(chapterListSelector())
            .mapNotNull { chapterFromElement(it, mangaTitle) }
            .distinctBy { it.url }
    }

    open fun chapterListSelector() = "div.total-chapter:has(h2) li"

    open fun chapterFromElement(element: Element, mangaTitle: String): SChapter? {
        val a = element.selectFirst("a") ?: return null
        return SChapter.create().apply {
            name = a.text().substringAfter("$mangaTitle ")
            setUrlWithoutDomain(a.absUrl("href"))
            date_upload = element.selectFirst("div.small")?.text().toDate()
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
