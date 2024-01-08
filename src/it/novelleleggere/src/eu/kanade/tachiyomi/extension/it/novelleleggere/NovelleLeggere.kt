package eu.kanade.tachiyomi.extension.it.novelleleggere

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NovelleLeggere : ParsedHttpSource() {

    // Info
    override val name: String = "Novelle Leggere"
    override val baseUrl: String = "https://www.novelleleggere.com"
    override val lang: String = "it"
    override val supportsLatest: Boolean = false

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl)

    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector(): String = "table:contains(Manga) tr:gt(0)"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val a = element.select("a").first()!!
        title = a.text()
        setUrlWithoutDomain(a.attr("abs:href"))
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Latest Not Supported")

    override fun latestUpdatesNextPageSelector(): String = throw Exception("Latest Not Supported")
    override fun latestUpdatesSelector(): String = throw Exception("Latest Not Supported")
    override fun latestUpdatesFromElement(element: Element): SManga =
        throw Exception("Latest Not Supported")

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("Search Not Supported")

    override fun searchMangaNextPageSelector(): String = throw Exception("Search Not Supported")
    override fun searchMangaSelector(): String = throw Exception("Search Not Supported")
    override fun searchMangaFromElement(element: Element): SManga =
        throw Exception("Search Not Supported")

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.post-content img").first()!!.attr("abs:src")
        title = document.select("div.post-content h3").text().trim()
        description =
            document.select("div.post-content div:contains(Trama) div.su-spoiler-content").text()
                .trim()
    }

    // Chapters
    override fun chapterListSelector(): String =
        "div.post-content div:contains(Capitoli) div.su-spoiler-content ul li a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text().trim()
        setUrlWithoutDomain(element.attr("abs:href"))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("div.post-content p>img, div.post-content figure>img").forEachIndexed { index, element ->
            add(Page(index, "", element.attr("abs:src").substringBefore("?")))
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw Exception("ImgURL Parse Not Used")
}
