package eu.kanade.tachiyomi.extension.uk.faust

import androidx.preference.MultiSelectListPreference
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
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Calendar

@Source
abstract class Faust :
    KeiSource(),
    ConfigurableSource {

    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://$domain/api"

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(10) { it.host == domain }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Content-Type", "application/json")

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeSearchRequest(sort = "-popularity", page = page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeSearchRequest(sort = "+updated", page = page)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeSearchRequest(null, page, query, filters)

    // ============================== Search/Utility ===============================
    private suspend fun makeSearchRequest(sort: String? = null, page: Int, query: String? = "", filters: FilterList? = null): MangasPage {
        val url = "$apiUrl/titles/search/library"
        val checkYear = Calendar.getInstance().get(Calendar.YEAR) + 1
        val requestBodyData = SearchRequestBody(
            searchQuery = query,
            page = page,
            pageSize = 30,
        )

        filters?.forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val asc = if (filter.state?.ascending == true) "-" else "+"
                    requestBodyData.sortBy = "$asc${filter.selected}"
                }
                is CategoriesFilter -> filter.selected?.let { requestBodyData.mangaType = it }
                is TranslationStatusFilter -> filter.selected?.let { requestBodyData.translationStatus = it }
                is PublicationStatusFilter -> filter.selected?.let { requestBodyData.publicationStatus = it }
                is AgeStatusFilter -> filter.selected?.let { requestBodyData.ageBracket = it }
                is YearRangeFilter -> {
                    filter.minValue?.let { requestBodyData.yearFrom = checkMinRange(it, 1970, checkYear) }
                    filter.maxValue?.let { requestBodyData.yearTo = checkMaxRange(it, 1970, checkYear) }
                }
                is ChaptersRangeFilter -> {
                    filter.minValue?.let { requestBodyData.minChapters = checkMinRange(it, 0, 3000) }
                    filter.maxValue?.let { requestBodyData.maxChapters = checkMaxRange(it, 0, 3000) }
                }
                is GenresFilter -> {
                    filter.included?.let { requestBodyData.genreIds = JsonArray(it.map(::JsonPrimitive)) }
                    filter.excluded?.let { requestBodyData.excludeGenreIds = JsonArray(it.map(::JsonPrimitive)) }
                }
                is TagsFilter -> {
                    filter.included?.let { requestBodyData.tagIds = JsonArray(it.map(::JsonPrimitive)) }
                    filter.excluded?.let { requestBodyData.excludeTagIds = JsonArray(it.map(::JsonPrimitive)) }
                }
                else -> {}
            }
        }

        if (filters.isNullOrEmpty()) {
            val ignoredGenres = ignoreGenres()
            if (ignoredGenres.isNotEmpty()) {
                requestBodyData.excludeGenreIds = JsonArray(ignoredGenres.map(::JsonPrimitive))
            }
            requestBodyData.sortBy = sort
        }

        val body = requestBodyData.toJsonRequestBody()

        client.post(url, body).use { response ->
            val data = response.parseAs<SearchResponseDto>()
            val mangas = data.titles.map { it.toSManga() }
            return MangasPage(mangas, (data.totalPages > data.page))
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "manga") {
            val tmpManga = SManga.create().apply {
                this.url = url.pathSegments[1]
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga / Manga ===============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/titles/${manga.url}"
        client.get(url).use { response ->
            val data = response.parseAs<SMangaDto>()
            val manga = data.toSManga()
            val chapters = data.volumes
                .flatMap {
                    it.chapters.map { chapter ->
                        chapter.toSChapter(manga.url)
                    }
                }
                .reversed()
            return SMangaUpdate(manga, chapters)
        }
    }

    // ============================== Manga / Chapters ===============================
    // WebView url
    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterSlug, seriesSlug) = chapter.url.split("/", limit = 2)
        val pieces = chapterSlug.split("-")
        return "$baseUrl/manga/$seriesSlug/${pieces[0]}-${pieces[1]}/${pieces[2]}-${pieces[3]}"
    }

    // ============================== Images ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterSlug, seriesSlug) = chapter.url.split("/", limit = 2)
        val url = "$apiUrl/chapters/$chapterSlug?titleSlug=$seriesSlug"
        val data = client.get(url).use { it.parseAs<ChapterResponseList>() }
        return data.pages.map { page ->
            Page(page.pageNumber, imageUrl = page.blobName)
        }
    }

    // ============================== Utilities/Filter ===============================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async {
            client.get("$apiUrl/genres/paged?page=1&pageSize=100").use { genres ->
                genres.parseAs<GenreListPageDto>().items.map { it.name to it.id }
            }
        }
        val tags = async {
            client.get("$apiUrl/tags/paged?page=1&pageSize=150").use { tags ->
                tags.parseAs<GenreListPageDto>().items.map { it.name to it.id }
            }
        }

        FiltersDto(
            genres = genres.await(),
            tags = tags.await(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        data?.let {
            val dto = it.parseAs<FiltersDto>()
            GenresFilter.options = dto.genres
            TagsFilter.options = dto.tags
        }
        val ignoredGenres = ignoreGenres()
        val filters = mutableListOf(
            OrderBy(),
            Filter.Separator(),
            GenresFilter(ignoredGenres),
            Filter.Separator(),
            TagsFilter(),
            Filter.Separator(),
            CategoriesFilter(),
            TranslationStatusFilter(),
            PublicationStatusFilter(),
            AgeStatusFilter(),
            Filter.Separator(),
            ChaptersRangeFilter(),
            Filter.Separator(),
            YearRangeFilter(),
        )
        return FilterList(filters)
    }

    // ============================== Utilities ===============================
    private fun checkMinRange(input: String?, min: Int, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return min.toString()
        if (value !in min..max) return min.toString()
        return value.toString()
    }
    private fun checkMaxRange(input: String?, min: Int, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return max.toString()
        if (value !in min..max) return max.toString()
        return value.toString()
    }

    // ============================== Preferences ===============================
    private fun ignoreGenres(): Set<String> = preferences.getStringSet(SITE_GENRES_PREF, emptySet<String>())!!
    private fun ignoreGenresTitles(): String? = preferences.getString(SITE_GENRES_PREF_TITLES, null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SITE_GENRES_PREF
            title = SITE_GENRES_PREF_TITLE
            val tags = GenresFilter.options
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = (ignoreGenresTitles() ?: "Не вибрано") + SITE_GENRES_PREF_SUM
            dialogTitle = "Виберіть категорії які потрібно сховати"
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<*>
                preferences.edit().putString(
                    SITE_GENRES_PREF_TITLES,
                    tags.filter { it.second in selected }
                        .joinToString { it.first }
                        .ifEmpty { "Не вибрано" },
                ).apply()
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { "Не вибрано" } + SITE_GENRES_PREF_SUM
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val SITE_GENRES_PREF = "site_hidden_genres"
        private const val SITE_GENRES_PREF_TITLES = "site_hidden_genres_titles"
        private const val SITE_GENRES_PREF_TITLE = "Приховані категорії"
        private const val SITE_GENRES_PREF_SUM = "\n\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'.\n\nⓘЯкщо список категорій порожній, зайдіть 'Огляд' -> 'Джерела' -> Faust -> 'Фільтр' та натисніть 'Скинути'"
    }
}
