package eu.kanade.tachiyomi.extension.pt.geasscomics

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
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class GeassComics :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = baseUrl.replace("://", "://api.")

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================= Popular ================================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchWorks(worksUrl(page).addQueryParameter("sortBy", "rating"))

    // ============================= Latest =================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchWorks(worksUrl(page).addQueryParameter("sortBy", "recent"))

    // ============================= Search =================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = worksUrl(page).apply {
            if (query.isNotBlank()) addQueryParameter("q", query)

            val sort = filters.firstInstance<SortFilter>()
            addQueryParameter("sortBy", sort.selected)
            addQueryParameter("sortDir", sort.order)
            filters.firstInstance<TypeFilter>().selected?.let { addQueryParameter("types", it) }
            filters.firstInstance<StatusFilter>().selected?.let { addQueryParameter("status", it) }

            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter { it.state }?.map { it.id }
                ?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("genres", it.joinToString(",")) }
            filters.firstInstanceOrNull<TagFilter>()?.state
                ?.filter { it.state }?.map { it.id }
                ?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("tags", it.joinToString(",")) }
        }
        return fetchWorks(url)
    }

    private fun worksUrl(page: Int): HttpUrl.Builder = "$apiUrl/api/works".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", PAGE_LIMIT.toString())
        .apply { if (!showNsfwPref()) addQueryParameter("safe", "true") }

    private suspend fun fetchWorks(url: HttpUrl.Builder): MangasPage {
        val result = client.get(url.build()).parseAs<ApiResponse<WorkListDto>>().data
        return MangasPage(result.items.map { it.toSManga() }, result.hasNextPage)
    }

    // ============================= Details ================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removePrefix("/manga/")
        val work = client.get("$apiUrl/api/works/$slug").parseAs<ApiResponse<WorkDto>>().data

        return SMangaUpdate(work.toSManga(), work.chapters.map { it.toSChapter(slug) })
    }

    // ============================= Pages ==================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (mangaSlug, number) = chapter.slugAndNumber()
        val result = client.get("$apiUrl/api/works/$mangaSlug/chapters/$number").parseAs<ApiResponse<ChapterPagesDto>>()
        return result.data.pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    // ============================= Utils ==================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/work/${manga.url.removePrefix("/manga/")}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaSlug, number) = chapter.slugAndNumber()
        return "$baseUrl/read/$mangaSlug/$number"
    }

    // chapter url: /chapter/{id}/{mangaSlug}/{number}
    private fun SChapter.slugAndNumber(): Pair<String, String> {
        val segments = url.split("/")
        return segments[3] to segments[4]
    }

    // ============================= Filters ================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async { client.get("$apiUrl/api/genres").parseAs<ApiResponse<List<GenreTagDto>>>().data }
        val tags = async { client.get("$apiUrl/api/tags").parseAs<ApiResponse<List<GenreTagDto>>>().data }

        FilterData(genres.await(), tags.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        val showNsfw = showNsfwPref()

        val genres = filterData?.genres.orEmpty()
            .filter { showNsfw || !it.isNsfw }
            .map { it.label to it.slug }
        val tags = filterData?.tags.orEmpty()
            .map { it.label to it.slug }

        return getFilters(genres, tags)
    }

    // ============================= Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT_KEY
            title = "Exibir conteúdo adulto"
            summary = "Habilita a visualização de mangás Hentai nas listas."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private fun showNsfwPref() = preferences.getBoolean(PREF_ADULT_KEY, false)

    companion object {
        private const val PAGE_LIMIT = 24
        private const val PREF_ADULT_KEY = "pref_adult_content"
    }
}
