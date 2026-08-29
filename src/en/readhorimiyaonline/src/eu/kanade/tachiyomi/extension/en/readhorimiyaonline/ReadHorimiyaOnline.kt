package eu.kanade.tachiyomi.extension.en.readhorimiyaonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

@Source
abstract class ReadHorimiyaOnline : HttpSource() {

    override val supportsLatest = false

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        return MangasPage(listOf(parseManga(doc)), false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("No latest updates")

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(baseUrl, headers)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Search not supported")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException("Search not supported")

    override fun getFilterList(): FilterList = FilterList()

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return parseManga(doc).apply { initialized = true }
    }

    // Chapter List
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        return doc.select("#Chapters_List ul li ul li a").map { element ->
            SChapter.create().apply {
                name = element.text()
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
    }

    // Page List
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select("div.separator a > img").mapIndexed { index, img ->
            val imageUrl = img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                ?: img.absUrl("src")
            Page(index, imageUrl = imageUrl)
        }
    }

    // Image URL
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Image URL parsing not used")

    // Private helper to avoid duplication
    private fun parseManga(doc: Document): SManga = SManga.create().apply {
        title = "Horimiya"
        url = "/"
        thumbnail_url = doc.selectFirst("ul.wp-block-gallery li.blocks-gallery-item img")
            ?.let { img ->
                img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                    ?: img.absUrl("src")
            }
        description = doc.selectFirst("p")?.text()
        status = SManga.UNKNOWN
        initialized = true
    }
}
