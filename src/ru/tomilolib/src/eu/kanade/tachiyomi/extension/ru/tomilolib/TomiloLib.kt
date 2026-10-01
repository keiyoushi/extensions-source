package eu.kanade.tachiyomi.extension.ru.tomilolib

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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class TomiloLib :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // Headers for REST/JSON API calls only. The global headers are reused by Coil
    // for cover/page images, so they must NOT include an "Accept: application/json"
    // header (the CDN returns 403 for images).
    private val apiHeaders: Headers
        get() = headersBuilder()
            .add("Accept", "application/json")
            .build()

    private val preferences by getPreferencesLazy()

    private val showAdult: Boolean
        get() = preferences.getBoolean(PREF_SHOW_ADULT, false)

    private val hidePaidChapters: Boolean
        get() = preferences.getBoolean(PREF_HIDE_PAID, false)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangasPage("$apiUrl/titles?sortBy=views&order=desc&page=$page&limit=$PAGE_LIMIT".toHttpUrl())

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangasPage("$apiUrl/titles?sortBy=updatedAt&order=desc&page=$page&limit=$PAGE_LIMIT".toHttpUrl())

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = "$apiUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())

        if (query.isNotBlank()) urlBuilder.addQueryParameter("search", query)

        var sortBy = "views"
        var order = "desc"

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> filter.selected()?.let { urlBuilder.addQueryParameter("type", it) }
                is StatusFilter -> filter.selected()?.let { urlBuilder.addQueryParameter("status", it) }
                is SortFilter -> {
                    sortBy = filter.selectedValue()
                    order = if (filter.ascending()) "asc" else "desc"
                }
                is GenreFilter ->
                    filter.state
                        .filter { it.state }
                        .forEach { urlBuilder.addQueryParameter("genres", it.name) }
                else -> {}
            }
        }

        urlBuilder.addQueryParameter("sortBy", sortBy)
        urlBuilder.addQueryParameter("order", order)
        return getMangasPage(urlBuilder.build())
    }

    private suspend fun getMangasPage(url: HttpUrl): MangasPage {
        val data = client.get(url, apiHeaders).parseAs<ApiResponse<TitlesData>>().data
        val mangas = data.titles
            .filter { showAdult || !it.isAdult }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, data.pagination.page < data.pagination.pages)
    }

    // ============================== Details ===============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/${manga.url.substringBefore('/')}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val titleId = manga.titleId()
        val details = async {
            if (fetchDetails) {
                client.get("$apiUrl/titles/$titleId", apiHeaders)
                    .parseAs<ApiResponse<TitleDto>>().data.toSManga(baseUrl)
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) getChapters(titleId) else chapters
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // ============================== Chapters ==============================

    private suspend fun getChapters(titleId: String): List<SChapter> {
        val chapters = mutableListOf<ChapterDto>()
        var page = 1
        var totalPages: Int
        do {
            val data = client.get("$apiUrl/chapters?titleId=$titleId&page=$page&limit=$CHAPTERS_PER_PAGE", apiHeaders)
                .parseAs<ApiResponse<ChaptersData>>().data
            chapters += data.chapters
            totalPages = data.pagination.pages
            page++
        } while (page <= totalPages)

        return chapters
            .filter { it.isPublished }
            .sortedByDescending { it.chapterNumber }
            .mapNotNull { it.toSChapter(hidePaidChapters) }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get("$apiUrl/chapters/${chapter.url}", apiHeaders)
            .parseAs<ApiResponse<ChapterDetailDto>>().data
        if (data.pages.isEmpty()) {
            if (data.isPaid) throw Exception("Глава платная и ещё не открыта бесплатно")
            return emptyList()
        }
        return data.pages.mapIndexed { i, url -> Page(i, imageUrl = resolveImageUrl(url, baseUrl)) }
    }

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Жанры (могут не комбинироваться с текстовым поиском)"),
        GenreFilter(),
    )

    // ============================= Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_ADULT
            title = "Показывать контент 18+"
            summary = "Включить тайтлы с пометкой 18+ в выдачу"
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PAID
            title = "Скрывать платные главы"
            summary = "Не показывать ещё не открытые платные главы в списке"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Mappers ===============================

    private fun SManga.titleId(): String = url.substringAfterLast('/')

    companion object {
        private const val PAGE_LIMIT = 30
        private const val CHAPTERS_PER_PAGE = 200
        private const val PREF_SHOW_ADULT = "pref_show_adult"
        private const val PREF_HIDE_PAID = "pref_hide_paid"
    }
}
