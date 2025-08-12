package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class Niadd(
    override val name: String,
    override val baseUrl: String,
    private val langCode: String
) : ParsedHttpSource() {

    override val lang: String = langCode
    override val supportsLatest: Boolean = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/category/?page=$page", headers)
    }

    override fun popularMangaSelector(): String = "div.manga-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a")!!
        manga.setUrlWithoutDomain(link.attr("href"))
        manga.title = element.selectFirst("h3")?.text() ?: ""
        manga.thumbnail_url = element.selectFirst("img")?.absUrl("src")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/category/last_update/?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?name=$query&page=$page", headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text() ?: ""
        manga.description = document.selectFirst("div.detail-desc")?.text()
        manga.thumbnail_url = document.selectFirst("div.detail-cover img")?.absUrl("src")
        return manga
    }

    override fun chapterListSelector(): String = "ul.chapter-list li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val link = element.selectFirst("a")!!
        chapter.setUrlWithoutDomain(link.attr("href"))
        chapter.name = link.text()
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reader-page img").mapIndexed { i, img ->
            Page(i, "", img.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }
}
