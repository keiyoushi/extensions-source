package eu.kanade.tachiyomi.extension.en.grrlpower

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Source
abstract class GrrlPower :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val comicAuthor = "David Barrack"
    private val startingYear = 2010
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    private val dateFormat by lazy {
        SimpleDateFormat("MMM dd yyyy", Locale.US)
    }

    override val client = network.client.newBuilder()
        .addInterceptor(TextInterceptor())
        .build()

    // ============================== Popular ==============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Observable.just(
        MangasPage(
            listOf(
                SManga.create().apply {
                    artist = comicAuthor
                    author = comicAuthor
                    description = "Grrl Power is a comic about a crazy nerdette that becomes a superheroine. Humor, action, cheesecake, beefcake, 'explosions, and maybe some drama. Possibly ninjas."
                    genre = "superhero, humor, action"
                    initialized = true
                    status = SManga.ONGOING
                    thumbnail_url = "https://static.tvtropes.org/pmwiki/pub/images/rsz_grrl_power.png"
                    title = "Grrl Power"
                    url = "/archive"
                },
            ),
            false,
        ),
    )

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details ==============================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        (startingYear..currentYear).flatMap { year ->
            val request = GET("$baseUrl/archive/?archive_year=$year", headers)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP error ${response.code}")
                }
                chapterListParse(response)
            }
        }.sortedByDescending { it.date_upload }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val year = response.request.url.queryParameter("archive_year") ?: return emptyList()

        return response.asJsoup().getElementsByClass("archive-date").map {
            val dateStr = "${it.text()} $year"
            val link = it.nextElementSibling()!!.child(0)
            SChapter.create().apply {
                name = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                date_upload = dateFormat.tryParse(dateStr)
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val soup = response.asJsoup()
        val pages = mutableListOf<Page>()

        val comicImg = soup.selectFirst("div#comic img")
        if (comicImg != null) {
            pages.add(
                Page(
                    index = 0,
                    url = response.request.url.toString(),
                    imageUrl = comicImg.absUrl("src"),
                ),
            )
        }

        val text = soup.getElementsByClass("entry").html()
        if (text.isNotEmpty() && showAuthorsNotesPref()) {
            pages.add(
                Page(
                    index = pages.size,
                    imageUrl = TextInterceptorHelper.createUrl("Author's Notes from $comicAuthor", text),
                ),
            )
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences ============================

    private val preferences by getPreferencesLazy()

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
