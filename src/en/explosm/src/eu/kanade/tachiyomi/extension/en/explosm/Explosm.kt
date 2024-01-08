package eu.kanade.tachiyomi.extension.en.explosm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Explosm : HttpSource() {

    override val name = "Cyanide & Happiness"

    override val baseUrl = "https://explosm.net"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private fun createManga(element: Element): SManga {
        return SManga.create().apply {
            initialized = true
            title = "C&H ${element.attr("id").substringAfter("panel")}" // year
            setUrlWithoutDomain(element.select("li a").first()!!.attr("href")) // January
            thumbnail_url = "https://vhx.imgix.net/vitalyuncensored/assets/13ea3806-5ebf-4987-bcf1-82af2b689f77/S2E4_Still1.jpg"
        }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return (GET("$baseUrl/comics/archive", headers))
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val eachYearAsAManga = response.asJsoup().select("dd.accordion-navigation > div").map { createManga(it) }

        return MangasPage(eachYearAsAManga, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        return createManga(response.asJsoup().select("div.content.active").first()!!)
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val januaryChapters = document.chaptersFromDocument() // should be at bottom of final returned list

        // get the rest of the year
        val chapters = document.select("div.content.active li:not(.active) a").reversed().map {
            client.newCall(GET(it.attr("abs:href"), headers)).execute().asJsoup().chaptersFromDocument()
        }.flatten()

        return chapters + januaryChapters
    }

    private fun Document.chaptersFromDocument(): List<SChapter> {
        return this.select("div.inner-wrap > div.row div.row.collapse").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.select("a").attr("href"))
                element.select("div#comic-author").text().let { cName ->
                    name = cName
                    date_upload = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                        .parse(cName.substringBefore(" "))?.time ?: 0L
                }
            }
        }
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, baseUrl + chapter.url)))
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String {
        return response.asJsoup().select("div#comic-wrap img").attr("abs:src")
    }

    override fun getFilterList() = FilterList()
}
