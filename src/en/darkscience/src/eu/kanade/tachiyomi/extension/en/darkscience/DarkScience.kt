package eu.kanade.tachiyomi.extension.en.darkscience

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("unused")
class DarkScience : HttpSource() {
    private val chapterDateRegex = """/(\d\d\d\d/\d\d/\d\d)/""".toRegex()
    private val chapterNumberRegex = """Dark Science #(\d+)""".toRegex()

    override val name = "Dark Science"
    override val baseUrl = "https://dresdencodak.com"
    override val lang = "en"
    override val supportsLatest = false

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
        url = "/category/darkscience/"
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

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        accumulateChapters(Triple(client.newCall(chapterListRequest(manga)), 0.0F, Observable.empty())).concatMap { it.third.toList() }

    override fun pageListParse(response: Response): List<Page> =
        listOf(
            Page(
                0,
                "",
                response
                    .asJsoup()
                    .selectFirst("article.post img.aligncenter")!!
                    .attr("src"),
            ),
        )

    override fun imageUrlParse(page: Response): String =
        page.asJsoup().selectFirst("article.post img.aligncenter")!!.attr("src")

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun parseChapter(ch: Element, chLast: Float): SChapter {
        val chTitle = ch.text()
        val chLink = ch.attr("href")
        val chDateMatch = chapterDateRegex.find(chLink)!!
        val chNumMatch = chapterNumberRegex.find(chTitle)
        val chDate = DATE_FMT.parse(chDateMatch.groupValues[1])?.time ?: 0L
        val chNum = chNumMatch?.groupValues?.getOrNull(1)?.toFloat() ?: (chLast - 0.01F)

        return SChapter.create().apply {
            name = chTitle
            chapter_number = chNum
            date_upload = chDate
            setUrlWithoutDomain(chLink)
        }
    }

    private fun accumulateChapters(state: Triple<Call?, Float, Observable<SChapter>>): Observable<Triple<Call?, Float, Observable<SChapter>>> {
        val archivePageFetch = state.first
        var chLast = state.second
        val archivePages = state.third

        return if (archivePageFetch == null) {
            Observable.just(state)
        } else {
            archivePageFetch
                .asObservableSuccess()
                .map {
                    val archivePage = it.asJsoup()
                    val nextPageUrl = archivePage.selectFirst("""#nav-below .nav-previous > a""")?.attr("href")
                    val nextCall = if (nextPageUrl != null) {
                        client.newCall(GET(nextPageUrl, headers))
                    } else {
                        null
                    }
                    val nextPages = Observable.from(
                        archivePage
                            .select("""#content article header > h2 > a""")
                            .map {
                                val ch = parseChapter(it, chLast)
                                chLast = ch.chapter_number
                                ch
                            },
                    )
                    Triple(nextCall, chLast, archivePages.concatWith(nextPages))
                }
                .concatMap { accumulateChapters(it) }
        }
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
