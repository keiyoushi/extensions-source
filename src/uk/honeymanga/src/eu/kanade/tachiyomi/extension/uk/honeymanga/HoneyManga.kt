package eu.kanade.tachiyomi.extension.uk.honeymanga

import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class HoneyManga :
    KeiSource(),
    ConfigurableSource {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrlHost get() = "data.api.$domain"
    private val apiUrl get() = "https://data.api.$domain"
    private val searchApiUrl get() = "https://search.api.$domain/v2/manga/pattern"
    private val imgUrl = "https://hmvolumestorage.b-cdn.net/public-resources"

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(10) { it.host == apiUrlHost }
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(page, "likes")

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(page, "lastUpdated")

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            if (query.length < 3) {
                throw Exception("Запит має містити щонайменше 3 символи / The query must contain at least 3 characters")
            }
            val url = searchApiUrl.toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()

            client.get(url).use { response ->
                val blockedGenres = blockGenres()
                val blockedTypes = blockTypes()
                val contentShown = contentType()
                val result = response.parseAs<List<ResponseData>>()
                val mangas = result.mapNotNull { it.toSManga(imgUrl, blockedTypes, blockedGenres, contentShown) }
                return MangasPage(mangas, false) // search by Name doesn't have pages
            }
        }

        return makeCatalogRequest(page, "likes", filters)
    }

    // =============================== Latest/Popular/Search Utilities ===============================
    private suspend fun makeCatalogRequest(page: Int, sortBy: String, filters: FilterList? = null): MangasPage {
        val searchFilters = mutableListOf<SearchFilter>()
        val setSearchSort = SearchSort(sortBy = sortBy, sortOrder = "DESC")

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
                is ContentTypeFilter -> filter.selected?.takeIf { it != "all" }?.let { searchFilters.add(SearchFilter("adult", it, listOf("18+"))) }
                else -> {}
            }
        }

        // Add ignored genres and types from preferences to Popular/Latest tab
        if (filters == null) {
            val blockedTypes = blockTypes()
            val blockedGenres = blockGenres()
            val contentShown = contentType()

            if (blockedTypes.isNotEmpty() && !searchFilters.any { it.filterBy == "type" && it.filterOperator == "NOT_IN" }) {
                searchFilters.add(SearchFilter("type", "NOT_IN", blockedTypes.toList()))
            }
            if (blockedGenres.isNotEmpty() && !searchFilters.any { it.filterBy == "genres" && it.filterOperator == "NOT_IN" }) {
                searchFilters.add(SearchFilter("genres", "NOT_IN", blockedGenres.toList()))
            }
            if (contentShown != "all" && !searchFilters.any { it.filterBy == "adult" }) {
                searchFilters.add(SearchFilter("adult", contentShown, listOf("18+")))
            }
        }

        val body = SearchRequestBody(
            page = page,
            pageSize = DEFAULT_PAGE_SIZE,
            sort = setSearchSort,
            filters = searchFilters.ifEmpty { null },
        ).toJsonRequestBody()

        client.post("$apiUrl/v2/manga/cursor-list", body).use { response ->
            val result = response.parseAs<CatalogResponseDto>()
            val mangas = result.data.mapNotNull { it.toSManga(imgUrl) }
            return MangasPage(mangas, result.hasNextPage)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "book") {
            val tmpManga = SManga.create().apply {
                this.url = url.toString()
            }

            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }

        return null
    }

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga): String = if (manga.url.contains("/book/")) { // old url compatibility
        manga.url
    } else {
        "$baseUrl/book/${manga.url}"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = if (manga.url.contains("/book/")) { // old url compatibility
            manga.url.substringAfterLast('/')
        } else {
            manga.url
        }

        val mangaAsync = async {
            if (fetchDetails) {
                val url = "$apiUrl/manga/$mangaId"
                val data = client.get(url).use { it.parseAs<CompleteMangaDto>() }
                data.toSManga(imgUrl)
            } else {
                manga
            }
        }

        val chaptersAsync = async {
            if (fetchChapters) {
                val body = ChapterRequestBody(
                    mangaId = mangaId,
                    page = 1,
                    pageSize = 10000,
                    sortOrder = "DESC",
                ).toJsonRequestBody()
                val chaptersUrl = "$apiUrl/v2/chapter/cursor-list"
                val data = client.post(chaptersUrl, body).use { it.parseAs<ChapterResponse>().data }
                val hideLocked = hideLocked()
                data.mapNotNull { it.toSChapter(hideLocked) }
            } else {
                chapters
            }
        }

        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    // =============================== Pages ================================
    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.contains("/read/")) { // old url compatibility
        chapter.url
    } else {
        "$baseUrl/read/${chapter.url}/${chapter.memo["mangaId"]!!.string}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val hideLocked = hideLocked()
        if (!hideLocked && chapter.memo["locked"]?.boolean == true) throw Exception("Розділ лише для меценатів.")
        val chapterId = if (chapter.url.contains("/read/")) { // old url compatibility
            chapter.url.substringBeforeLast('/').substringAfterLast('/')
        } else {
            chapter.url
        }
        val url = "$apiUrl/chapter/frames/$chapterId"
        return client.get(url).use { it.parseAs<ChapterPages>().toPageList(imgUrl) }
    }

    // ============================= Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderBy(),
        TypeFilter(),
        HideTypeFilter(blockTypes()),
        Filter.Separator(),
        GenresFilter(blockGenres()),
        Filter.Separator(),
        TagsFilter(),
        StatusFilter(),
        TranslationFilter(),
        ContentTypeFilter(),
    )

    // ============================= Utilities ==============================
    companion object {
        private const val DEFAULT_PAGE_SIZE = 30
        private const val GENRES_PREF = "pref_genres_exclude"
        private const val GENRES_PREF_TITLE = "Приховані жанри"
        private const val GENRES_PREF_DIALOG = "Виберіть жанри які потрібно сховати"
        private const val TYPE_PREF = "pref_types_exclude"
        private const val TYPE_PREF_TITLE = "Приховані категорії"
        private const val TYPE_PREF_DIALOG = "Виберіть категорії які потрібно сховати"
        private const val DEFAULT_TYPE_BLOCK = "Новела"
        private const val CONTENT_PREF = "pref_content_type"
        private const val CONTENT_PREF_TITLE = "Виберіть тип контенту для відображення"
        private const val HIDE_LOCKED_CHAPTERS = "hide_locked_chapters"
        private const val HIDE_LOCKED_CHAPTERS_TITLE = "Приховувати платні розділи"
        private const val HIDE_LOCKED_CHAPTERS_SUM = "Може викликати помилки при оновленні. Будуть відмічені іконкою: \uD83D\uDD12"
    }

    // ============================ Preferences =============================

    private fun blockGenres(): Set<String> = preferences.getStringSet(GENRES_PREF, emptySet()) ?: emptySet()
    private fun blockTypes(): Set<String> = preferences.getStringSet(TYPE_PREF, setOf(DEFAULT_TYPE_BLOCK)) ?: setOf(DEFAULT_TYPE_BLOCK)
    private fun contentType(): String = preferences.getString(CONTENT_PREF, "all") ?: "all"
    private fun hideLocked(): Boolean = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)

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

        ListPreference(screen.context).apply {
            key = CONTENT_PREF
            title = CONTENT_PREF_TITLE
            entries = arrayOf("Весь контент, без обмежень", "Без контенту 18+", "Лише 18+")
            entryValues = arrayOf("all", "NOT_IN", "IN")
            summary = "%s"
            setDefaultValue("all")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = HIDE_LOCKED_CHAPTERS_TITLE
            summary = HIDE_LOCKED_CHAPTERS_SUM
            setDefaultValue(true)
        }.let(screen::addPreference)
    }
}
