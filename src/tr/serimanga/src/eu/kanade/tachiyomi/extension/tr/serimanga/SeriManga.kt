package eu.kanade.tachiyomi.extension.tr.serimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class SeriManga : ParsedHttpSource() {
    override val name = "SeriManga"

    override val baseUrl = "https://serimanga.com"

    override val lang = "tr"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = "a.manga-list-bg"

    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/mangalar", headers)
        } else {
            GET("$baseUrl/mangalar?page=$page", headers)
        }
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("span.mlb-name").text()
        thumbnail_url = styleToUrl(element).removeSurrounding("'")
    }

    private fun styleToUrl(element: Element): String {
        return element.attr("style").substringAfter("(").substringBefore(")")
    }

    override fun popularMangaNextPageSelector() = "[rel=next]"

    override fun latestUpdatesSelector() = "a.sli2-img"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?a=a&page=$page", headers)

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.attr("title")
        thumbnail_url = styleToUrl(element)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/mangalar?search=$query&page=$page", headers)

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select(".demo1").text()
        genre = document.select("div.spc2rcrc-links > a").joinToString { it.text() }
        status = document.select("div.is-status.is-status--green").text().let {
            parseStatus(it)
        }
        thumbnail_url = document.select("[rel=image_src]").attr("href")
    }

    private fun parseStatus(status: String) = when {
        status.contains("CONTINUES") -> SManga.ONGOING
        status.contains("Tamamlanmış") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        var continueParsing = true

        while (continueParsing) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select(popularMangaNextPageSelector()).let {
                if (it.isNotEmpty()) {
                    document = client.newCall(GET(it.attr("abs:href"), headers)).execute().asJsoup()
                } else {
                    continueParsing = false
                }
            }
        }
        return chapters
    }

    override fun chapterListSelector() = "ul.spl-list > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = "${element.select("span").first()!!.text()}: ${element.select("span")[1].text()}"
        date_upload = dateFormat.parse(element.select("span")[2].ownText())?.time ?: 0
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-manga > img").mapIndexed { i, element ->
            val url = if (element.hasAttr("data-src"))element.attr("data-src") else element.attr("src")
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()
}
