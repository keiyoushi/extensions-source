package eu.kanade.tachiyomi.extension.en.grrlpower

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Calendar
import java.util.Locale

@Source
abstract class GrrlPower :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val comicAuthor = "David Barrack"
    private val startingYear = 2010
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(TextInterceptor())

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
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
    )

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()

    // ============================= Chapters ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val chapterList = (startingYear..currentYear).flatMap { year ->
            client.get("$baseUrl/archive/?archive_year=$year").asJsoup()
                .getElementsByClass("archive-date").map {
                    val dateStr = "${it.text()} $year"
                    val link = it.nextElementSibling()!!.child(0)
                    SChapter.create().apply {
                        name = link.text()
                        setUrlWithoutDomain(link.absUrl("href"))
                        date_upload = dateFormat.tryParseDate(dateStr)
                    }
                }
        }.sortedByDescending { it.date_upload }

        return SMangaUpdate(manga, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val soup = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = mutableListOf<Page>()

        val comicImg = soup.selectFirst("div#comic img")
        if (comicImg != null) {
            pages.add(
                Page(
                    index = 0,
                    url = soup.location(),
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

private val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("MMM d yyyy")
    .toFormatter(Locale.US)
