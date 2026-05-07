package eu.kanade.tachiyomi.extension.en.mangatown

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mangatown : HttpSource() {

    override val name = "Mangatown"

    override val baseUrl = "https://www.mangatown.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")

    private fun popularMangaSelector() = "li:has(a.manga_cover)"

    private fun popularMangaNextPageSelector() = "a.next:not([href^=javascript])"

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("p.title a").first()!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/directory/0-0-0-0-0-0/$page.htm", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/$page.htm", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("name", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapter_list li").map { element ->
            SChapter.create().apply {
                element.select("a").let { urlElement ->
                    setUrlWithoutDomain(urlElement.attr("href"))
                    name = "${urlElement.text()} ${element.select("span:not(span.time,span.new)").joinToString(" ") { it.text() }}"
                }
                date_upload = parseDate(element.select("span.time").text())
            }
        }
    }

    private val dateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.US)

    private fun parseDate(date: String): Long = when {
        date.contains("Today") -> Calendar.getInstance().timeInMillis
        date.contains("Yesterday") -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        else -> dateFormat.tryParse(date)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val elements = document.select("select#top_chapter_list ~ div.page_select option:not(:contains(featured))")
        return if (elements.isNotEmpty()) {
            elements.mapIndexed { i, e ->
                val path = e.attr("value")
                val pageUrl = if (path.startsWith("http")) path else baseUrl + path
                Page(i, url = pageUrl)
            }
        } else {
            document.select("div#viewer img").mapIndexed { i, e ->
                Page(i, imageUrl = e.attr("abs:src"))
            }
        }
    }

    override fun imageUrlParse(response: Response): String = response.asJsoup().select("div#viewer img").attr("abs:src")

    override fun getFilterList() = FilterList()
}
