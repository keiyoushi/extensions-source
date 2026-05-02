package eu.kanade.tachiyomi.extension.en.reallifecomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RealLifeComics : HttpSource() {

    override val name = "Real Life Comics"

    override val baseUrl = "https://reallifecomics.com"

    override val lang = "en"

    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("MMMM yyyy dd", Locale.US)
    private val nameFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US)

    // Helper

    private fun createManga(year: Int): SManga = SManga.create().apply {
        setUrlWithoutDomain("/archivepage.php?year=$year")
        title = "$name ($year)"
        thumbnail_url = "$baseUrl$LOGO"
        author = AUTHOR
        status = if (year != currentYear) SManga.COMPLETED else SManga.ONGOING
        description = "$SUMMARY $year"
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        // create one manga entry for each yearly archive
        // skip 2016 and 2017 as they don't have any archive
        val mangas = (currentYear downTo 1999)
            .filter { it !in 2016..2017 }
            .map(::createManga)

        return Observable.just(MangasPage(mangas, false))
    }

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1).map { mangaList ->
        val filtered = mangaList.mangas.filter { it.title.contains(query, ignoreCase = true) }
        MangasPage(filtered, mangaList.hasNextPage)
    }

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(
        manga.apply {
            initialized = true
        },
    )

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".calendar tbody tr td a")
        .map(::chapterFromElement)
        .distinctBy { it.url }
        .mapIndexed { index, chapter ->
            chapter.apply { chapter_number = index.toFloat() }
        }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("href")

        // entries between 1999-2014 do not have dates in the link
        // but all entries are placed in a calendar class which has the month & year as heading
        // figure out a date using the calendar
        val monthYear = element.closest(".calendar")?.previousElementSibling()?.text().orEmpty()

        val date = "$monthYear ${element.text()}".trim()
        val time = dateFormat.tryParse(date)

        date_upload = time
        name = if (time != 0L) {
            nameFormat.format(time)
        } else {
            date
        }
    }

    // Page

    override fun pageListParse(response: Response): List<Page> {
        val image = response.asJsoup().selectFirst(".comic img")?.attr("abs:src").orEmpty()
        return listOf(Page(0, imageUrl = image))
    }

    // Unsupported

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val LOGO = "/images/logo.png"

        private const val AUTHOR = "Maelyn Dean"

        private const val SUMMARY = "The normal daily lives of some abnormal people. This entry includes all the chapters published in"

        private val currentYear: Int
            get() = Calendar.getInstance()[Calendar.YEAR]
    }
}
