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
    override val name = "Dark Science"
    override val baseUrl = "https://dresdencodak.com"
    override val lang = "en"
    override val supportsLatest = false

    private fun initTheManga(manga: SManga): SManga = manga.apply {
        url = "/category/darkscience/"
        thumbnail_url = "https://dresdencodak.com/wp-content/uploads/2019/03/DC_CastIcon_Kimiko.png"
        title = name
        author = "Sen (A. Senna Diaz)"
        artist = "Sen (A. Senna Diaz)"
        description = """Scientist Kimiko Ross has a problem:
        | her money’s gone and a bank exploded her house. With no place
        | else to go, she travels to Nephilopolis, the city of giants –
        | built from the ruins of an ancient war and a fading memory of
        | tomorrow.\n Follow our cyborg hero as she attempts to survive the
        | bureaucratic behemoth with a little “help” from her “friends.”
        | And what exactly is Dark Science anyway?\nSupport the comic on
        | Patreon: https://www.patreon.com/dresdencodak
        """.trimMargin()
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(initTheManga(manga))

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()

        var archivePage: Document? = client.newCall(GET(baseUrl + manga.url, headers))
            .execute().asJsoup()

        var chLast = 0.0F

        while (archivePage != null) {
            val nextArchivePageUrl = archivePage.selectFirst("""#nav-below .nav-previous > a""")
                ?.attr("href")
            val nextArchivePage = if (nextArchivePageUrl != null) {
                client.newCall(GET(nextArchivePageUrl, headers)).execute().asJsoup()
            } else { null }

            archivePage.select("""#content article header > h2 > a""").forEach {
                val chTitle = it.text()
                val chLink = it.attr("href")
                val chNum = chapterNumberRegex.find(chTitle)
                    ?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    ?: (chLast + 0.01F)

                chapters.add(
                    SChapter.create().apply {
                        name = chTitle
                        chapter_number = chNum
                        date_upload = getDate(chLink)
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

    private fun getDate(url: String): Long {
        return try {
            dateFormat.parse(
                chapterDateRegex.find(url)!!.groupValues[1],
            )!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListParse(response: Response): List<Page> =
        listOf(
            Page(
                0,
                imageUrl = response.asJsoup()
                    .selectFirst("article.post img.aligncenter")!!
                    .attr("src"),
            ),
        )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val chapterDateRegex = """/(\d\d\d\d/\d\d/\d\d)/""".toRegex()
    private val chapterNumberRegex = """Dark Science #(\d+)""".toRegex()
}
