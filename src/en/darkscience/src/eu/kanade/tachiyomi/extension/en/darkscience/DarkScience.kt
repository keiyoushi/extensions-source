package eu.kanade.tachiyomi.extension.en.darkscience

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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
    private val thumbnailUrl = "https://dresdencodak.com/wp-content/uploads/2019/03/DC_CastIcon_Kimiko.png"
    private val patreonUrl = "https://www.patreon.com/dresdencodak"
    private val seriesTitle = name
    private val authorName = "Sen (A. Senna Diaz)"
    private val seriesGenre = "Science Fiction, Mystery, LGBT+"
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
        "Support the comic on Patreon: $patreonUrl"

    private fun initTheManga(manga: SManga): SManga = manga.apply {
        url = archiveUrl
        thumbnail_url = thumbnailUrl
        title = seriesTitle
        author = authorName
        artist = authorName
        description = seriesDescription
        genre = seriesGenre
        status = SManga.ONGOING
        update_strategy = UpdateStrategy.ALWAYS_UPDATE
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

                chapters.add(
                    SChapter.create().apply {
                        name = chTitle
                        chapter_number = chNumMatch?.groupValues?.getOrNull(1)?.toFloat() ?: 0.0F
                        date_upload = DATE_FMT.parse(chDateMatch.groupValues[1])?.time ?: 0L
                        setUrlWithoutDomain(chLink)
                    },
                )
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

    override fun fetchImageUrl(page: Page): Observable<String> {
        val comicPage = client.newCall(GET(baseUrl + page.url, headers)).execute().asJsoup()
        val imageUrl = comicPage.selectFirst("article.post img.aligncenter")!!.attr("src")
        return Observable.just(imageUrl)
    }

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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
