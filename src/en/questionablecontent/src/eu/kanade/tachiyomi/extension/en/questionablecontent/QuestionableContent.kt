package eu.kanade.tachiyomi.extension.en.questionablecontent

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.utils.getPreferencesLazy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.Date

class QuestionableContent :
    HttpSource(),
    ConfigurableSource {

    override val name = "Questionable Content"
    override val baseUrl = "https://www.questionablecontent.net"
    override val lang = "en"
    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(TextInterceptor())
        .build()

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = name
            artist = AUTHOR
            author = AUTHOR
            status = SManga.ONGOING
            url = "/archive.php"
            description = "An internet comic strip about romance and robots"
            thumbnail_url = "https://i.ibb.co/ZVL9ncS/qc-teh.png"
            initialized = true
        }

        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchPopularManga(1).map { it.mangas.first() }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapters = document.select("""div#container a[href^="view.php?comic="]""")
            .map { element ->
                val chapterUrl = element.attr("href")
                val number = URL_REGEX.find(chapterUrl)!!.groupValues[1]

                SChapter.create().apply {
                    setUrlWithoutDomain("/$chapterUrl")
                    name = element.text()
                    chapter_number = number.toFloat()
                }
            }
            .distinct()

        if (chapters.isNotEmpty()) {
            val firstChapter = chapters.first()
            if (firstChapter.url != preferences.getString(LAST_CHAPTER_URL, null)) {
                val date = Date().time
                firstChapter.date_upload = date
                preferences.edit()
                    .putString(LAST_CHAPTER_URL, firstChapter.url)
                    .putLong(LAST_CHAPTER_DATE, date)
                    .apply()
            } else {
                firstChapter.date_upload = preferences.getLong(LAST_CHAPTER_DATE, 0L)
            }
        }

        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.select("#strip").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }.toMutableList()

        if (showAuthorsNotesPref()) {
            val str = document.selectFirst("#newspost")?.html()
            if (!str.isNullOrEmpty()) {
                pages.add(Page(pages.size, imageUrl = TextInterceptorHelper.createUrl("Author's Notes from $AUTHOR", str)))
            }
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
        private const val LAST_CHAPTER_URL = "QC_LAST_CHAPTER_URL"
        private const val LAST_CHAPTER_DATE = "QC_LAST_CHAPTER_DATE"
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
        private const val AUTHOR = "Jeph Jacques"
        private val URL_REGEX = """view\.php\?comic=(.*)""".toRegex()
    }
}
