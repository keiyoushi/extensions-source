package eu.kanade.tachiyomi.extension.ru.ninegrid

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class NineGrid :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    private val apiKey: String
        get() = preferences.getString(PREF_API_KEY, "") ?: ""

    private val apiBase: String
        get() = "$baseUrl/api/external/v1"

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            add("Authorization", "Bearer $apiKey")
        }
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "popular")
            .build()
        return getSeriesList(url)
    }

    private suspend fun getSeriesList(url: HttpUrl): MangasPage {
        val data = client.get(url).parseAs<SeriesListResponse>()
        val mangas = data.content.map { it.toSManga(apiBase) }
        return MangasPage(mangas, data.page + 1 < data.totalPages)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiBase/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", "20")
            .addQueryParameter("sort", "latest")
            .build()
        return getSeriesList(url)
    }

    // Search

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
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

        return getSeriesList(url.build())
    }

    // Manga Details & Chapter List

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                client.get("$apiBase/series/${manga.url}").parseAs<SeriesDto>().toSManga(apiBase)
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) getChapterList(manga) else chapters
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val data = client.get("$apiBase/series/${manga.url}/issues").parseAs<IssuesResponse>()
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
                        date_upload = Instant.tryParse(t.createdAt)
                        scanlator = teamLabel
                    },
                )
            }
        }

        return chapters.reversed()
    }

    // Page List

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get("$apiBase${chapter.url}").parseAs<PagesResponse>()
        return data.pages.map { Page(it.index, imageUrl = it.url) }
    }

    // Filters

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        PublisherFilter(),
        YearFilter(),
        GenreFilter(getGenreList()),
    )

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_API_KEY
            title = "API-ключ"
            summary = "Для трекинга прогресса"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_API_KEY = "pref_api_key"

        private val ANNUAL_REGEX = Regex("^annual\\s*", RegexOption.IGNORE_CASE)
    }
}
