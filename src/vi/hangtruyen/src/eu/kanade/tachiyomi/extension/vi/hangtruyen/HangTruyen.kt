package eu.kanade.tachiyomi.extension.vi.hangtruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HangTruyen : WPComics(
    "HangTruyen",
    "https://hangtruyen.net",
    "vi",
    dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    },
    gmtOffset = null,
) {
    override val client = super.client.newBuilder()
        .rateLimit(5)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/tim-kiem?r=newly-updated&page=$page&orderBy=view_desc")

    override fun popularMangaNextPageSelector() = ".next-page"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("div.search-result .m-post").map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(entries, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!
        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/tim-kiem?r=newly-updated&page=$page")

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    // Search
    override val searchPath = "tim-kiem"

    override fun searchMangaSelector() = "div.search-result"

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

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
}
