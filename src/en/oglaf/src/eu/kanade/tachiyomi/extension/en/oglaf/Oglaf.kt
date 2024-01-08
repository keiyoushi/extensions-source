package eu.kanade.tachiyomi.extension.en.oglaf

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Oglaf : ParsedHttpSource() {

    override val name = "Oglaf"

    override val baseUrl = "https://www.oglaf.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "Oglaf"
            artist = "Trudy Cooper & Doug Bayne"
            author = "Trudy Cooper & Doug Bayne"
            status = SManga.ONGOING
            url = "/archive/"
            description = "Filth and other Fantastical Things in handy webcomic form."
            thumbnail_url = "https://i.ibb.co/tzY0VQ9/oglaf.png"
        }

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = super.chapterListParse(response).distinct()
        return chapterList.mapIndexed {
                i, ch ->
            ch.apply { chapter_number = chapterList.size.toFloat() - i }
        }
    }

    override fun chapterListSelector() = "a:has(img[width=400])"

    override fun chapterFromElement(element: Element): SChapter {
        val nameRegex =
            """/(.*)/""".toRegex()
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.name = nameRegex.find(element.attr("href"))!!.groupValues[1]
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val urlRegex =
            """/.*/\d*/""".toRegex()
        val pages = mutableListOf<Page>()

        fun addPage(document: Document) {
            pages.add(Page(pages.size, "", document.select("img#strip").attr("abs:src")))
            val next = document.select("a[rel=next]").attr("href")
            if (urlRegex.matches(next)) addPage(client.newCall(GET(baseUrl + next, headers)).execute().asJsoup())
        }

        addPage(document)

        return pages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")
}
