package eu.kanade.tachiyomi.extension.en.reallifecomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RealLifeComics : ParsedHttpSource() {
    override val name = "Real Life Comics"

    override val baseUrl = "https://reallifecomics.com"

    override val lang = "en"

    override val supportsLatest = false

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
        return (currentYear downTo 1999).filter { it !in 2016..2017 }
            .map { createManga(it) }
            .let { Observable.just(MangasPage(it, false))!! }
    }

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        fetchPopularManga(1).map { mangaList ->
            mangaList.copy(mangaList.mangas.filter { it.title.contains(query) })
        }

    // Details

    override fun fetchMangaDetails(manga: SManga) = Observable.just(
        manga.apply {
            initialized = true
        },
    )!!

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).distinct().mapIndexed { index, chapter ->
            chapter.apply { chapter_number = index.toFloat() }
        }
    }

    override fun chapterListSelector() = ".calendar tbody tr td a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = element.attr("href")

        // entries between 1999-2014 do not have dates in the link
        // but all entries are placed in a calendar class which has the month & year as heading
        // figure out a date using the calendar
        // perhaps there might be a better way to get this but for now this works
        val monthYear = element
            .parent()!!
            .parent()!!
            .parent()!!
            .parent()!!
            .firstElementSibling()
            .text()

        val date = "$monthYear ${element.text()}"
        val parsedDate = SimpleDateFormat("MMMM yyyy dd", Locale.US).parse(date)
        date_upload = parsedDate?.time ?: 0L

        // chapter names are kept the same as what the site has
        name = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US).format(parsedDate ?: 0L)
    }

    // Page

    override fun pageListParse(document: Document): List<Page> {
        val image = document.select(".comic img").attr("src")
        return listOf(Page(0, "", image))
    }

    // Unsupported

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector() = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element) = throw Exception("Not used")

    override fun searchMangaNextPageSelector() = throw Exception("Not used")

    override fun searchMangaSelector() = throw Exception("Not used")

    override fun popularMangaRequest(page: Int) = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("Not used")

    override fun popularMangaNextPageSelector() = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element) = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document) = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector() = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not used")

    override fun latestUpdatesSelector() = throw Exception("Not used")

    companion object {
        private const val LOGO = "/images/logo.png"

        private const val AUTHOR = "Maelyn Dean"

        private const val SUMMARY = "The normal daily lives of some abnormal people. This entry includes all the chapters published in"

        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }
    }
}
