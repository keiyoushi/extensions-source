package eu.kanade.tachiyomi.extension.en.clowncorps

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ClownCorps : ConfigurableSource, HttpSource() {
    override val baseUrl = "https://clowncorps.net"
    override val lang = "en"
    override val name = "Clown Corps"
    override val supportsLatest = false

    private val creator = "Joe Chouinard"

    // Text from: https://clowncorps.net/about/
    private val synopsis = "Clown Corps is a comic about crime-fighting clowns. " +
        "It's pronounced \"core.\" Like marine corps."

    override val client = network.client.newBuilder()
        .addInterceptor(TextInterceptor())
        .build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.fromCallable {
        MangasPage(
            listOf(
                SManga.create().apply {
                    title = name
                    artist = creator
                    author = creator
                    description = synopsis
                    status = SManga.ONGOING
                    // Image from: https://clowncorps.net/about/
                    thumbnail_url = "$baseUrl/wp-content/uploads/2022/11/clowns41.jpg"
                    setUrlWithoutDomain("/comic")
                },
            ),
            hasNextPage = false,
        )
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(manga.apply { initialized = true })

    override fun chapterListParse(response: Response): List<SChapter> {
        val pageStatus = response.asJsoup().select("#paginav li.paginav-pages").text()
        val pageCount = pageStatus.split(" ").last().toInt()

        val chapters = mutableListOf<SChapter>()

        for (page in 1..pageCount) {
            val url = "$baseUrl/comic/page/$page/"
            val resp = client.newCall(GET(url, headers)).execute()
            val comics = resp.asJsoup().select(".comic")
            for (comic in comics) {
                val link = comic.selectFirst(".post-title a")?.attr("href")
                val title = comic.selectFirst(".post-title a")?.text()
                val postDate = comic.selectFirst(".post-date")?.text()
                val postTime = comic.selectFirst(".post-time")?.text()
                if (link == null || title == null || postDate == null || postTime == null) continue
                val date = parseDate("$postDate $postTime")

                val chapter = SChapter.create().apply {
                    setUrlWithoutDomain(link)
                    name = title
                    date_upload = date
                }
                chapters.add(chapter)
            }
        }

        // Add chapter numbers to the chapters, to ensure correct sorting.
        // Seems to be unnecessary, as it already bases the order on the chapter names.
        // Kept here, commented out, just in case.
        /*chapters.forEachIndexed { index, sChapter ->
            sChapter.chapter_number = chapters.size - index.toFloat()
        }*/

        return chapters
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("MMMM dd, yyyy hh:mm aa", Locale.ENGLISH)
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val pages = mutableListOf<Page>()

        val image = doc.selectFirst("#comic img") ?: return pages

        val url = image.attr("src")
        pages.add(Page(0, "", url))

        if (showAuthorsNotesPref()) {
            val title = image.attr("title")

            // Ignore chapters that don't really have author's notes
            val ignoreRegex = Regex("""^chapter \d+ page \d+$""", RegexOption.IGNORE_CASE)
            if (ignoreRegex.matches(title)) return pages

            val localURL = TextInterceptorHelper.createUrl("Author's Notes from $creator", title)
            val textPage = Page(pages.size, "", localURL)
            pages.add(textPage)
        }

        return pages
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)
        }
        screen.addPreference(authorsNotesPref)
    }

    companion object {
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
    }
}
