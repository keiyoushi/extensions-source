package eu.kanade.tachiyomi.extension.en.elanschool

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class ElanSchool : HttpSource() {

    override val name = "Elan School"

    override val lang = "en"

    override val baseUrl = "https://elan.school"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            url = "/chapters/?dps_paged=$page"
            title = "Elan School"
            thumbnail_url = "$baseUrl/wp-content/uploads/2018/11/The-Elan-School-Comic-1cNEW-1-768x1491.jpg"
            description = "A 16 year old boy named Joe gets indoctrinated into a sick cult that is run by imprisoned teenagers. Based on the true story of the Elan School."
            status = SManga.ONGOING
            author = "Joe Nobody"
            artist = "Joe Nobody"
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) = fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1)
        .map { it.mangas.first().apply { initialized = true } }

    private fun chapterNextPageSelector() = "a.next"

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChaps = mutableListOf<SChapter>()
        var document = response.asJsoup()

        while (true) {
            val chapters = document.select(chapterListSelector()).map {
                chapterFromElement(it)
            }
            if (chapters.isEmpty()) {
                break
            }

            allChaps += chapters

            val hasNext = document.select(chapterNextPageSelector()).isNotEmpty()
            if (!hasNext) {
                break
            }

            val nextUrl = document.select(chapterNextPageSelector()).attr("href")
            document = client.newCall(GET(nextUrl, headers)).execute().asJsoup()
        }

        return allChaps.reversed()
    }

    private fun chapterListSelector() = "div.listing-item > a.title"

    private fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.text()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[data-orig-file]").mapIndexed { i, img ->
            Page(i, "", img.attr("src"))
        }
    }

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
