package eu.kanade.tachiyomi.extension.en.mangatown

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mangatown : ParsedHttpSource() {

    override val name = "Mangatown"

    override val baseUrl = "https://www.mangatown.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    override fun popularMangaSelector() = "li:has(a.manga_cover)"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/directory/0-0-0-0-0-0/$page.htm")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/$page.htm")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("p.title a").first()!!.let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next:not([href^=javascript])"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return POST("$baseUrl/search?page=$page&name=$query", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.article_content")

        return SManga.create().apply {
            title = infoElement.select("h1").text()
            author = infoElement.select("b:containsOwn(author) + a").text()
            artist = infoElement.select("b:containsOwn(artist) + a").text()
            status = if (infoElement.select("div.chapter_content:contains(has been licensed)").isNotEmpty()) {
                SManga.LICENSED
            } else {
                parseStatus(infoElement.select("li:has(b:containsOwn(status))").text())
            }
            genre = infoElement.select("li:has(b:containsOwn(genre)) a").joinToString { it.text() }
            description = document.select("span#show").text().removeSuffix("HIDE")
            thumbnail_url = document.select("div.detail_info img").attr("abs:src")
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chapter_list li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let { urlElement ->
                setUrlWithoutDomain(urlElement.attr("href"))
                name = "${urlElement.text()} ${element.select("span:not(span.time,span.new)").joinToString(" ") { it.text() }}"
            }
            date_upload = parseDate(element.select("span.time").text())
        }
    }

    private fun parseDate(date: String): Long {
        return when {
            date.contains("Today") -> Calendar.getInstance().apply {}.timeInMillis
            date.contains("Yesterday") -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
            else -> {
                try {
                    SimpleDateFormat("MMM dd,yyyy", Locale.US).parse(date)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("select#top_chapter_list ~ div.page_select option:not(:contains(featured))").mapIndexed { i, element ->
            Page(i, element.attr("value").substringAfter("com"))
        }
    }

    // Get the page
    override fun imageUrlRequest(page: Page) = GET(baseUrl + page.url)

    // Get the image from the requested page
    override fun imageUrlParse(response: Response): String {
        return response.asJsoup().select("div#viewer img").attr("abs:src")
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
