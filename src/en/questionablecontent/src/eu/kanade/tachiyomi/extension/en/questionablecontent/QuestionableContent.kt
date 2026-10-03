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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.OkHttpClient
import java.util.Date

@Source
abstract class QuestionableContent :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(TextInterceptor())
        // The site's zstd responses get cut off after the first frame, which truncates the archive page
        addNetworkInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Accept-Encoding", "br, gzip").removeHeader("Origin").build())
        }
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun createManga() = SManga.create().apply {
        title = name
        artist = AUTHOR
        author = AUTHOR
        status = SManga.ONGOING
        url = "/archive.php"
        description = "An internet comic strip about romance and robots"
        thumbnail_url = "https://i.ibb.co/ZVL9ncS/qc-teh.png"
        initialized = true
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) createManga() else manga
        val chapterList = if (fetchChapters) fetchChapterList(manga) else chapters

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val chapters = document.select("""div#container a[href^="view.php?comic="]""")
            .map { element ->
                val chapterUrl = element.attr("href")
                val number = URL_REGEX.find(chapterUrl)!!.groupValues[1]

                SChapter.create().apply {
                    setUrlWithoutDomain("/$chapterUrl")
                    name = element.text().let { text ->
                        LINK_TEXT_REGEX.matchEntire(text)?.destructured?.let { (num, title) -> "$num: $title" } ?: text
                    }
                    chapter_number = number.toFloat()
                }
            }
            .distinctBy { it.url }

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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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
        private val LINK_TEXT_REGEX = """See #(\d+): "(.*)" with newspost""".toRegex()
    }
}
