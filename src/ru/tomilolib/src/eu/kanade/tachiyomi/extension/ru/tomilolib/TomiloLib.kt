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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class TomiloLib :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val apiUrl by lazy { "$baseUrl/api" }
    private val cdnUrl = "https://tomilolib.s3.regru.cloud"

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
            .map { it.toSManga() }
        return MangasPage(mangas, data.pagination.page < data.pagination.pages)
    }

    // ============================== Details ===============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/${manga.url.substringBefore('/')}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.titleId()}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<ApiResponse<TitleDto>>().data.toSManga()

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
            .mapNotNull { it.toSChapter() }
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
        return data.pages.mapIndexed { i, url -> Page(i, imageUrl = resolveImageUrl(url)) }
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

    private fun TitleDto.toSManga(): SManga = SManga.create().apply {
        url = "$slug/$id"
        title = name.trim()
        thumbnail_url = resolveImageUrl(coverImage)
        author = this@toSManga.author
        artist = this@toSManga.artist
        genre = genres.joinToString()
        status = parseStatus(this@toSManga.status)
        description = buildString {
            val desc = this@toSManga.description
            if (desc.isNotBlank()) append(desc.trim())
            val others = altNames.filter { it.isNotBlank() }
            if (others.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Альтернативные названия: ")
                append(others.joinToString(" / "))
            }
        }
    }

    private fun ChapterDto.toSChapter(): SChapter? {
        val locked = isPaid && unlockPrice > 0 && isLockedNow(freeAt)
        if (locked && hidePaidChapters) return null
        return SChapter.create().apply {
            url = id
            name = this@toSChapter.name ?: "Глава ${chapterNumber.toString().removeSuffix(".0")}"
            chapter_number = chapterNumber.toFloat()
            date_upload = DATE_FORMAT.tryParse(releaseDate)
            if (locked) {
                scanlator = "🔒 Платно"
            }
        }
    }

    private fun isLockedNow(freeAt: String?): Boolean {
        val ts = DATE_FORMAT.tryParse(freeAt)
        return ts == 0L || ts > System.currentTimeMillis()
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "pause", "frozen" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // "/uploads/..." objects are not publicly accessible on the S3 CDN (403),
    // but the same objects are served from the CDN root without that prefix.
    private fun resolveImageUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val url = when {
            path.startsWith("http") -> path
            path.startsWith("/") -> cdnUrl + path
            else -> "$cdnUrl/$path"
        }
        return when {
            url.startsWith("$cdnUrl/uploads") -> url.replace("$cdnUrl/uploads", cdnUrl)
            url.startsWith("$baseUrl/uploads") -> url.replace("$baseUrl/uploads", cdnUrl)
            else -> url
        }
    }

    companion object {
        private const val PAGE_LIMIT = 30
        private const val CHAPTERS_PER_PAGE = 200
        private const val PREF_SHOW_ADULT = "pref_show_adult"
        private const val PREF_HIDE_PAID = "pref_hide_paid"

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
