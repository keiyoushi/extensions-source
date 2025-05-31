package eu.kanade.tachiyomi.extension.en.clowncorps

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.MultiSelectListPreference
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
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ClownCorps : ConfigurableSource, HttpSource() {
    override val baseUrl = "https://clowncorps.net"
    override val lang = "en"
    override val name = "Clown Corps"
    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(TextInterceptor())
        .build()

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

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        Observable.just(MangasPage(listOf(getManga()), hasNextPage = false))

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        fetchPopularManga(page)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(getManga())

    @Serializable
    class SerializableChapter(val fullLink: String, val name: String, val dateUpload: Long) {
        override fun hashCode() = fullLink.hashCode()
        override fun equals(other: Any?) =
            other is SerializableChapter && fullLink == other.fullLink
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // The total number of webpages with chapters on them
        val document = response.asJsoup()
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
        val fullJsonString = Json.encodeToString(allChapters)
        setChapterCache(fullJsonString)

        // Convert the serializable chapters to SChapters
        return allChapters
            .sortedByDescending { it.dateUpload }
            .map { chapter ->
                SChapter.create().apply {
                    setUrlWithoutDomain(chapter.fullLink)
                    name = chapter.name
                    date_upload = chapter.dateUpload
                }
            }
    }

    private fun getChaptersFromCache(): Set<SerializableChapter> {
        val cachedChaps = getChapterCache() ?: return emptySet()
        return Json.decodeFromString(cachedChaps)
    }

    private fun fetchChapterWebpage(webpageIndex: Int): Document {
        val url = "$baseUrl/comic/page/$webpageIndex/"
        return client.newCall(GET(url, headers)).execute().asJsoup()
    }

    private fun extractChapters(document: Document): List<SerializableChapter> {
        val comics = document.select(".comic")
        return comics.map {
            val link = it.selectFirst(".post-title a")!!.attr("href")
            val title = it.selectFirst(".post-title a")!!.text()
            val postDate = it.selectFirst(".post-date")!!.text()
            val postTime = it.selectFirst(".post-time")!!.text()
            val date = parseDate("$postDate $postTime")
            SerializableChapter(link, title, date)
        }
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

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun getShowAuthorsNotesPref() =
        preferences.getBoolean(SETTING_KEY_SHOW_AUTHORS_NOTES, false)

    private fun getChapterCache() =
        preferences.getString(CACHE_KEY_CHAPTERS, null)

    private fun setChapterCache(json: String) =
        preferences.edit().putString(CACHE_KEY_CHAPTERS, json).apply()

    private fun clearChapterCache() =
        preferences.edit().remove(CACHE_KEY_CHAPTERS).apply()

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
