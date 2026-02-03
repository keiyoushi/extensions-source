package eu.kanade.tachiyomi.extension.en.onepunchmanonline

import eu.kanade.tachiyomi.network.GET
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

class OnePunchManOnline : ParsedHttpSource() {

    override val name = "One Punch Man Online"
    override val baseUrl = "https://w10.1punchman.com"
    override val lang = "en"
    override val supportsLatest = true

    // =========================================================================
    //  Popular Manga (Hardcoded Single Entry)
    // =========================================================================
    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(createManga()), false))

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector(): String? = null

    // =========================================================================
    //  Latest Updates
    // =========================================================================
    override fun fetchLatestUpdates(page: Int) = fetchPopularManga(page)
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = null

    // =========================================================================
    //  Search (Client-Side Safe Filtering)
    // =========================================================================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.fromCallable {
        val manga = createManga()
        // If query matches title, return the manga. Otherwise return empty list.
        if (query.isBlank() || manga.title.contains(query, ignoreCase = true)) {
            MangasPage(listOf(manga), false)
        } else {
            MangasPage(emptyList(), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = null

    // =========================================================================
    //  Manga Details
    // =========================================================================
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(createManga().apply { initialized = true })

    override fun mangaDetailsParse(document: Document) = throw UnsupportedOperationException()

    private fun createManga(): SManga = SManga.create().apply {
        title = "One Punch Man"
        url = "/"
        thumbnail_url = "https://comicvine.gamespot.com/a/uploads/original/11111/111114608/3458824-8589005300-YICcI.jpg"
        author = "ONE"
        artist = "Murata Yusuke"
        status = SManga.ONGOING
        genre = "Action, Comedy, Superhero, Seinen"
        description = "One-Punch Man is a superhero who has trained so hard that his hair has fallen out, and who can overcome any enemy with one punch."
    }

    // =========================================================================
    //  Chapter List
    // =========================================================================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl, headers)

    // Catches ALL links containing "/manga/" to get both "punch" and "chapter" URLs
    override fun chapterListSelector() = "ul li a[href*='/manga/']"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        name = element.text()
    }

    // =========================================================================
    //  Page List
    // =========================================================================
    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("div.entry-content img, .separator img, p img")

        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
