package eu.kanade.tachiyomi.extension.all.ahottie

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AHottie() : ParsedHttpSource() {
    override val baseUrl = "https://ahottie.net"
    override val lang = "all"
    override val name = "AHottie"
    override val supportsLatest = false

    // Popular
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select(".relative img").attr("src")
        genre = element.select(".flex a").joinToString(", ") {
            it.text()
        }
        title = element.select("h2").text()
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page")
    }

    override fun popularMangaSelector() = "#main > div > div"

    // Search

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return when {
            query.isNotEmpty() -> GET("$baseUrl/search?kw=$query&page=$page")
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("h1").text()
        genre = document.select("div.pl-3 > a").joinToString(", ") {
            it.text()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var doc = document
        while (true) {
            doc.select("#main img.block").map {
                pages.add(Page(pages.size, imageUrl = it.attr("src")))
            }
            val nextPageUrl = doc.select("a[rel=next]").attr("abs:href")
            if (nextPageUrl.isEmpty()) break
            doc = client.newCall(GET(nextPageUrl)).execute().asJsoup()
        }
        return pages
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val pages = element.select("[role=navigation] :nth-last-child(2)")?.text() ?: "1"
        setUrlWithoutDomain(element.select("link[rel=canonical]").attr("abs:href") + "#pages=$pages")
        chapter_number = 0F
        name = "GALLERY"
        date_upload =
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(element.select("time").text())?.time
                ?: 0L
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters not implemented, use search"),
    )
}
