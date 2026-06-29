package eu.kanade.tachiyomi.extension.en.onepunchmanonline

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

class OnePunchManOnline : HttpSource() {

    override val name = "One Punch Man Online"
    override val baseUrl = "https://w11.1punchman.com"
    override val lang = "en"
    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(createManga()), false))

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Latest ================================
    override fun fetchLatestUpdates(page: Int) = fetchPopularManga(page)
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // =============================== Search ================================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.fromCallable {
        val manga = createManga()
        if (query.isBlank() || manga.title.contains(query, ignoreCase = true)) {
            MangasPage(listOf(manga), false)
        } else {
            MangasPage(emptyList(), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Details ===============================
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(createManga().apply { initialized = true })

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    private fun createManga(): SManga = SManga.create().apply {
        title = "One Punch Man"
        url = "/"
        thumbnail_url = "https://1punchman.com/wp-content/uploads/2024/02/9782380712018_1_75.jpg"
        author = "ONE"
        artist = "Murata Yusuke"
        status = SManga.ONGOING
        genre = "Action, Comedy, Superhero, Seinen"
        description = "One-Punch Man is a superhero who has trained so hard that his hair has fallen out, and who can overcome any enemy with one punch."
    }

    // ============================== Chapters ===============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl, headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("ul li a[href*='/manga/']").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.text()
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.entry-content img, .separator img, p img")
            .map { img ->
                img.attr("abs:data-src")
                    .ifEmpty { img.attr("abs:data-lazy-src") }
                    .ifEmpty { img.attr("abs:src") }
            }
            .filter { it.startsWith("http") }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
