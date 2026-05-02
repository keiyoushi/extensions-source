package eu.kanade.tachiyomi.extension.en.supermega

import eu.kanade.tachiyomi.network.GET
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

class Supermega : HttpSource() {

    override val name = "SUPER MEGA"

    override val baseUrl = "https://www.supermegacomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/")
            title = "SUPER MEGA"
            artist = "JohnnySmash"
            author = "JohnnySmash"
            status = SManga.ONGOING
            description = ""
            thumbnail_url = "https://www.supermegacomics.com/runningman_inverted.PNG"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.first().apply { initialized = true } }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val document = client.newCall(GET(baseUrl)).execute().asJsoup()
        val latestComicNumber = document.selectFirst("[name='bigbuttonprevious']")
            ?.parent()?.attr("href")?.substringAfter("?i=")?.toIntOrNull()?.plus(1) ?: 0

        val chapters = (1..latestComicNumber).reversed().map {
            SChapter.create().apply {
                name = it.toString()
                chapter_number = it.toFloat()
                setUrlWithoutDomain("?i=$it")
            }
        }

        return Observable.just(chapters)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("img[border='4']").mapIndexed { i, element ->
        Page(i, imageUrl = element.absUrl("src"))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
