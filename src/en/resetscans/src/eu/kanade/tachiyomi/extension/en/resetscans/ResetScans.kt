package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Calendar

class ResetScans : ParsedHttpSource() {
    override val name = "Reset Scans"

    override val baseUrl = "https://reset-scans.us"

    override val client: OkHttpClient = network.cloudflareClient
    override val lang = "en"
    override val supportsLatest = true

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            url = element.select("a").attr("href").substringAfter(baseUrl)
            name = element.select("div > a").text()
            // this source is weird and has two different ways of displaying the chapters release date
            val release = element.select(".chapter-release-date").text()
            // we only set date_upload on chapters with a "new" icon, because the "chapter release date" for old chapters doesn't have a year.
            // example: Jan 07
            if (release.isBlank()) {
                date_upload = element.select(".chapter-release-date > a").attr("title").parseRelativeDate()
            } else {
                date_upload = 0L
            }
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
    override fun chapterListSelector(): String = ".wp-manga-chapter"
    override fun chapterListRequest(manga: SManga): Request = POST("$baseUrl/${manga.url}ajax/chapters/")
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/mangas/page/$page/?m_orderby=latest")

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("img.img-responsive").attr("data-src")
            title = document.select(".post-title > h1").first()!!.text()
            genre = document.select(".genres-content").text()
            author = document.select(".author-content").text()
            artist = document.select(".artist-content").text()
            description = document.select(".summary__content").text()
            status = when (document.select(".post-status > .post-content_item > .summary-content").last()!!.text()) {
                null -> SManga.UNKNOWN
                "OnGoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                "Canceled" -> SManga.CANCELLED
                "On Hold" -> SManga.ON_HIATUS
                "Upcoming" -> SManga.ONGOING
                else -> { SManga.UNKNOWN }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".wp-manga-chapter-img").mapIndexed { index, page ->
            Page(index, "", page.attr("data-src"))
        }
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            thumbnail_url = element.select("img").attr("data-src")
            url = element.select("a").attr("abs:href").substringAfter(baseUrl)
            title = element.select("a").attr("title")
        }
    }

    override fun popularMangaNextPageSelector(): String? = ".nextpostslink"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/mangas/page/$page/?m_orderby=trending")

    override fun popularMangaSelector(): String = ".manga"
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = ".nextpostslink"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val actual = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/page/$page/?s=$actual&post_type=wp-manga")
    }

    override fun searchMangaSelector(): String = ".c-tabs-item__content"

    /**
     * Parses dates in this form: 21 hours ago
     * Taken from multisrc/madara/Madara.kt
     */
    private fun String.parseRelativeDate(): Long {
        val number = Regex("""(\d+)""").find(this)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            contains("week") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            contains("month") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            contains("year") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }
}
