package eu.kanade.tachiyomi.extension.en.oglaf

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

class Oglaf : HttpSource() {

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

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = document.select("a:has(img[width=400])").mapNotNull { element ->
            val href = element.attr("href")
            val nameMatch = nameRegex.find(href) ?: return@mapNotNull null

            SChapter.create().apply {
                url = href
                name = nameMatch.groupValues[1]
            }
        }.distinct()

        return chapterList.mapIndexed { i, ch ->
            ch.apply { chapter_number = chapterList.size.toFloat() - i }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        var document = response.asJsoup()

        while (true) {
            val imageUrl = document.selectFirst("img#strip")?.attr("abs:src") ?: break
            pages.add(Page(pages.size, imageUrl = imageUrl))

            val nextUrl = document.selectFirst("a[rel=next]")?.attr("href")
            if (nextUrl != null && urlRegex.matches(nextUrl)) {
                document = client.newCall(GET(baseUrl + nextUrl, headers)).execute().asJsoup()
            } else {
                break
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    companion object {
        private val nameRegex = """/(.*)/""".toRegex()
        private val urlRegex = """/.*/\d*/""".toRegex()
    }
}
