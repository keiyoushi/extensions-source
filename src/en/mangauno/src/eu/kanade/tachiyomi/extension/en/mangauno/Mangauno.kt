package eu.kanade.tachiyomi.extension.en.mangauno

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Mangauno :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiUrl: String
        get() = "$baseUrl/api"

    private val useEnglishTitle: Boolean
        get() = preferences.getString(TITLE_PREF, "english") == "english"

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$apiUrl/list/popular?page=$page&limit=$PAGE_SIZE")
        val data = response.parseAs<ListResponse>()
        val mangas = data.toSMangaList(useEnglishTitle)
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$apiUrl/list/latest?page=$page&limit=$PAGE_SIZE")
        val data = response.parseAs<ListResponse>()
        val mangas = data.toSMangaList(useEnglishTitle)
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val response = client.get(url.build())
        val data = response.parseAs<ListResponse>()
        val mangas = data.toSMangaList(useEnglishTitle)
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$apiUrl/manga/${manga.url}")
        val data = response.parseAs<DetailsResponse>()
        return SMangaUpdate(
            data.toSManga(useEnglishTitle),
            data.toSChapterList(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val response = client.get("$apiUrl/chapter/$chapterId")
        val data = response.parseAs<PageListResponse>()
        return data.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/m/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/r/${chapter.url}"

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/search/facets").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            AdultFilter(),
            TypeFilter(),
            StatusFilter(),
            SortFilter(),
            YearGroup(),
        )

        data?.parseAs<FacetsDto>()?.let { facets ->
            val genreList = facets.genres.map { CheckBoxFilter(it.name) }
            val tagList = facets.tags.map { CheckBoxFilter(it.name) }

            if (genreList.isNotEmpty()) {
                filters.add(GenreGroup(genreList))
            }
            if (tagList.isNotEmpty()) {
                filters.add(TagGroup(tagList))
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
    }
}
