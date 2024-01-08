package eu.kanade.tachiyomi.extension.en.gwtb

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable

class GWTB : HttpSource() {
    override val lang = "en"

    override val name = "Gone with the Blastwave"

    override val baseUrl = "http://www.blastwave-comic.com"

    override val supportsLatest = false

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(".fall > option:not(:first-child)").map {
            SChapter.create().apply {
                name = it.ownText()
                url = "/index.php?nro=${it.`val`()}"
                chapter_number = it.`val`().toFloat()
            }
        }

    override fun pageListParse(response: Response) =
        listOf(Page(0, "", response.asJsoup().selectFirst(".comic_title + img")!!.absUrl("src")))

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        SManga.create().apply {
            title = name
            url = "/index.php"
            author = "Kimmo Lemetti"
            artist = "Kimmo Lemetti"
            thumbnail_url = "$baseUrl/images/yarr.jpg"
            description = "Because war can be boring too."
        }.let { Observable.just(MangasPage(listOf(it), false)) }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(manga.apply { initialized = true })

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used!")

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used!")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used!")
}
