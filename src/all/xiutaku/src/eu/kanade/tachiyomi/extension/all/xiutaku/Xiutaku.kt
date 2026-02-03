package eu.kanade.tachiyomi.extension.all.xiutaku

import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class Xiutaku : ParsedHttpSource() {
    override val baseUrl = "https://xiutaku.com"
    override val lang = "all"
    override val name = "Xiutaku"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .setRandomUserAgent(UserAgentType.MOBILE)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    // Latest
    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        title = element.selectFirst(".item-content .item-link")!!.text()
        setUrlWithoutDomain(element.selectFirst(".item-content .item-link")!!.attr("abs:href"))
    }

    override fun latestUpdatesNextPageSelector() = ".pagination-next:not([disabled])"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?start=${20 * (page - 1)}", headers)

    override fun latestUpdatesSelector() = ".blog > div"

    // Popular
    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hot?start=${20 * (page - 1)}", headers)

    override fun popularMangaSelector() = latestUpdatesSelector()

    // Search
    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("page", (20 * (page - 1)).toString())
        }.build()
        return GET(searchUrl, headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".article-header")!!.text()
        description = document.selectFirst(".article-info:not(:has(small))")?.text()
        genre = document.selectFirst(".article-tags")
            ?.select(".tags > .tag")?.joinToString { it.text().removePrefix("#") }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dateUploadStr = document.selectFirst(".article-info > small")?.text()
        val dateUpload = DATE_FORMAT.tryParse(dateUploadStr)
        val maxPage =
            document.selectFirst(".pagination-list > span:last-child > a")?.text()?.toIntOrNull()
                ?: 1
        val basePageUrl = response.request.url
        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                setUrlWithoutDomain("$basePageUrl?page=$page")
                name = "Page $page"
                date_upload = dateUpload
            }
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = document.select(".article-fulltext img")
        .mapIndexed { i, imgEl -> Page(i, imageUrl = imgEl.attr("abs:src")) }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("H:m DD-MM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
