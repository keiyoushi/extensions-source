package eu.kanade.tachiyomi.extension.uk.dgmanga

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.collections.joinToString
import kotlin.getValue
import kotlin.text.ifEmpty

@Source
abstract class DGManga :
    KeiSource(),
    ConfigurableSource {

    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://$domain/api"

    private val preferences by getPreferencesLazy()

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(page, "popular")

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(page, "updated")

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty() && query.length < 2) {
            throw Exception("Запит має містити щонайменше 2 символи / The query must contain at least 2 characters")
        }
        return makeCatalogRequest(page, "popular", query, filters)
    }

    // ============================== Search ===============================
    private suspend fun makeCatalogRequest(page: Int, sortBy: String, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = "$apiUrl/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "28")
            query?.let { addQueryParameter("q", it) }

            filters?.forEach { filter ->
                when (filter) {
                    is OrderBy -> filter.selected?.let { addQueryParameter("sort", it) }
                    is TypeFilter -> filter.selected?.let { addQueryParameter("type", it) }
                    is StatusFilter -> filter.selected?.let { addQueryParameter("status", it) }
                    is TranslationStatusFilter -> filter.selected?.let { addQueryParameter("translation_status", it) }
                    is GenresFilter -> filter.active?.let { addQueryParameter("genres", filter.selectedValues.joinToString(",")) }
                    is TagsFilter -> filter.active?.let { addQueryParameter("tags", filter.selectedValues.joinToString(",")) }
                    is LicensedFilter -> filter.selected?.let { addQueryParameter("isLicensed", it) }
                    else -> {}
                }
            }

            if (filters == null) {
                if (hideLicensedInSearch) addQueryParameter("isLicensed", "false")
                addQueryParameter("sort", sortBy)
            }
        }.build()

        client.get(url).use { response ->
            val data = response.parseAs<CatalogResponseDto>()
            val ignoredGenres = ignoreGenres()
            val mangas = data.titles.mapNotNull { it.toSManga(ignoredGenres) }

            return MangasPage(mangas, (data.totalPages > data.page))
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "title") {
            val tmpManga = SManga.create().apply {
                this.url = url.pathSegments[1]
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga ===============================
    // WebView url
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaAsync = async {
            if (fetchDetails) {
                val url = "$apiUrl/titles/${manga.url}"
                client.get(url).use { it.parseAs<SMangaDto>().toSManga() }
            } else {
                manga
            }
        }

        val chaptersAsync = async {
            if (fetchChapters) {
                val chaptersUrl = "$apiUrl/chapters/title/${manga.url}"
                val data = client.get(chaptersUrl).use { it.parseAs<List<ChapterResponseDto>>() }
                data.map { it.toSChapter() }
            } else {
                chapters
            }
        }

        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    // ============================== Manga/Chapters ===============================
    // WebView url
    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterId, chapterNumber, chapterTitle) = chapter.url.split("/", limit = 3)
        return "$baseUrl/read/$chapterTitle/$chapterNumber?chapterId=$chapterId"
    }

    // ============================== Images ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterId, _, _) = chapter.url.split("/", limit = 3)
        val url = "$apiUrl/chapters/$chapterId"
        val data = client.get(url).parseAs<PagesList>()
        return data.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page)
        }
    }

    // =========================== Related Manga (Komikku) ============================
    override val supportsRelatedMangas: Boolean = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$apiUrl/titles/${manga.url}/similar"
        val data = client.get(url).parseAs<List<SearchResponseTitlesDto>>()
        val ignoredGenres = ignoreGenres()
        return data.mapNotNull { it.toSManga(ignoredGenres) }
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderBy(),
        GenresFilter(),
        Filter.Separator(),
        TagsFilter(),
        TypeFilter(),
        StatusFilter(),
        TranslationStatusFilter(),
        LicensedFilter(),
    )

    // ============================== Preference ===============================
    private fun ignoreGenres(): Set<String> = preferences.getStringSet(SITE_GENRES_PREF, emptySet<String>())!!
    private val hideLicensedInSearch = preferences.getBoolean(SITE_LICENSED_SEARCH, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SITE_GENRES_PREF
            title = SITE_GENRES_PREF_TITLE
            val tags = GenresFilter.values
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = tags.filter { it.second in ignoreGenres() }
                .joinToString { it.first }
                .ifEmpty { "Не вибрано" } + SITE_GENRES_PREF_SUM
            dialogTitle = "Виберіть категорії які потрібно сховати"
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { "Не вибрано" } + SITE_GENRES_PREF_SUM
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SITE_LICENSED_SEARCH
            title = SITE_LICENSED_SEARCH_TITLE
            summary = SITE_LICENSED_SEARCH_SUM
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val SITE_GENRES_PREF = "site_hidden_genres"
        private const val SITE_GENRES_PREF_TITLE = "Приховані категорії"
        private const val SITE_GENRES_PREF_SUM = "\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'."
        private const val SITE_LICENSED_SEARCH = "site_hide_licensed"
        private const val SITE_LICENSED_SEARCH_TITLE = "Скривати ліцензовані твори"
        private const val SITE_LICENSED_SEARCH_SUM = "\nⓘ При зміні цього параметра необхідно перезапустити програму з повною зупинкою."
    }
}
