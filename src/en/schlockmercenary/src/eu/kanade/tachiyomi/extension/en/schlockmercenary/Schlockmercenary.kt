package eu.kanade.tachiyomi.extension.en.schlockmercenary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

class Schlockmercenary : HttpSource() {

    override val name = "Schlock Mercenary"

    override val baseUrl = "https://www.schlockmercenary.com"

    override val lang = "en"

    override val supportsLatest = false

    // Books

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$ARCHIVE_URL", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.archive-book").map { element ->
            val book = element.selectFirst("h4 > a")!!
            val thumbUrl = element.selectFirst("img")?.attr("abs:src") ?: "$baseUrl$DEFAULT_THUMBNAIL_URL"
            SManga.create().apply {
                url = book.attr("href")
                title = book.text()
                artist = "Howard Tayler"
                author = "Howard Tayler"
                // Schlock Mercenary finished as of July 2020
                status = SManga.COMPLETED
                description = element.selectFirst("p")?.text() ?: ""
                thumbnail_url = thumbUrl.substringBefore("?")
            }
        }
        return MangasPage(mangas, false)
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(GET("$baseUrl$ARCHIVE_URL", headers))
        .asObservableSuccess()
        .map { response ->
            val document = response.asJsoup()
            val sanitizedTitle = manga.title.replace(TITLE_SANITIZATION_REGEX, "\\\\$1")
            val book = document.select("div.archive-book:contains($sanitizedTitle)")

            book.select("ul.chapters > li:not(ul > li > ul > li) > a").mapIndexed { index, element ->
                SChapter.create().apply {
                    url = element.attr("href")
                    name = element.text()
                    chapter_number = (index + 1).toFloat()
                    date_upload = dateFormat.tryParse(url.takeLast(10))
                }
            }.reversed()
        }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val requestUrl = "$baseUrl$ARCHIVE_URL"
        return client.newCall(GET(requestUrl, headers))
            .asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()

                val currentChapter = document.selectFirst("ul.chapters > li:not(ul > li > ul > li) > a[href=${chapter.url}]")
                    ?: return@map emptyList<Page>()

                val start = dateFormat.tryParse(currentChapter.attr("href").takeLast(10))
                var nextChapter = currentChapter.parent()?.nextElementSibling()?.selectFirst("a")
                var end = start + 1L

                if (nextChapter == null) {
                    nextChapter = currentChapter.parents()[2]?.nextElementSibling()?.selectFirst("ul.chapters > li:not(ul > li > ul > li) > a")
                }

                if (nextChapter != null) {
                    end = dateFormat.tryParse(nextChapter.attr("href").takeLast(10))
                }

                generatePageListBetweenDates(start, end)
            }
    }

    private fun generatePageListBetweenDates(start: Long, end: Long): List<Page> {
        val pages = mutableListOf<Page>()
        val calendar = GregorianCalendar().apply { time = Date(start) }
        val endDate = Date(end)

        while (calendar.time.before(endDate)) {
            val today = dateFormat.format(calendar.time)
            getImageUrlsForDay(today).forEach {
                pages.add(Page(pages.size, imageUrl = it))
            }
            calendar.add(Calendar.DATE, 1)
        }

        return pages
    }

    private fun getImageUrlsForDay(day: String): List<String> {
        val requestUrl = "$baseUrl/$day"
        val document = client.newCall(GET(requestUrl, headers)).execute().asJsoup()
        return document.select("div#strip-$day > img").map { it.attr("abs:src") }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    companion object {
        const val DEFAULT_THUMBNAIL_URL = "/static/img/logo.b6dacbb8.jpg"
        const val ARCHIVE_URL = "/archives/"

        private val TITLE_SANITIZATION_REGEX = """([",'])""".toRegex()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    }
}
