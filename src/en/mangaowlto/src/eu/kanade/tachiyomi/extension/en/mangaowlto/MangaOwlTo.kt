package eu.kanade.tachiyomi.extension.en.mangaowlto

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangaOwlTo(
    private val collection: String,
    extraName: String,
    private val genresList: List<Genre>,
) : ConfigurableSource, HttpSource() {
    override val name: String = "MangaOwl.To $extraName"
    override val lang = "en"
    override val supportsLatest = true

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    private val defaultDomain: String =
        preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)!!

    override val baseUrl = "https://$defaultDomain"

    private val apiUrl = "https://api.$defaultDomain/v1"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Mirror (Requires Restart)"
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"
        }
        screen.addPreference(mirrorPref)
    }

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/stories?type=$collection&ordering=-view_count&page=$page".toHttpUrl(), headers)

    override fun popularMangaParse(response: Response) =
        json.decodeFromString<MangaOwlToStories>(response.body.string()).toMangasPage()

    // Latest

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl/stories?type=$collection&ordering=-modified_at&page=$page".toHttpUrl(), headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty() || filters.isEmpty()) {
            // Search won't work together with filter
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            GET(url, headers)
        } else {
            val url = "$apiUrl/stories?type=$collection".toHttpUrl().newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("ordering", filter.toUriPart())
                    }
                    is StatusFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                    is GenresFilter ->
                        filter.state
                            .filter { it.state }
                            .forEach { url.addQueryParameter("genres", it.uriPart) }
                    else -> {}
                }
            }

            url.addQueryParameter("page", page.toString())
            GET(url.build(), headers)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Manga summary page
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl/stories/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response) =
        json.decodeFromString<MangaOwlToStory>(response.body.string()).toSManga()

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/comic/${manga.url}"
    }

    // Chapters
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) =
        json.decodeFromString<MangaOwlToStory>(response.body.string()).chaptersList

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/chapters/$id/images?page_size=1000", headers)
    }

    override fun pageListParse(response: Response) =
        json.decodeFromString<MangaOwlToChapterPages>(response.body.string()).toPages()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("Search query won't use filters"),
        GenresFilter(genresList),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private const val MIRROR_PREF_KEY = "MIRROR"
        private val MIRROR_PREF_ENTRIES get() = arrayOf(
            "mangaowl.to",
            "mangabuddy.to",
            "mangafreak.to",
            "toonily.to",
            "manganato.so",
            "mangakakalot.so", // Redirected from mangago.to
        )
        private val MIRROR_PREF_ENTRY_VALUES get() = arrayOf(
            "mangaowl.to",
            "mangabuddy.to",
            "mangafreak.to",
            "toonily.to",
            "manganato.so",
            "mangago.to", // API for domain mangakakalot.so
        )
        private val MIRROR_PREF_DEFAULT_VALUE get() = MIRROR_PREF_ENTRY_VALUES[0]

        const val ONGOING = "ongoing"
        const val COMPLETED = "completed"
    }
}
