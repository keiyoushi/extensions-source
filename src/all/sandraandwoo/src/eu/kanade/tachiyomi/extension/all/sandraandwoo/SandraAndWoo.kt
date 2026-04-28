package eu.kanade.tachiyomi.extension.all.sandraandwoo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.floor

abstract class SandraAndWoo(
    final override val baseUrl: String = "https://www.sandraandwoo.com",
    final override val lang: String,
) : HttpSource() {
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

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(MangasPage(listOf(manga), false))

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(this.manga)

    private fun roundHalfwayUp(x: Float) = (x + floor(x + 1)) / 2

    private fun chapterParse(element: Element, lastChapterNumber: Float): Pair<Float, SChapter> {
        val path = URI(element.attr("href")).path
        val dateMatch = CHAPTER_DATE_REGEX.matchEntire(path)

        val date = if (dateMatch != null) {
            val (_, year, month, day) = dateMatch.groupValues
            DATE_FORMAT.tryParse("$year-$month-$day")
        } else {
            0L
        }

        val hover = element.attr("title")
        val titleMatch = CHAPTER_TITLE_REGEX.matchEntire(hover)

        val title = titleMatch?.groupValues?.getOrNull(1) ?: hover
        val number = titleMatch?.groupValues?.getOrNull(2).orEmpty()
        val backupNumber = titleMatch?.groupValues?.getOrNull(3).orEmpty()

        val chapterNumber = when {
            number.isNotEmpty() -> number.toFloat()
            backupNumber.isNotEmpty() -> backupNumber.toFloat()
            else -> roundHalfwayUp(lastChapterNumber)
        }

        val chapter = SChapter.create().apply {
            url = path
            name = title
            chapter_number = chapterNumber
            date_upload = date
        }

        return Pair(chapterNumber, chapter)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select("#column a").reversed()

        val initial = Pair(0f, SChapter.create())

        return elements.runningFold(initial) { previous, element ->
            chapterParse(element, previous.first)
        }.drop(1).map { it.second }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imgUrl = document.selectFirst("#comic img")?.absUrl("src") ?: ""

        return listOf(Page(0, imageUrl = imgUrl))
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        private val CHAPTER_DATE_REGEX = Regex(""".*/(\d+)/(\d+)/(\d+)/[^/]*/""")
        private val CHAPTER_TITLE_REGEX = Regex("""Permanent Link:\s*((?:\[(\d{4})])?\s*(?:\[[^]]*(\d{4})])?.*)""")
    }
}
