package eu.kanade.tachiyomi.extension.en.mangauno

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

@Source
abstract class Mangauno :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiUrl: String
        get() = "$baseUrl/api"

    private val useEnglishTitle: Boolean
        get() = preferences.getString(TITLE_PREF, "english") == "english"

    private var fetchFiltersStatus = FetchFilterStatus.NOT_FETCHED
    private var facets: FacetsDto? = null

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/list/popular?page=$page&limit=$PAGE_SIZE", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ListResponse>()
        val mangas = data.toSMangaList(useEnglishTitle)
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/list/latest?page=$page&limit=$PAGE_SIZE", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search/advanced".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("limit", PAGE_SIZE.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("title", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("types", filter.toUriPart())
                is StatusFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("statuses", filter.toUriPart())
                is SortFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("sort", filter.toUriPart())
                is YearGroup -> {
                    val min = filter.state[0].state
                    val max = filter.state[1].state
                    if (min.isNotEmpty()) url.addQueryParameter("yearMin", min)
                    if (max.isNotEmpty()) url.addQueryParameter("yearMax", max)
                }
                is AdultFilter -> if (filter.state) url.addQueryParameter("adult", "1")
                is GenreGroup -> {
                    val selected = filter.state.filter { it.state }.map { it.name }
                    if (selected.isNotEmpty()) url.addQueryParameter("genres", selected.joinToString(","))
                }
                is TagGroup -> {
                    val selected = filter.state.filter { it.state }.map { it.name }
                    if (selected.isNotEmpty()) url.addQueryParameter("tags", selected.joinToString(","))
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<DetailsResponse>()
        return data.toSManga(useEnglishTitle).apply {
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<DetailsResponse>()
        return data.toSChapterList(dateFormat)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListResponse>()
        return data.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/m/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/r/${chapter.url}"

    private fun fetchFilters() {
        fetchFiltersStatus = FetchFilterStatus.FETCHING
        thread {
            try {
                val response = client.newCall(GET("$apiUrl/search/facets", headers)).execute()
                facets = response.parseAs<FacetsDto>()
                fetchFiltersStatus = FetchFilterStatus.FETCHED
            } catch (e: Exception) {
                fetchFiltersStatus = FetchFilterStatus.FAILED
            }
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            AdultFilter(),
            TypeFilter(),
            StatusFilter(),
            SortFilter(),
            YearGroup(),
        )

        when (fetchFiltersStatus) {
            FetchFilterStatus.NOT_FETCHED, FetchFilterStatus.FAILED -> {
                filters.add(Filter.Separator())
                filters.add(Filter.Header("Press 'Reset' to load genres and tags"))
                fetchFilters()
            }
            FetchFilterStatus.FETCHING -> {
                filters.add(Filter.Separator())
                filters.add(Filter.Header("Loading genres and tags... Press 'Reset' to refresh"))
            }
            FetchFilterStatus.FETCHED -> {
                val genreList = facets?.genres?.map { CheckBoxFilter(it.name) } ?: emptyList()
                val tagList = facets?.tags?.map { CheckBoxFilter(it.name) } ?: emptyList()

                if (genreList.isNotEmpty()) {
                    filters.add(GenreGroup(genreList))
                }
                if (tagList.isNotEmpty()) {
                    filters.add(TagGroup(tagList))
                }
            }
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val titlePref = ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = "Title Language"
            entries = arrayOf("English Title", "Japanese Title")
            entryValues = arrayOf("english", "japanese")
            setDefaultValue("english")
            summary = "%s"
        }
        screen.addPreference(titlePref)
    }

    companion object {
        const val IMG_API_URL = "https://xz7.fstr-cdn.com"
        private const val TITLE_PREF = "PREF_TITLE_LANG"
        private const val PAGE_SIZE = 24
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}

private enum class FetchFilterStatus {
    NOT_FETCHED,
    FETCHING,
    FETCHED,
    FAILED,
}
