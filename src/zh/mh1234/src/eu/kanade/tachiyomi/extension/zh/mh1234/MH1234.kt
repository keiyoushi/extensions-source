package eu.kanade.tachiyomi.extension.zh.mh1234

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MH1234 : ParsedHttpSource() {

    override val baseUrl = "https://b.amh1234.com"
    override val lang = "zh"
    override val name = "漫画1234"
    override val supportsLatest = true

    companion object {
        val IMG_REGEX1 = Regex("var chapterImages = (\\[.*?])")
        val IMG_REGEX2 = Regex("var chapterPath = \"(.*?)\"")
        val NUM_REGEX = Regex("\\d+")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic/one/page_waphit.html")

    override fun popularMangaSelector() = ".itemBox"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.attr("src")
        element.selectFirst(".title")!!.let {
            title = it.text()
            this.setUrlWithoutDomain(it.absUrl("href"))
        }
    }

    override fun popularMangaNextPageSelector() = null

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic/one/page_recent.html")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = null

    // Search Page

    // override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET(
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search/")
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString()).build(),
    )

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    // Manga Detail Page

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val txtItme = document.select(".txtItme")
        author = txtItme[0].text()
        status = when (txtItme[2].selectFirst("a:last-child")?.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("meta[name='description']")?.text()
    }

    // Manga Detail Page / Chapters Page (Separate)

    // override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url)

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val date = DATE_FORMAT.parse(document.selectFirst(".txtItme .date")!!.text())?.time
        val list = document.select("#chapter-list-1 a").map {
            SChapter.create().apply {
                this.setUrlWithoutDomain(it.absUrl("href"))
                name = it.text()
                date_upload = date ?: Date().time
                chapter_number = NUM_REGEX.find(it.text())?.value?.toFloat() ?: -1F
            }
        }
        return list.reversed()
    }

    // Manga View Page

    override fun pageListParse(document: Document): List<Page> {
        val html = document.body().html()
        val prefix = "https://gmh1234.wszwhg.net/${IMG_REGEX2.find(html)!!.groups[0]!!.value}"
        val list = Json.decodeFromString<List<String>>(IMG_REGEX1.find(html)!!.groups[0]!!.value)
        return list.mapIndexed { i, s ->
            Page(i, "${document.location()}?p=${i + 1}", prefix + s)
        }
    }

    // Image

    // override fun imageRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
