package eu.kanade.tachiyomi.extension.en.readhorimiyaonline

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
import rx.Observable

@Source
abstract class ReadHorimiyaOnline : HttpSource() {

    override val supportsLatest = false

    // Popular Manga – only one manga exists on this site
    override fun popularMangaRequest(page: Int): Request = Request.Builder().url(baseUrl).headers(headers).build()

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val manga = SManga.create().apply {
            title = "Horimiya"
            url = "/"
            thumbnail_url = doc.select("ul.wp-block-gallery li.blocks-gallery-item img")
                .firstOrNull()
                ?.let { img ->
                    img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                        ?: img.absUrl("src")
                } ?: ""
            description = doc.select("p").firstOrNull()?.text() ?: ""
            status = SManga.UNKNOWN
            initialized = true
        }
        return MangasPage(listOf(manga), false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = Request.Builder().url(baseUrl).headers(headers).build()

    override fun latestUpdatesParse(response: Response): MangasPage = MangasPage(emptyList(), false)

    // Search – not supported
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = Request.Builder().url(baseUrl).headers(headers).build()

    override fun searchMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun getFilterList(): FilterList = FilterList()

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga): Request = Request.Builder().url(baseUrl).headers(headers).build()

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = "Horimiya"
            url = "/"
            thumbnail_url = doc.select("ul.wp-block-gallery li.blocks-gallery-item img")
                .firstOrNull()
                ?.let { img ->
                    img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                        ?: img.absUrl("src")
                } ?: ""
            description = doc.select("p").firstOrNull()?.text() ?: ""
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    // Chapter List
    override fun chapterListRequest(manga: SManga): Request = Request.Builder().url(baseUrl).headers(headers).build()

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        return doc.select("#Chapters_List ul li ul li a").map { element ->
            SChapter.create().apply {
                name = element.text()
                url = element.absUrl("href")
                date_upload = 0L
            }
        }
    }

    // Page List
    override fun pageListRequest(chapter: SChapter): Request = Request.Builder().url(chapter.url).headers(headers).build()

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select("div.separator a > img").mapIndexed { index, img ->
            val imageUrl = img.attr("data-lazy-src").takeIf { it.isNotEmpty() }
                ?: img.absUrl("src")
            Page(index, imageUrl = imageUrl)
        }
    }

    // Image URL – unused because pageListParse sets imageUrl directly
    override fun imageUrlParse(response: Response): String = ""
}
