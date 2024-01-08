package eu.kanade.tachiyomi.extension.en.schlockmercenary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

class Schlockmercenary : ParsedHttpSource() {

    override val name = "Schlock Mercenary"

    override val baseUrl = "https://www.schlockmercenary.com"

    override val lang = "en"

    override val supportsLatest = false

    private var chapterCount = 1

    // Books

    override fun popularMangaRequest(page: Int): Request = GET("${baseUrl}$archiveUrl")

    override fun popularMangaSelector(): String = "div.archive-book"

    override fun popularMangaFromElement(element: Element): SManga {
        val book = element.select("h4 > a").first()!!
        val thumb = (
            baseUrl + (
                element.select("img").first()?.attr("src")
                    ?: defaultThumbnailUrl
                )
            ).substringBefore("?")
        return SManga.create().apply {
            url = book.attr("href")
            title = book.text()
            artist = "Howard Tayler"
            author = "Howard Tayler"
            // Schlock Mercenary finished as of July 2020
            status = SManga.COMPLETED
            description = element.select("p").first()?.text() ?: ""
            thumbnail_url = thumb
        }
    }

    // Chapters

    override fun chapterListSelector() = "ul.chapters > li:not(ul > li > ul > li) > a"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val requestUrl = "${baseUrl}$archiveUrl"
        return client.newCall(GET(requestUrl))
            .asObservableSuccess()
            .map { response ->
                getChaptersForBook(response, manga)
            }
    }

    private fun getChaptersForBook(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()
        val sanitizedTitle = manga.title.replace("""([",'])""".toRegex(), "\\\\$1")
        val book = document.select(popularMangaSelector() + ":contains($sanitizedTitle)")
        val chapters = mutableListOf<SChapter>()
        chapterCount = 1
        book.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.url = element.attr("href")
        chapter.name = element.text()
        chapter.chapter_number = chapterCount++.toFloat()
        chapter.date_upload = chapter.url.takeLast(10).let {
            SimpleDateFormat(dateFormat, Locale.getDefault()).parse(it)!!.time
        }
        return chapter
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val requestUrl = "${baseUrl}$archiveUrl"
        return client.newCall(GET(requestUrl))
            .asObservableSuccess()
            .map { response ->
                getPagesForChapter(response, chapter)
            }
    }

    private fun getPagesForChapter(response: Response, chapter: SChapter): List<Page> {
        val document = response.asJsoup()

        /**
         * To find the end page, first, the next chapter start must be found,
         * then subtract one day off of the next chapter start.
         * If no chapter start is found, grab the next book start.
         * If no next book exists, assume one page long.
         */
        val currentChapter = document.select(chapterListSelector() + "[href=${chapter.url}]").first()!!
        val start = chapterFromElement(currentChapter).date_upload
        // Find next chapter start
        var nextChapter = currentChapter.parent()?.nextElementSibling()?.select("a")?.first()
        var end = start + 1
        // Chapter is the last in the book

        if (nextChapter == null) {
            // Grab next book start.
            nextChapter = currentChapter.parents()[2]?.nextElementSibling()?.select(chapterListSelector())?.first()
        }

        if (nextChapter != null) {
            end = chapterFromElement(nextChapter).date_upload
        }

        return generatePageListBetweenDates(start, end)
    }

    private fun generatePageListBetweenDates(start: Long, end: Long): List<Page> {
        val pages = mutableListOf<Page>()
        val calendar = GregorianCalendar()
        calendar.time = Date(start)

        while (calendar.time.before(Date(end))) {
            val result = calendar.time
            val formatter = SimpleDateFormat(dateFormat, Locale.getDefault())
            val today = formatter.format(result)
            getImageUrlsForDay(today).forEach { pages.add(Page(pages.size, "", it)) }
            calendar.add(Calendar.DATE, 1)
        }

        return pages
    }

    private fun getImageUrlsForDay(day: String): List<String> {
        val requestUrl = "$baseUrl/$day"
        val document = client.newCall(GET(requestUrl)).execute().asJsoup()
        return document.select("div#strip-$day > img").map { it.attr("abs:src") }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector(): String? = null

    override fun searchMangaSelector(): String = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException("Not used")

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException("Not used")

    companion object {
        const val defaultThumbnailUrl = "/static/img/logo.b6dacbb8.jpg"
        const val archiveUrl = "/archives/"
        const val dateFormat = "yyyy-MM-dd"
    }
}
