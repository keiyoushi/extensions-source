package eu.kanade.tachiyomi.extension.en.darkscience

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
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class DarkScience : HttpSource() {
    private val chapterDateRegex = """/(\d\d\d\d/\d\d/\d\d)/""".toRegex()
    private val chapterNumberRegex = """Dark Science #(\d+)""".toRegex()

    override val name = "Dark Science"
    override val baseUrl = "https://dresdencodak.com"
    override val lang = "en"
    override val supportsLatest = false

    private val archiveUrl = "$baseUrl/category/darkscience/"
    private val authorName = "Sen (A. Senna Diaz)"
    private val seriesDescription = "" +
        "Scientist Kimiko Ross has a problem: " +
        "her money’s gone and a bank exploded her house. " +
        "With no place else to go, she travels to " +
        "Nephilopolis, the city of giants – built from the " +
        "ruins of an ancient war and a fading memory of " +
        "tomorrow.\n" +
        "Follow our cyborg hero as she attempts to survive " +
        "the bureaucratic behemoth with a little “help” " +
        "from her “friends.” And what exactly is " +
        "Dark Science anyway?\n" +
        "Support the comic on Patreon: https://www.patreon.com/dresdencodak"

    private fun initTheManga(manga: SManga): SManga = manga.apply {
        url = archiveUrl
        thumbnail_url = "https://dresdencodak.com/wp-content/uploads/2019/03/DC_CastIcon_Kimiko.png"
        title = name
        author = authorName
        artist = authorName
        description = seriesDescription
        genre = "Science Fiction, Mystery, LGBT+"
        status = SManga.ONGOING
        initialized = true
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(
            listOf(initTheManga(SManga.create())),
            false,
        ),
    )

    // We still (re-)initialise all properties here, for this method also gets called on a
    // backup restore. And in a backup, only `url` and `title` are preserved.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(initTheManga(manga))

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()

        var archivePage: Document? = client.newCall(GET(archiveUrl, headers)).execute().asJsoup()

        var chLast = 0.0F

        while (archivePage != null) {
            val nextArchivePageUrl = archivePage.selectFirst("""#nav-below .nav-previous > a""")?.attr("href")
            val nextArchivePage = if (nextArchivePageUrl != null) {
                client.newCall(GET(nextArchivePageUrl, headers)).execute().asJsoup()
            } else { null }

            archivePage.select("""#content article header > h2 > a""").forEach {
                val chTitle = it.text()
                val chLink = it.attr("href")
                val chDateMatch = chapterDateRegex.find(chLink)!!
                val chNumMatch = chapterNumberRegex.find(chTitle)
                val chDate = DATE_FMT.parse(chDateMatch.groupValues[1])?.time ?: 0L
                val chNum = chNumMatch?.groupValues?.getOrNull(1)?.toFloat() ?: (chLast + 0.01F)

                chapters.add(
                    SChapter.create().apply {
                        name = chTitle
                        chapter_number = chNum
                        date_upload = chDate
                        setUrlWithoutDomain(chLink)
                    },
                )

                // This is a hack to make the app not think there’s missing chapters after
                // a title page.
                chLast = chNum
            }

            archivePage = nextArchivePage
        }

        return Observable.just(chapters)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        // It would’ve been cleaner and neater to extract chapter information from the archive feed.
        // Alas, the website provides no structured way of extracting that information. We’d have
        // to iterate over all »chapters« so far, picking the ones for which the chapter number
        // regex fails, *guessing* they’re chapter title pages, and then demote all other chapters
        // to pages of these. As i don’t see a clean way to cache the »chapter« list we’ve fetched
        // so far, that whole endeavour seems not worth the effort.
        // So here it is: Each »chapter« having just 1 page.
        return Observable.just(listOf(Page(0, chapter.url)))
    }

    override fun imageUrlParse(response: Response): String =
        response.asJsoup().selectFirst("article.post img.aligncenter")!!.attr("src")

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
