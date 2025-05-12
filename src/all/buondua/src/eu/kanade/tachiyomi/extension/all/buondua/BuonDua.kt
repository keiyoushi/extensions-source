package eu.kanade.tachiyomi.extension.all.buondua

import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
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
import java.util.concurrent.TimeUnit

class BuonDua() : ParsedHttpSource() {
    override val baseUrl = "https://buondua.com"
    override val lang = "all"
    override val name = "Buon Dua"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .setRandomUserAgent(UserAgentType.MOBILE)
        .build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        manga.title = element.select(".item-content .item-link").text()
        manga.setUrlWithoutDomain(element.select(".item-content .item-link").attr("abs:href"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".pagination-next:not([disabled])"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?start=${20 * (page - 1)}")
    }

    override fun latestUpdatesSelector() = ".blog > div"

    // Popular
    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hot?start=${20 * (page - 1)}")
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    // Search
    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.findInstance<TagFilter>()!!
        return when {
            query.isNotEmpty() -> GET("$baseUrl/?search=$query&start=${20 * (page - 1)}")
            tagFilter.state.isNotEmpty() -> GET("$baseUrl/tag/${tagFilter.state}&start=${20 * (page - 1)}")
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".article-header").text()
        manga.description = document.select(".article-info > strong").text().trim()
        val genres = mutableListOf<String>()
        document.select(".article-tags").first()!!.select(".tags > .tag").forEach {
            genres.add(it.text().substringAfter("#"))
        }
        manga.genre = genres.joinToString(", ")
        return manga
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val dateUploadStr = doc.selectFirst(".article-info > small")?.text()
        val dateUpload = DATE_FORMAT.tryParse(dateUploadStr)
        val maxPage = doc.select("nav.pagination:first-of-type a.pagination-link").last()?.text()?.toInt() ?: 1
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
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".article-fulltext img")
            .mapIndexed { i, imgEl -> Page(i, imageUrl = imgEl.absUrl("src")) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        TagFilter(),
    )

    class TagFilter : Filter.Text("Tag ID")

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("H:m DD-MM-yyyy", Locale.US)
    }
}
