package eu.kanade.tachiyomi.extension.en.existentialcomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class ExistentialComics : HttpSource() {

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

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("div#date-comics ul li a:eq(0)").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.text()
            chapter_number = url.substringAfterLast("/").toFloatOrNull() ?: 0f
        }
    }.distinctBy { it.url }.reversed()

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select(".comicImg").mapIndexed { i, element ->
        Page(i, imageUrl = element.attr("abs:src"))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
