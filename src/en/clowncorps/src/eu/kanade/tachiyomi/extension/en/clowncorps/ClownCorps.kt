package eu.kanade.tachiyomi.extension.en.clowncorps

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.MultiSelectListPreference
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

@Source
abstract class ClownCorps :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(TextInterceptor())

    private fun getManga() = SManga.create().apply {
        title = name
        artist = CREATOR
        author = CREATOR
        status = SManga.ONGOING
        initialized = true
        // Image and description from: https://clowncorps.net/about/
        thumbnail_url = "$baseUrl/wp-content/uploads/2022/11/clowns41.jpg"
        description = "Clown Corps is a comic about crime-fighting clowns.\n" +
            "It's pronounced \"core.\" Like marine corps."
        url = "/comic"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(getManga()), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(page)

    @Serializable
    class SerializableChapter(val fullLink: String, val name: String, val dateUpload: Long) {
        override fun hashCode() = fullLink.hashCode()
        override fun equals(other: Any?) = other is SerializableChapter && fullLink == other.fullLink
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(getManga(), chapters)

        // The total number of webpages with chapters on them
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val currentPageIndicator = document.select("#paginav li.paginav-pages").text()
        val totalWebpageCount = currentPageIndicator.split(" ").last().toInt()

        val allChapters = getChaptersFromCache().toMutableSet()
        // Fetch all the chapters from the website until we reached where the cache left off
        for (webpageIndex in 1..totalWebpageCount) {
            val pageDoc = if (webpageIndex == 1) document else fetchChapterWebpage(webpageIndex)
            val anyChaptersWereAdded = allChapters.addAll(extractChapters(pageDoc))
            if (!anyChaptersWereAdded) break // No new chapters were added from this webpage, so we're done
        }

        // Save the chapters to cache
        val fullJsonString = allChapters.toJsonString<Set<SerializableChapter>>()
        setChapterCache(fullJsonString)

        // Convert the serializable chapters to SChapters
        val chapterList = allChapters
            .sortedByDescending { it.dateUpload }
            .map { chapter ->
                SChapter.create().apply {
                    setUrlWithoutDomain(chapter.fullLink)
                    name = chapter.name
                    date_upload = chapter.dateUpload
                }
            }

        return SMangaUpdate(getManga(), chapterList)
    }

    private fun getChaptersFromCache(): Set<SerializableChapter> {
        val cachedChaps = getChapterCache() ?: return emptySet()
        return cachedChaps.parseAs()
    }

    private suspend fun fetchChapterWebpage(webpageIndex: Int): Document {
        val url = "$baseUrl/comic/page/$webpageIndex/"
        return client.get(url).asJsoup()
    }

    private fun extractChapters(document: Document): List<SerializableChapter> {
        val comics = document.select(".comic")
        return comics.map {
            val link = it.selectFirst(".post-title a")!!.attr("href")
            val title = it.selectFirst(".post-title a")!!.text()
            val postDate = it.selectFirst(".post-date")!!.text()
            val postTime = it.selectFirst(".post-time")!!.text()
            val date = dateFormat.tryParseDateTime("$postDate $postTime")
            SerializableChapter(link, title, date)
        }
    }

    private val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("MMMM d, yyyy h:mm a")
        .toFormatter(Locale.ENGLISH)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        val pages = mutableListOf<Page>()

        val image = doc.selectFirst("#comic img") ?: return pages

        val url = image.attr("src")
        pages.add(Page(0, "", url))

        if (getShowAuthorsNotesPref()) {
            val title = image.attr("title")

            // Ignore chapters that don't really have author's notes
            val ignoreRegex = Regex("""^chapter \d+ page \d+$""", RegexOption.IGNORE_CASE)
            if (ignoreRegex.matches(title)) return pages

            val localURL = TextInterceptorHelper.createUrl("Author's Notes from $CREATOR", title)
            val textPage = Page(pages.size, "", localURL)
            pages.add(textPage)
        }

        return pages
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun getShowAuthorsNotesPref() = preferences.getBoolean(SETTING_KEY_SHOW_AUTHORS_NOTES, false)

    private fun getChapterCache() = preferences.getString(CACHE_KEY_CHAPTERS, null)

    private fun setChapterCache(json: String) = preferences.edit().putString(CACHE_KEY_CHAPTERS, json).apply()

    private fun clearChapterCache() = preferences.edit().remove(CACHE_KEY_CHAPTERS).apply()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SETTING_KEY_SHOW_AUTHORS_NOTES
            title = "Show author's notes"
            summary =
                "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)
        }
        screen.addPreference(authorsNotesPref)

        // I couldn't find a way to create a simple button, so here's a workaround that uses
        // a MultiSelectListPreference with a single option as a kind of confirmation window.
        val clearCachePref = MultiSelectListPreference(screen.context).apply {
            key = SETTING_KEY_CLEAR_CHAPTER_CACHE
            title = "Clear chapter cache"
            summary = "Clears the chapter cache, forcing a full re-fetch from the website."
            dialogTitle = "Are you sure you want to clear the chapter cache?"
            entries = arrayOf("Yes, I'm sure")
            entryValues = arrayOf(VALUE_CONFIRM)
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Set<*>
                if (checkValue.contains(VALUE_CONFIRM)) {
                    clearChapterCache()
                    Toast.makeText(screen.context, "Cleared chapter cache", Toast.LENGTH_SHORT)
                        .show()
                }

                false // Don't actually save the "yes"
            }
        }
        screen.addPreference(clearCachePref)
    }

    companion object {
        private const val CREATOR = "Joe Chouinard"

        private const val SETTING_KEY_SHOW_AUTHORS_NOTES = "showAuthorsNotes"

        private const val CACHE_KEY_CHAPTERS = "chaptersCache"

        private const val SETTING_KEY_CLEAR_CHAPTER_CACHE = "clearChapterCache"
        private const val VALUE_CONFIRM = "yes"
    }
}
