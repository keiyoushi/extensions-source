package eu.kanade.tachiyomi.extension.all.sandraandwoo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.floor

abstract class SandraAndWoo(
    final override val baseUrl: String = "http://www.sandraandwoo.com",
    final override val lang: String,
) : ParsedHttpSource() {
    override val supportsLatest = false

    protected abstract val writer: String
    protected abstract val illustrator: String
    protected abstract val synopsis: String
    protected abstract val genres: String
    protected abstract val state: Int
    protected abstract val thumbnail: String
    protected abstract val archive: String

    private val manga: SManga
        get() = SManga.create().apply {
            title = name
            artist = illustrator
            author = writer
            description = synopsis
            genre = genres
            status = state
            thumbnail_url = thumbnail
            setUrlWithoutDomain(archive)
        }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val mangasPage = MangasPage(listOf(manga), false)
        return Observable.just(mangasPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        Observable.just(MangasPage(emptyList(), false))!!

    override fun chapterListSelector() = "#column a"

    private fun roundHalfwayUp(x: Float) = (x + floor(x + 1)) / 2

    private fun chapterParse(element: Element, lastChapterNumber: Float): Pair<Float, SChapter> {
        val path = URI(element.attr("href")).path
        val dateMatch = CHAPTER_DATE_REGEX.matchEntire(path)!!
        val (_, year, month, day) = dateMatch.groupValues
        val date = "$year-$month-$day".timestamp()

        val hover = element.attr("title")
        val titleMatch = CHAPTER_TITLE_REGEX.matchEntire(hover)!!
        val (_, title, number, backupNumber) = titleMatch.groupValues

        val chapterNumber =
            if (number.isNotEmpty()) {
                number.toFloat()
            } else if (backupNumber.isNotEmpty()) {
                backupNumber.toFloat()
            } else {
                roundHalfwayUp(lastChapterNumber)
            }
        val chapter = SChapter.create().apply {
            url = path
            name = title
            chapter_number = chapterNumber
            date_upload = date
        }

        return Pair(chapterNumber, chapter)
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        val elements = document.select(chapterListSelector()).reversed()

        val initial = Pair(0f, SChapter.create())

        return elements.runningFold(initial) { previous, element ->
            chapterParse(element, previous.first)
        }.drop(1).map { it.second }.reversed()
    }

    override fun chapterListParse(response: Response) = chapterListParse(response.asJsoup())

    private fun pageImageSelector() = "#comic img"

    override fun pageListParse(document: Document): List<Page> {
        val img = document.selectFirst(pageImageSelector())!!
        val path = img.attr("src")

        return listOf(Page(0, "", "${baseUrl}$path"))
    }

    override fun mangaDetailsParse(document: Document) = manga

    // <editor-fold desc="not used">
    override fun chapterFromElement(element: Element) = throw Exception("Not used")

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")

    override fun latestUpdatesSelector() = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element) = throw Exception("Not used")

    override fun popularMangaNextPageSelector() = throw Exception("Not used")

    override fun popularMangaRequest(page: Int) = throw Exception("Not used")

    override fun popularMangaSelector() = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element) = throw Exception("Not used")

    override fun searchMangaNextPageSelector() = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("Not used")

    override fun searchMangaSelector() = throw Exception("Not used")
    // </editor-fold>

    private fun String.timestamp() = DATE_FORMAT.parse(this)?.time ?: 0L

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        private val CHAPTER_DATE_REGEX = Regex(""".*/(\d+)/(\d+)/(\d+)/[^/]*/""")
        private val CHAPTER_TITLE_REGEX = Regex("""Permanent Link:\s*((?:\[(\d{4})])?\s*(?:\[[^]]*(\d{4})])?.*)""")
    }
}
