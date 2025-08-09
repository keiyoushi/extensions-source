package eu.kanade.tachiyomi.extension.zh.xgmn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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

class XGMN : ParsedHttpSource() {

    override val baseUrl = "http://xgmn8.vip"
    override val lang = "zh"
    override val name = "性感美女"
    override val supportsLatest = true
    // private var realUrl = baseUrl;

    companion object {
        val ID_REGEX = Regex("\\d+(?=\\.html)")
        val PAGE_SIZE_REGEX = Regex("\\d+(?=P)")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    }

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top.html")

    override fun popularMangaSelector() = ".related_box"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        element.selectFirst("a")!!.let {
            title = it.attr("title")
            setUrlWithoutDomain(it.absUrl("href"))
        }
    }

    override fun popularMangaNextPageSelector() = null

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/new.html")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = null

    // Search Page

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val httpUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("/plus/search/index.asp")
            .addQueryParameter("keyword", query)
            .addQueryParameter("p", page.toString())
        return GET(httpUrl.build())
    }

    override fun searchMangaSelector() = ".node > p > a"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.text()
        setUrlWithoutDomain(element.absUrl("href"))
        thumbnail_url = "$baseUrl/uploadfile/pic/${ID_REGEX.find(url)?.value}.jpg"
    }

    override fun searchMangaNextPageSelector() = null

    // Manga Detail Page

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        author = document.selectFirst(".item-2")?.text()?.substringAfter("模特：")
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED
    }

    // Manga Detail Page / Chapters Page (Separate)

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response) = response.asJsoup().let {
        listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst(".current")!!.absUrl("href"))
                name = it.selectFirst(".article-title")!!.text()
                chapter_number = 1F
                date_upload = DATE_FORMAT.tryParse(
                    it.selectFirst(".item-1")?.text()?.substringAfter("更新："),
                )
            },
        )
    }

    // Manga View Page

    override fun pageListParse(document: Document): List<Page> {
        val prefix = document.selectFirst(".current")!!.absUrl("href").substringBeforeLast(".html")
        val total = PAGE_SIZE_REGEX.find(document.selectFirst(".article-title")!!.text())!!.value
        val size = document.select(".article-content > p[style] > img").size
        return List(total.toInt()) {
            Page(it, prefix + (it / size).let { v -> if (v == 0) "" else "_$v" } + ".html#${it % size + 1}")
        }
    }

    // Image

    // override fun imageRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(response: Response): String {
        val seq = response.request.url.fragment!!
        val url = response.asJsoup()
            .selectXpath("//*[contains(@class,'article-content')]/p[@*[contains(.,'center')]]/img[position()=$seq]")
            .first() ?: throw Exception("$seq | ${response.request.url}")
        return "$baseUrl/${getUrlWithoutDomain(url.attr("src"))}"
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun getUrlWithoutDomain(url: String): String {
        val prefix = listOf("http://", "https://").firstOrNull(url::startsWith)
        return url.substringAfter(prefix ?: "").substringAfter('/')
    }
}
