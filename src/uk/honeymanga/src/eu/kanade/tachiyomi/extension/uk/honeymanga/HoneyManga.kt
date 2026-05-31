package eu.kanade.tachiyomi.extension.uk.honeymanga

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.collections.emptySet
import kotlin.collections.ifEmpty

class HoneyManga :
    HttpSource(),
    ConfigurableSource {

    override val name = "HoneyManga"
    override val baseUrl = "https://honey-manga.com.ua"
    override val lang = "uk"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", "$baseUrl/")
        .add("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .rateLimitHost(API_URL.toHttpUrl(), 10)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = makeCatalogRequest(page, "likes")

    override fun popularMangaParse(response: Response) = parseAsCatalogResponse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = makeCatalogRequest(page, "lastUpdated")

    override fun latestUpdatesParse(response: Response) = parseAsCatalogResponse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            if (query.length < 3) {
                throw UnsupportedOperationException("Запит має містити щонайменше 3 символи / The query must contain at least 3 characters")
            }
            val url = "$SEARCH_API_URL/v2/manga/pattern".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        return makeCatalogRequest(page, "likes", filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val blockedGenres = blockGenres()
        val blockedTypes = blockTypes()

        val url = response.request.url
        if (url.queryParameter("query") != null) {
            val result = response.parseAs<List<ResponseData>>()
            val mangas = result.mapNotNull { it.toSManga(baseUrl, IMAGE_STORAGE_URL, blockedTypes, blockedGenres) }
            return MangasPage(mangas, false) // search by Name doesn't have pages
        }

        return parseAsCatalogResponse(response)
    }

    // =============================== Latest/Popular/Search Utilities ===============================
    private fun makeCatalogRequest(page: Int, sortBy: String, filters: FilterList? = null): Request {
        val searchFilters = mutableListOf<SearchFilter>()
        val setSearchSort = SearchSort(sortBy = sortBy, sortOrder = "DESC")
        val blockedTypes = blockTypes()
        val blockedGenres = blockGenres()

        filters?.forEach { filter ->
            when (filter) {
                is TranslationFilter -> filter.selected?.let { searchFilters.add(SearchFilter("translationStatus", "EQUAL", listOf(it))) }
                is StatusFilter -> filter.selected?.let { searchFilters.add(SearchFilter("titleStatus", "EQUAL", listOf(it))) }
                is TagsFilter -> {
                    filter.included?.let { searchFilters.add(SearchFilter("tags", "ALL", it)) }
                    filter.excluded?.let { searchFilters.add(SearchFilter("tags", "NOT_IN", it)) }
                }
                is OrderBy -> {
                    setSearchSort.sortBy = filter.selected
                    setSearchSort.sortOrder = if (filter.state?.ascending == true) "ASC" else "DESC"
                }
                is GenresFilter -> { // no need to add ignored genres from preferences since they are applied to filters
                    filter.included?.let { searchFilters.add(SearchFilter("genres", "ALL", it)) }
                    filter.excluded?.let { searchFilters.add(SearchFilter("genres", "NOT_IN", it)) }
                }
                is HideTypeFilter -> { // no need to add ignored types from preferences since they are applied to filters
                    filter.active?.let { searchFilters.add(SearchFilter("type", "NOT_IN", it)) }
                }
                is TypeFilter -> filter.selected?.let { searchFilters.add(SearchFilter("type", "EQUAL", listOf(it))) }
                else -> {}
            }
        }

        // Add ignored genres and types from preferences if it's not set by Filters (Popular/Latest tab)
        if (filters.isNullOrEmpty() && blockedTypes.isNotEmpty() && !searchFilters.any { it.filterBy == "type" && it.filterOperator == "NOT_IN" }) {
            searchFilters.add(SearchFilter("type", "NOT_IN", blockedTypes.toList()))
        }
        if (filters.isNullOrEmpty() && blockedGenres.isNotEmpty() && !searchFilters.any { it.filterBy == "genres" && it.filterOperator == "NOT_IN" }) {
            searchFilters.add(SearchFilter("genres", "NOT_IN", blockedGenres.toList()))
        }

        val body = SearchRequestBody(
            page = page,
            pageSize = DEFAULT_PAGE_SIZE,
            sort = setSearchSort,
            filters = searchFilters.ifEmpty { null },
        ).toJsonRequestBody()

        return POST("$API_URL/v2/manga/cursor-list", headers, body)
    }

    private fun parseAsCatalogResponse(response: Response): MangasPage {
        val result = response.parseAs<CatalogResponseDto>()
        val mangas = result.data.mapNotNull { it.toSManga(baseUrl, IMAGE_STORAGE_URL) }
        return MangasPage(mangas, result.hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast('/')
        val url = "$API_URL/manga/$mangaId"
        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast('/')
        return "$baseUrl/book/$mangaId"
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<CompleteMangaDto>().toSManga(baseUrl, IMAGE_STORAGE_URL)

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val body = ChapterRequestBody(
            mangaId = manga.url.substringAfterLast('/'),
            page = 1,
            pageSize = 10000,
            sortOrder = "DESC",
        ).toJsonRequestBody()
        return POST("$API_URL/v2/chapter/cursor-list", headers, body)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url.substringAfter("read/")
        return "$baseUrl/read/$url"
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ChapterResponse>().data.mapNotNull { it.toSChapter(baseUrl) }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringBeforeLast('/').substringAfterLast('/')
        val url = "$API_URL/chapter/frames/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterPages>().toPageList(IMAGE_STORAGE_URL)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Filters ==============================
    override fun getFilterList() = FilterList(
        OrderBy(),
        TypeFilter(),
        HideTypeFilter(blockTypes()),
        Filter.Separator(),
        GenresFilter(blockGenres()),
        Filter.Separator(),
        TagsFilter(),
        StatusFilter(),
        TranslationFilter(),
    )

    // ============================= Utilities ==============================
    companion object {
        private const val API_URL = "https://data.api.honey-manga.com.ua"
        private const val SEARCH_API_URL = "https://search.api.honey-manga.com.ua"
        private const val IMAGE_STORAGE_URL = "https://hmvolumestorage.b-cdn.net/public-resources"
        private const val DEFAULT_PAGE_SIZE = 30
        private const val GENRES_PREF = "pref_genres_exclude"
        private const val GENRES_PREF_TITLE = "Приховані жанри"
        private const val GENRES_PREF_DIALOG = "Виберіть жанри які потрібно сховати"
        private const val TYPE_PREF = "pref_types_exclude"
        private const val TYPE_PREF_TITLE = "Приховані категорії"
        private const val TYPE_PREF_DIALOG = "Виберіть категорії які потрібно сховати"
        private const val DEFAULT_TYPE_BLOCK = "Новела"
    }

    // ============================ Preferences =============================

    private fun blockGenres(): Set<String> = preferences.getStringSet(GENRES_PREF, emptySet<String>())!!
    private fun blockTypes(): Set<String> = preferences.getStringSet(TYPE_PREF, setOf(DEFAULT_TYPE_BLOCK))!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            val tags = GenresFilter.options.toTypedArray()
            key = GENRES_PREF
            title = GENRES_PREF_TITLE
            entries = tags
            entryValues = tags
            summary = blockGenres().joinToString()
            dialogTitle = GENRES_PREF_DIALOG
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<*>
                this.summary = selected.joinToString()
                true
            }
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            val blockedTypes = blockTypes()
            val types = HideTypeFilter.options.toTypedArray()
            key = TYPE_PREF
            title = TYPE_PREF_TITLE
            entries = types
            entryValues = types
            summary = blockedTypes.joinToString()
            dialogTitle = TYPE_PREF_DIALOG
            setDefaultValue(setOf(DEFAULT_TYPE_BLOCK))

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<*>
                this.summary = selected.joinToString()
                true
            }
        }.let(screen::addPreference)
    }
}
