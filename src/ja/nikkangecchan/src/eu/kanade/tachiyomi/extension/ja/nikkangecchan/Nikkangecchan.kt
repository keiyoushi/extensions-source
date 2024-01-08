package eu.kanade.tachiyomi.extension.ja.nikkangecchan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Nikkangecchan : ParsedHttpSource() {

    override val name = "Nikkangecchan"

    override val baseUrl = "https://nikkangecchan.jp"

    override val lang = "ja"

    override val supportsLatest = false

    private val catalogHeaders = Headers.Builder()
        .apply {
            add("User-Agent", USER_AGENT)
            add("Referer", baseUrl)
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, catalogHeaders)

    override fun popularMangaSelector(): String = ".contentInner > figure"

    override fun popularMangaFromElement(element: Element): SManga {
        val imgBox = element.select(".imgBox").first()!!
        val detailBox = element.select(".detailBox").last()!!

        return SManga.create().apply {
            title = detailBox.select("h3").first()!!.text()
            thumbnail_url = baseUrl + imgBox.select("a > img").first()!!.attr("src")
            setUrlWithoutDomain(baseUrl + imgBox.select("a").first()!!.attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map {
                val filtered = it.mangas.filter { e -> e.title.contains(query, true) }
                MangasPage(filtered, false)
            }
    }

    // Does not have search, use complete list (in popular) instead.
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        popularMangaRequest(page)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val detailBox = document.select("#comicDetail .detailBox")

        return SManga.create().apply {
            title = detailBox.select("h3").first()!!.text()
            author = detailBox.select(".author").first()!!.text()
            artist = detailBox.select(".author").first()!!.text()
            description = document.select(".description").first()!!.text()
            status = SManga.ONGOING
        }
    }

    override fun chapterListSelector(): String = ".episodeBox"

    override fun chapterListParse(response: Response): List<SChapter> =
        super.chapterListParse(response).reversed()

    override fun chapterFromElement(element: Element): SChapter {
        val episodePage = element.select(".episode-page").first()!!
        val title = element.select("h4.episodeTitle").first()!!.text()
        val dataTitle = episodePage.attr("data-title").substringBefore("|").trim()

        return SChapter.create().apply {
            name = "$title - $dataTitle"
            chapter_number = element.select("h4.episodeTitle").first()!!.text().toFloatOrNull() ?: -1f
            scanlator = "Akita Publishing"
            setUrlWithoutDomain(baseUrl + episodePage.attr("data-src").substringBeforeLast("/"))
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, chapter.url, "$baseUrl${chapter.url}/image")))
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val headers = Headers.Builder()
            .apply {
                add("User-Agent", USER_AGENT)
                add("Referer", baseUrl + page.url.substringBeforeLast("/"))
            }

        return GET(page.imageUrl!!, headers.build())
    }

    override fun latestUpdatesSelector() = ""

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun pageListRequest(chapter: SChapter): Request = throw Exception("This method should not be called!")

    override fun pageListParse(document: Document): List<Page> = throw Exception("This method should not be called!")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
    }
}
