package eu.kanade.tachiyomi.extension.en.irovedout

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
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Locale

class IRovedOut : HttpSource() {

    override val name = "I Roved Out"
    override val baseUrl = "https://www.irovedout.com"
    override val lang = "en"
    override val supportsLatest = false
    private val archiveUrl = "$baseUrl/archive"
    private val thumbnailUrl = "https://i.ibb.co/2g7Htwq/irovedout.png"
    private val seriesTitle = "I Roved Out in Search of Truth and Love"
    private val authorName = "Alexis Flower"
    private val seriesGenre = "Fantasy"
    private val seriesDescription = """
        I ROVED OUT IN SEARCH OF TRUTH AND LOVE is written & illustrated by Alexis Flower.
        It updates in chunks anywhere between 3 and 30 pages long at least once a month.
    """.trimIndent()
    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private val titleRegex = Regex("Book (?<bookNumber>\\d+): (?<chapterTitle>.+)")

    override fun chapterListRequest(manga: SManga): Request = throw Exception("Not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val mainPage = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val books = mainPage.select("#menu-menu > li > a[href^=$archiveUrl]")

        var chapterCounter = 1F
        val chaptersByBook = books.mapIndexed { bookIndex, book ->
            val bookNumber = bookIndex + 1
            val bookUrl = book.attr("href")
            val bookPage = client.newCall(GET(bookUrl, headers)).execute().asJsoup()
            val chapters = bookPage.select(".comic-archive-chapter-wrap")
            chapters.map {
                val chapterWrap = it.selectFirst(".comic-archive-chapter-wrap")!!
                SChapter.create().apply {
                    name = "Book $bookNumber: ${chapterWrap.selectFirst(".comic-archive-chapter")!!.text()}"
                    url = chapterWrap.selectFirst(".comic-archive-title > a")!!.attr("href")
                    date_upload = dateFormat.parse(chapterWrap.select(".comic-archive-date").last()!!.text())?.time ?: 0L
                    chapter_number = chapterCounter++
                }
            }
        }
        return Observable.just(chaptersByBook.flatten().reversed())
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        val comicPage = client.newCall(GET(page.url, headers)).execute().asJsoup()
        val imageUrl = comicPage.selectFirst("#comic img")!!.attr("src")
        return Observable.just(imageUrl)
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga)
    }

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val match = titleRegex.matchEntire(chapter.name) ?: return Observable.just(listOf())
        val bookNumber = match.groups["bookNumber"]!!.value.toInt()
        val title = match.groups["chapterTitle"]!!.value
        val bookPage = client.newCall(GET(archiveUrl + if (bookNumber != 1) "-book-$bookNumber" else "", headers)).execute().asJsoup()
        val chapterWrap = bookPage.select(".comic-archive-chapter-wrap").find { it.selectFirst(".comic-archive-chapter")!!.text() == title }
        val pageUrls = chapterWrap?.select(".comic-archive-list-wrap .comic-archive-title > a")?.map { it.attr("href") } ?: return Observable.just(listOf())
        val pages = pageUrls.mapIndexed { pageIndex, pageUrl ->
            Page(pageIndex, pageUrl)
        }
        return Observable.just(pages)
    }

    override fun pageListRequest(chapter: SChapter): Request = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            url = ""
            thumbnail_url = thumbnailUrl
            title = seriesTitle
            author = authorName
            artist = authorName
            description = seriesDescription
            genre = seriesGenre
            status = SManga.ONGOING
            initialized = true
        }
        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw Exception("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")
}
