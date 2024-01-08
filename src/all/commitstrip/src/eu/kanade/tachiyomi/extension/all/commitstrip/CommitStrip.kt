package eu.kanade.tachiyomi.extension.all.commitstrip

import eu.kanade.tachiyomi.network.GET
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class CommitStrip(
    override val lang: String,
    private val siteLang: String,
) : ParsedHttpSource() {

    override val name = "Commit Strip"
    override val baseUrl = "https://www.commitstrip.com"

    override val supportsLatest = false

    // Helper

    private fun createManga(year: Int): SManga = SManga.create().apply {
        url = "$baseUrl/$siteLang/$year"
        title = "$name ($year)"
        thumbnail_url = when (lang) {
            "en" -> LOGO_EN
            "fr" -> LOGO_FR
            else -> LOGO_EN
        }
        author = when (lang) {
            "en" -> AUTHOR_EN
            "fr" -> AUTHOR_FR
            else -> AUTHOR_EN
        }
        artist = ARTIST
        status = if (year != currentYear) SManga.COMPLETED else SManga.ONGOING
        description = when (lang) {
            "en" -> "$SUMMARY_EN $NOTE $year"
            "fr" -> "$SUMMARY_FR $NOTE $year"
            else -> "$SUMMARY_EN $NOTE $year"
        }
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        // have one manga entry for each year
        return (currentYear downTo 2012)
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

    // Open in WebView

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("${manga.url}/?", headers)
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        // create a new call to parse the no of pages in the site
        // example responseString - Page 1 of 11
        val responseString = client.newCall(GET(manga.url, headers)).execute().run {
            asJsoup().selectFirst(".wp-pagenavi .pages")?.text() ?: "1"
        }
        // use regex to get the last number (i.e. 11 above)
        val pages = Regex("\\d+").findAll(responseString).last().value.toInt()

        return (1..pages).map {
            val response = chapterListRequest(manga, it)
            chapterListParse(response)
        }.let { Observable.just(it.flatten()) }
    }

    private fun chapterListRequest(manga: SManga, page: Int): Response =
        client.newCall(GET("${manga.url}/page/$page", headers)).execute().run {
            if (!isSuccessful) {
                close()
                throw Exception("HTTP error $code")
            }
            this
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed().distinct().mapIndexed { index, chapter ->
            chapter.apply { chapter_number = index.toFloat() }
        }
    }

    override fun chapterListSelector() = ".excerpt a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = "$baseUrl/$siteLang" + element.attr("href").substringAfter(baseUrl)

        // get the chapter date from the url
        val date = Regex("\\d{4}\\/\\d{2}\\/\\d{2}").find(url)?.value
        val parsedDate = date?.let { SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(it) }
        date_upload = parsedDate?.time ?: 0L

        name = element.select("span").text()
    }

    // Page

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(GET(chapter.url, headers)).execute().run {
            asJsoup().select(".entry-content p img").attr("src")
        }.let {
            Observable.just(listOf(Page(0, "", it)))
        }
    }

    // Unsupported

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")

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
        private const val LOGO_EN = "https://i.imgur.com/HODJlt9.jpg"

        private const val LOGO_FR = "https://i.imgur.com/I7ps9zS.jpg"

        private const val AUTHOR_EN = "Mark Nightingale"

        private const val AUTHOR_FR = "Thomas Gx"

        private const val ARTIST = "Etienne Issartial"

        private const val SUMMARY_EN = "The blog relating the daily life of web agency developers."

        private const val SUMMARY_FR = "Le blog qui raconte la vie des codeurs"

        private const val NOTE = "\n\nNote: This entry includes all the chapters published in"

        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }
    }
}
