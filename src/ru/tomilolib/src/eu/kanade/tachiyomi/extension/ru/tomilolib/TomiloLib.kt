package eu.kanade.tachiyomi.extension.ru.tomilolib

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class TomiloLib :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val apiUrl by lazy { "$baseUrl/api" }

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    // Global headers only carry a browser-like Referer. This same header set is
    // reused by Coil for cover/page images, so it must NOT include an
    // "Accept: application/json" header (the CDN returns 403 for images).
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Headers for REST/JSON API calls only.
    private val apiHeaders by lazy {
        headersBuilder()
            .add("Accept", "application/json")
            .build()
    }

    private val preferences by getPreferencesLazy()

    private val showAdult: Boolean
        get() = preferences.getBoolean(PREF_SHOW_ADULT, false)

    private val hidePaidChapters: Boolean
        get() = preferences.getBoolean(PREF_HIDE_PAID, false)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/titles?sortBy=views&order=desc&page=$page&limit=$PAGE_LIMIT", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/titles?sortBy=updatedAt&order=desc&page=$page&limit=$PAGE_LIMIT", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangasPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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
        return GET(urlBuilder.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangasPage(response)

    private fun parseMangasPage(response: Response): MangasPage {
        val data = response.parseAs<ApiResponse<TitlesData>>().data
        val mangas = data.titles
            .filter { showAdult || !it.isAdult }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, data.pagination.page < data.pagination.pages)
    }

    // ============================== Details ===============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/${manga.url.substringBefore('/')}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.titleId()}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<ApiResponse<TitleDto>>().data.toSManga(baseUrl)

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable { getChapters(manga.titleId()) }

    private fun getChapters(titleId: String): List<SChapter> {
        val chapters = mutableListOf<ChapterDto>()
        var page = 1
        var totalPages: Int
        do {
            val data = client.newCall(chaptersPageRequest(titleId, page)).execute()
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

    private fun chaptersPageRequest(titleId: String, page: Int): Request = GET("$apiUrl/chapters?titleId=$titleId&page=$page&limit=$CHAPTERS_PER_PAGE", apiHeaders)

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/${chapter.url}", apiHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ApiResponse<ChapterDetailDto>>().data
        if (data.pages.isEmpty()) {
            if (data.isPaid) throw Exception("Глава платная и ещё не открыта бесплатно")
            return emptyList()
        }
        return data.pages.mapIndexed { i, url -> Page(i, imageUrl = resolveImageUrl(url, baseUrl)) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
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
