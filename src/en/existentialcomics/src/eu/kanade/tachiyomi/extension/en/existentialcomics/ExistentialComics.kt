package eu.kanade.tachiyomi.extension.en.existentialcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class ExistentialComics : ParsedHttpSource() {

    override val name = "Existential Comics"

    override val baseUrl = "https://existentialcomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "Existential Comics"
            artist = "Corey Mohler"
            author = "Corey Mohler"
            status = SManga.ONGOING
            url = "/archive/byDate"
            description = "A philosophy comic about the inevitable anguish of living a brief life in an absurd world. Also Jokes."
            thumbnail_url = "https://i.ibb.co/pykMVYM/existential-comics.png"
        }

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga) = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).distinct().reversed()
    }

    override fun chapterListSelector() = "div#date-comics ul li a:eq(0)"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()
        chapter.chapter_number = chapter.url.substringAfterLast("/").toFloat()
        return chapter
    }

    override fun pageListParse(document: Document) = document.select(".comicImg").mapIndexed { i, element -> Page(i, "", "https:" + element.attr("src").substring(1)) }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
}
