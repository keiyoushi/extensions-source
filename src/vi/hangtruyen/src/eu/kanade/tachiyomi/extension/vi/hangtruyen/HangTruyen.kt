package eu.kanade.tachiyomi.extension.vi.hangtruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class HangTruyen : ParsedHttpSource() {
    override val name = "HangTruyen"
    override val lang = "vi"
    override val supportsLatest = true
    override val baseUrl = "https://hangtruyen.net"

    override val client = super.client.newBuilder()
        .rateLimit(5)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/tim-kiem?r=newly-updated&page=$page&orderBy=view_desc")

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!
        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    override fun popularMangaSelector() = "div.search-result .m-post"
    override fun popularMangaNextPageSelector() = ".next-page"

    // Latest
    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/tim-kiem?r=newly-updated&page=$page")

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = "div.search-result"
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title-detail a")!!.text().trim()
        author = document.selectFirst("div.author p")?.text()?.trim()
        description = document.selectFirst("div.sort-des div.line-clamp")?.text()?.trim()
        genre = document.select("div.kind a, div.m-tags a").joinToString(", ") { it.text().trim() }
        status = when (document.selectFirst("div.status p")?.text()?.trim()) {
            "Đang tiến hành" -> SManga.ONGOING
            "Hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.col-image img")?.attr("abs:src")
    }

    // Chapters
    override fun chapterListSelector() = "div.list-chapters div.l-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a.ll-chap")!!
        setUrlWithoutDomain(a.attr("href"))
        name = a.text().trim()
        date_upload = element.select("span.ll-update")[0].text().toDate()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#read-chaps .mi-item img.reading-img").mapIndexed { index, element ->
            val img = when {
                element.hasAttr("data-src") -> element.attr("abs:data-src")
                else -> element.attr("abs:src")
            }
            Page(index, imageUrl = img)
        }.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun String?.toDate(): Long {
        this ?: return 0L

        val secondWords = listOf("second", "giây")
        val minuteWords = listOf("minute", "phút")
        val hourWords = listOf("hour", "giờ")
        val dayWords = listOf("day", "ngày")
        val monthWords = listOf("month", "tháng")
        val yearWords = listOf("year", "năm")
        val agoWords = listOf("ago", "trước")

        return try {
            if (agoWords.any { this.contains(it, ignoreCase = true) }) {
                val trimmedDate = this.substringBefore(" ago").removeSuffix("s").split(" ")
                val calendar = Calendar.getInstance()

                when {
                    yearWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                    monthWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                    dayWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
                    hourWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
                    minuteWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
                    secondWords.doesInclude(trimmedDate[1]) -> calendar.apply { add(Calendar.SECOND, -trimmedDate[0].toInt()) }
                }

                calendar.timeInMillis
            } else {
                substringAfterLast(" ").let {
                    // timestamp has year
                    if (Regex("""\d+/\d+/\d\d""").find(it)?.value != null) {
                        dateFormat.parse(it)?.time ?: 0L
                    } else {
                        // MangaSum - timestamp sometimes doesn't have year (current year implied)
                        dateFormat.parse("$it/$currentYear")?.time ?: 0L
                    }
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private val currentYear by lazy { Calendar.getInstance(Locale.US)[1].toString().takeLast(2) }

    private fun List<String>.doesInclude(thisWord: String): Boolean = this.any { it.contains(thisWord, ignoreCase = true) }
}
