package eu.kanade.tachiyomi.extension.all.commitstrip

import eu.kanade.tachiyomi.network.GET
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
import java.util.Locale

abstract class CommitStrip(
    override val lang: String,
    private val siteLang: String,
) : HttpSource() {

    override val name = "Commit Strip"
    override val baseUrl = "https://www.commitstrip.com"

    override val supportsLatest = false

    private val dateFormat by lazy { SimpleDateFormat("yyyy/MM/dd", Locale.US) }

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
        val mangas = (currentYear downTo 2012).map { createManga(it) }
        return Observable.just(MangasPage(mangas, false))
    }

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1).map { mangaPage ->
        val filtered = mangaPage.mangas.filter { it.title.contains(query, ignoreCase = true) }
        MangasPage(filtered, false)
    }

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(
        manga.apply {
            initialized = true
        },
    )

    // Open in WebView

    override fun mangaDetailsRequest(manga: SManga): Request = GET("${manga.url}/?", headers)

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val pages = client.newCall(GET(manga.url, headers)).execute().use { response ->
            val responseString = response.asJsoup().selectFirst(".wp-pagenavi .pages")?.text() ?: "1"
            pageRegex.findAll(responseString).lastOrNull()?.value?.toInt() ?: 1
        }

        val chapters = (1..pages).flatMap { page ->
            client.newCall(GET("${manga.url}/page/$page", headers)).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP error ${response.code}")
                response.asJsoup().select(".excerpt a").map { element ->
                    SChapter.create().apply {
                        url = "$baseUrl/$siteLang" + element.attr("href").substringAfter(baseUrl)

                        // get the chapter date from the url
                        val dateStr = dateRegex.find(url)?.value
                        date_upload = dateFormat.tryParse(dateStr)

                        name = element.select("span").text()
                    }
                }
            }
        }.distinctBy { it.url }

        val total = chapters.size
        chapters.forEachIndexed { index, chapter ->
            chapter.chapter_number = (total - index).toFloat()
        }

        chapters
    }

    // Page

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        client.newCall(GET(chapter.url, headers)).execute().use { response ->
            val imageUrl = response.asJsoup().selectFirst(".entry-content p img")?.attr("abs:src") ?: ""
            listOf(Page(0, imageUrl = imageUrl))
        }
    }

    // Unsupported

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val LOGO_EN = "https://i.imgur.com/HODJlt9.jpg"
        private const val LOGO_FR = "https://i.imgur.com/I7ps9zS.jpg"
        private const val AUTHOR_EN = "Mark Nightingale"
        private const val AUTHOR_FR = "Thomas Gx"
        private const val ARTIST = "Etienne Issartial"
        private const val SUMMARY_EN = "The blog relating the daily life of web agency developers."
        private const val SUMMARY_FR = "Le blog qui raconte la vie des codeurs"
        private const val NOTE = "\n\nNote: This entry includes all the chapters published in"

        private val dateRegex = Regex("""\d{4}/\d{2}/\d{2}""")
        private val pageRegex = Regex("""\d+""")

        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }
    }
}
