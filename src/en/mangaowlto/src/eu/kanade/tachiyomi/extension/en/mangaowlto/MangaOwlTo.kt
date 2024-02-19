package eu.kanade.tachiyomi.extension.en.mangaowlto

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.en.mangaowlto.MangaOwlToFactory.Genre
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class MangaOwlTo(
    private val collection: String = "manga",
    extraName: String = "",
    private val genresList: List<Genre> = listOf(),
) : ConfigurableSource, HttpSource() {
    override val name: String = "MangaOwl.To $extraName"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val defaultDomain: String = preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE) ?: MIRROR_PREF_ENTRY_VALUES[0]
    override val baseUrl = "https://api.$defaultDomain/v1"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(MIRROR_PREF_KEY, entry).commit()
            }
        }
        screen.addPreference(mirrorPref)
    }

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/stories?type=$collection&ordering=-view_count&page=$page", headers)

    override fun popularMangaParse(response: Response) =
        json.decodeFromString<MangaOwlToStories>(response.body.string()).toMangasPage()

    // Latest

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/stories?type=$collection&ordering=-modified_at&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty() || filters.isEmpty()) {
            // Search won't work together with filter
            GET("$baseUrl/search?q=$query&page=$page", headers)
        } else {
            val url = "$baseUrl/stories?type=$collection".toHttpUrl().newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("ordering", filter.toUriPart())
                    }
                    is StatusFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                    is GenreFilter ->
                        filter.state
                            .filter { it.state }
                            .forEach { url.addQueryParameter("genres", it.uriPart) }
                    else -> {}
                }
            }

            url.apply {
                addQueryParameter("page", page.toString())
            }
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Manga summary page

    override fun mangaDetailsParse(response: Response) =
        json.decodeFromString<MangaOwlToStory>(response.body.string()).toSManga()

    // Chapters

    override fun chapterListParse(response: Response) =
        json.decodeFromString<MangaOwlToStory>(response.body.string()).chaptersList

    // Pages

    override fun pageListParse(response: Response) =
        json.decodeFromString<MangaOwlToChapterPages>(response.body.string()).toPages()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Search query won't use filters"),
        GenreFilter(genresList),
        StatusFilter(),
        SortFilter(),
    )

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Default", null),
            Pair("Most view", "-view_count"),
            Pair("Added", "created_at"),
            Pair("Last update", "-modified_at"),
            Pair("High rating", "rating"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Any", null),
            Pair("Completed", COMPLETED),
            Pair("Ongoing", ONGOING),
        ),
    )

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        private const val MIRROR_PREF_KEY = "MIRROR"
        private const val MIRROR_PREF_TITLE = "Mirror (Requires Restart)"
        private val MIRROR_PREF_ENTRIES = arrayOf(
            "mangaowl.to",
            "mangabuddy.to",
            "mangafreak.to",
            "toonily.to",
            "manganato.so",
            // "mangakakalot.so", This one doesn't have its own API
        )
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]

        const val ONGOING = "ongoing"
        const val COMPLETED = "completed"
    }
}
