package eu.kanade.tachiyomi.extension.en.grrlpower

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.collections.ArrayList

@Suppress("unused")
class GrrlPower(
    override val baseUrl: String = "https://www.grrlpowercomic.com",
    override val lang: String = "en",
    override val name: String = "Grrl Power Comic",
    override val supportsLatest: Boolean = false,
) : HttpSource(), ConfigurableSource {
    private val comicAuthor = "David Barrack"
    private val startingYear = 2010
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private val dateFormat = SimpleDateFormat("MMM dd yyyy", Locale.US)

    override val client = super.client.newBuilder().addInterceptor(TextInterceptor()).build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(
            listOf(
                SManga.create().apply {
                    artist = comicAuthor
                    author = comicAuthor
                    description = "Grrl Power is a comic about a crazy nerdette that becomes a" +
                        " superheroine. Humor, action, cheesecake, beefcake, 'splosions," +
                        " and maybe some drama. Possibly ninjas. "
                    genre = "superhero, humor, action"
                    initialized = true
                    status = SManga.ONGOING
                    // Thumbnail Found On The TvTropes Page for the comic
                    thumbnail_url = "https://static.tvtropes.org/pmwiki/pub/images/rsz_grrl_power.png"
                    title = "Grrl Power"
                    url = "/archive"
                },
            ),
            false,
        ),
    )!!

    /**
     There are separate pages for each year.
     A Separate call needs to be made for each year since publication
     After we get the response send on like normal and collect all the chapters.
     */
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val ret: ArrayList<SChapter> = ArrayList()
        for (i in startingYear..currentYear) {
            client
                .newCall(GET("$baseUrl/archive/?archive_year=$i"))
                .asObservableSuccess()
                .map { chapterListParse(it) }
                .subscribe { ret.addAll(it) }
            // Using A List of Observables and calling .from() won't work due to the number of
            // observables active at once. error shown below for reference in case someone knows a fix.
            // java.lang.IllegalArgumentException: Sequence contains too many elements
        }
        // Sort By Date
        ret.sortByDescending { it.date_upload }
        return Observable.just(ret)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val year = response.request.url.toString().substringAfter('=').toInt()
        return response.asJsoup().getElementsByClass("archive-date").map {
            val date = dateFormat.parse("${it.text()} $year")
            val link = it.nextElementSibling()!!.child(0)
            SChapter.create().apply {
                name = link.text()
                setUrlWithoutDomain(link.attr("href"))
                date_upload = date?.time ?: 0L
                // chapter_number isn't set as suggested by arkon
                // https://github.com/tachiyomiorg/tachiyomi-extensions/pull/15717#discussion_r1138014748
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url: String = response.request.url.toString()
        val pages: ArrayList<Page> = ArrayList()
        val soup = response.asJsoup()
        pages.add(
            Page(
                0,
                url,
                soup.selectFirst("div#comic img")!!.absUrl("src"),
            ),
        )
        // The TextInterceptor handles html natively.
        val text = soup.getElementsByClass("entry").html()

        if (text.isNotEmpty() && showAuthorsNotesPref()) {
            pages.add(Page(1, "", TextInterceptorHelper.createUrl(comicAuthor, text)))
        }
        return pages
    }

    // Show Authors Notes Pref Copied from
    // ProjectRoot/multisrc/overrides/webtoons/webtoons/src/WebtoonsSrc.kt
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    companion object {
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
    }
    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(SHOW_AUTHORS_NOTES_KEY, checkValue).commit()
            }
        }
        screen.addPreference(authorsNotesPref)
    } // End of Preferences

    // This can be called when the user refreshes the comic even if initialized is true
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("Not Used")
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not Used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Not Used")
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not Used")
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not Used")
    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("Not Used")
    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not Used")
}
