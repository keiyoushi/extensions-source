package eu.kanade.tachiyomi.extension.ru.ninegrid

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class NineGrid :
    HttpSource(),
    ConfigurableSource {

    override val name = "NineGrid"
    override val lang = "ru"
    override val supportsLatest = true

    private val preferences = getPreferences {
        getString(PREF_DEFAULT_BASE_URL, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != DEFAULT_BASE_URL) {
                edit()
                    .putString(PREF_BASE_URL, DEFAULT_BASE_URL)
                    .putString(PREF_DEFAULT_BASE_URL, DEFAULT_BASE_URL)
                    .apply()
            }
        }
    }

    override val baseUrl: String
        get() = preferences.getString(PREF_BASE_URL, DEFAULT_BASE_URL)!!.trimEnd('/')

    private val apiKey: String
        get() = preferences.getString(PREF_API_KEY, "") ?: ""

    private val apiBase: String
        get() = "$baseUrl/api/external/v1"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            add("Authorization", "Bearer $apiKey")
        }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "popular")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SeriesListResponse>()
        val mangas = data.content.map {
            it.toSManga().apply {
                thumbnail_url = "$apiBase/series/$id/thumbnail"
            }
        }
        return MangasPage(mangas, data.page + 1 < data.totalPages)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "latest")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("q", query)

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.selected)
                is PublisherFilter -> if (filter.state.isNotBlank()) {
                    url.addQueryParameter("publisher", filter.state)
                }
                is YearFilter -> if (filter.state.isNotBlank()) {
                    url.addQueryParameter("year", filter.state)
                }
                is GenreFilter -> filter.state.filter { it.state }.forEach { genre ->
                    url.addQueryParameter("genre", genre.name)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Manga Details

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiBase/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val s = response.parseAs<SeriesDto>()
        return s.toSManga().apply {
            thumbnail_url = "$apiBase/series/${s.id}/thumbnail"
            initialized = true
        }
    }

    // Chapter List

    override fun chapterListRequest(manga: SManga): Request = GET("$apiBase/series/${manga.url}/issues", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<IssuesResponse>()
        val chapters = mutableListOf<SChapter>()

        for (issue in data.issues) {
            for (t in issue.translations) {
                val teamLabel = t.teamNames.takeIf { it.isNotEmpty() }
                    ?.joinToString()

                chapters.add(
                    SChapter.create().apply {
                        url = "/translations/${t.id}/pages"
                        name = buildString {
                            append("#${issue.number}")
                            if (!issue.name.isNullOrBlank()) append(" — ${issue.name}")
                            if (issue.translations.size > 1 && teamLabel != null) {
                                append(" [$teamLabel]")
                            }
                        }
                        chapter_number = issue.number
                            .replace(ANNUAL_REGEX, "1000.")
                            .toFloatOrNull() ?: -1f
                        date_upload = DATE_FORMAT.tryParse(t.createdAt)
                        scanlator = teamLabel
                    },
                )
            }
        }

        return chapters.reversed()
    }

    // Page List

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBase${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PagesResponse>()
        return data.pages.map { Page(it.index, imageUrl = it.url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        PublisherFilter(),
        YearFilter(),
        GenreFilter(getGenreList()),
    )

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL
            title = "URL сервера"
            summary = "По умолчанию: $DEFAULT_BASE_URL"
            setDefaultValue(DEFAULT_BASE_URL)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_API_KEY
            title = "API-ключ"
            summary = "Для трекинга прогресса"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://9grid.cc"
        private const val PREF_BASE_URL = "pref_base_url"
        private const val PREF_DEFAULT_BASE_URL = "pref_default_base_url"
        private const val PREF_API_KEY = "pref_api_key"
        const val SEARCH_PREFIX = "id:"

        private val ANNUAL_REGEX = Regex("^annual\\s*", RegexOption.IGNORE_CASE)
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    }
}
