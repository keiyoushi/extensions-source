package eu.kanade.tachiyomi.extension.id.mangalay

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mangalay : ParsedHttpSource() {
    override val name = "Mangalay"
    override val baseUrl = "http://mangalay.blogspot.com"
    override val lang = "id"
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/2013/04/daftar-baca-komik_20.html", headers)
    }

    override fun popularMangaSelector() = ".post-body table"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
        title = element.select(".tr-caption").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create()

    override fun chapterListSelector() = ".post-body span > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select("b").text()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".separator img")
            .dropLast(1) // :last-child not working somehow
            .mapIndexed { index, element ->
                val url = element.attr("src")
                Page(index, "", url)
            }
    }

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not Used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not Used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not Used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not Used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not Used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not Used")

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not Used")
}
