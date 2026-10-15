package eu.kanade.tachiyomi.extension.en.reallifecomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RealLifeComics : KeiSource() {

    override val supportsLatest = false

    private val dateFormat = DateTimeFormatter.ofPattern("MMMM yyyy d", Locale.US)
    private val nameFormat = DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy", Locale.US)

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

    override suspend fun getPopularManga(page: Int): MangasPage {
        // create one manga entry for each yearly archive
        // skip 2016 and 2017 as they don't have any archive
        val mangas = (currentYear downTo 1999)
            .filter { it !in 2016..2017 }
            .map(::createManga)

        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangaList = getPopularManga(1)
        val filtered = mangaList.mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(filtered, mangaList.hasNextPage)
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val chapterList = client.get(getMangaUrl(manga)).asJsoup()
            .select(".calendar tbody tr td a")
            .map(::chapterFromElement)
            .distinctBy { it.url }
            .mapIndexed { index, chapter ->
                chapter.apply { chapter_number = index.toFloat() }
            }

        return SMangaUpdate(manga, chapterList)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("href")

        // entries between 1999-2014 do not have dates in the link
        // but all entries are placed in a calendar class which has the month & year as heading
        // figure out a date using the calendar
        val monthYear = element.closest(".calendar")?.previousElementSiblings()?.select("h4.month")?.first()?.text().orEmpty()

        val date = "$monthYear ${element.text()}".trim()
        val time = dateFormat.tryParseDate(date)

        date_upload = time
        name = if (time != 0L) {
            nameFormat.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
        } else {
            date
        }
    }

    // Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val image = client.get(getChapterUrl(chapter)).asJsoup().selectFirst(".comic img")?.attr("abs:src").orEmpty()
        return listOf(Page(0, imageUrl = image))
    }

    companion object {
        private const val LOGO = "/images/logo.png"

        private const val AUTHOR = "Maelyn Dean"

        private const val SUMMARY = "The normal daily lives of some abnormal people. This entry includes all the chapters published in"

        private val currentYear: Int
            get() = LocalDate.now().year
    }
}
