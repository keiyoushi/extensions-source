package eu.kanade.tachiyomi.multisrc.heancms

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

abstract class HeanCms(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    protected val apiUrl: String = baseUrl.replace("://", "://api."),
) : ConfigurableSource, HttpSource() {

    protected val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    protected open val useNewQueryEndpoint = false

    protected open val useNewChapterEndpoint = false

    protected open val enableLogin = false

    /**
     * Custom Json instance to make usage of `encodeDefaults`,
     * which is not enabled on the injected instance of the app.
     */
    protected val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR", "es"),
        classLoader = this::class.java.classLoader!!,
    )

    protected open val coverPath: String = ""

    protected open val cdnUrl = apiUrl

    protected open val mangaSubDirectory: String = "series"

    protected open val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.US)

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private fun authHeaders(): Headers {
        val builder = headersBuilder()
        if (enableLogin && preferences.user.isNotEmpty() && preferences.password.isNotEmpty()) {
            val tokenData = preferences.tokenData
            val token = if (tokenData.isExpired(tokenExpiredAtDateFormat)) {
                getToken()
            } else {
                tokenData.token
            }
            if (token != null) {
                builder.add("Authorization", "Bearer $token")
            }
        }
        return builder.build()
    }

    private fun getToken(): String? {
        val body = FormBody.Builder()
            .add("email", preferences.user)
            .add("password", preferences.password)
            .build()

        val response = client.newCall(POST("$apiUrl/login", headers, body)).execute()

        if (!response.isSuccessful) {
            val result = response.parseAs<HeanCmsErrorsDto>()
            val message = result.errors?.firstOrNull()?.message ?: intl["login_failed_unknown_error"]

            throw Exception(message)
        }

        val result = response.parseAs<HeanCmsTokenPayloadDto>()

        preferences.tokenData = result

        return result.token
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", "")
            .addQueryParameter(if (useNewQueryEndpoint) "status" else "series_status", "All")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "total_views")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", "[]")
            .addQueryParameter("adult", "true")

        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", "")
            .addQueryParameter(if (useNewQueryEndpoint) "status" else "series_status", "All")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "latest")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", "[]")
            .addQueryParameter("adult", "true")

        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val slug = query.substringAfter(SEARCH_PREFIX)
        val manga = SManga.create().apply {
            val mangaId = getIdBySlug(slug)
            url = "/$mangaSubDirectory/$slug#$mangaId"
        }

        return fetchMangaDetails(manga).map { MangasPage(listOf(it), false) }
    }

    private fun getIdBySlug(slug: String): Int {
        val result = runCatching {
            val response = client.newCall(GET("$apiUrl/series/$slug", headers)).execute()
            val json = response.body.string()

            val seriesDetail = json.parseAs<HeanCmsSeriesDto>()

            seriesDetail.id
        }
        return result.getOrNull() ?: throw Exception(intl.format("id_not_found_error", slug))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortByFilter = filters.firstInstanceOrNull<SortByFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

        val tagIds = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",", prefix = "[", postfix = "]")

        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", query)
            .addQueryParameter(if (useNewQueryEndpoint) "status" else "series_status", statusFilter?.selected?.value ?: "All")
            .addQueryParameter("order", if (sortByFilter?.state?.ascending == true) "asc" else "desc")
            .addQueryParameter("orderBy", sortByFilter?.selected ?: "total_views")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", tagIds)
            .addQueryParameter("adult", "true")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string()

        val result = json.parseAs<HeanCmsQuerySearchDto>()
        val mangaList = result.data.map {
            it.toSManga(apiUrl, coverPath, mangaSubDirectory)
        }

        return MangasPage(mangaList, result.meta?.hasNextPage() ?: false)
    }

    override fun getMangaUrl(manga: SManga): String {
        val seriesSlug = manga.url
            .substringAfterLast("/")
            .substringBefore("#")

        return "$baseUrl/$mangaSubDirectory/$seriesSlug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!manga.url.contains("#")) {
            throw Exception(intl.format("url_changed_error", name, name))
        }

        val seriesSlug = manga.url.substringAfterLast("/").substringBefore("#")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        return GET("$apiUrl/series/$seriesSlug", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaStatus = response.request.url.fragment?.toIntOrNull() ?: SManga.UNKNOWN

        val result = runCatching { response.parseAs<HeanCmsSeriesDto>() }

        val seriesResult = result.getOrNull()
            ?: throw Exception(intl.format("url_changed_error", name, name))

        val seriesDetails = seriesResult.toSManga(cdnUrl, coverPath, mangaSubDirectory)

        return seriesDetails.apply {
            status = status.takeUnless { it == SManga.UNKNOWN }
                ?: mangaStatus
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (useNewChapterEndpoint) {
            if (!manga.url.contains("#")) {
                throw Exception(intl.format("url_changed_error", name, name))
            }

            val seriesId = manga.url.substringAfterLast("#")
            val seriesSlug = manga.url.substringAfterLast("/").substringBefore("#")

            val url = "$apiUrl/chapter/query".toHttpUrl().newBuilder()
                .addQueryParameter("page", "1")
                .addQueryParameter("perPage", PER_PAGE_CHAPTERS.toString())
                .addQueryParameter("series_id", seriesId)
                .fragment(seriesSlug)

            return GET(url.build(), headers)
        }

        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val showPaidChapters = preferences.showPaidChapters

        if (useNewChapterEndpoint) {
            val apiHeaders = headersBuilder()
                .add("Accept", ACCEPT_JSON)
                .build()

            val seriesId = response.request.url.queryParameter("series_id")

            val seriesSlug = response.request.url.fragment!!

            var result = response.parseAs<HeanCmsChapterPayloadDto>()

            val currentTimestamp = System.currentTimeMillis()

            val chapterList = mutableListOf<HeanCmsChapterDto>()

            chapterList.addAll(result.data)

            var page = 2
            while (result.meta.hasNextPage()) {
                val url = "$apiUrl/chapter/query".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("perPage", PER_PAGE_CHAPTERS.toString())
                    .addQueryParameter("series_id", seriesId)
                    .build()

                val nextResponse = client.newCall(GET(url, apiHeaders)).execute()
                result = nextResponse.parseAs<HeanCmsChapterPayloadDto>()
                chapterList.addAll(result.data)
                page++
            }

            return chapterList
                .filter { it.price == 0 || showPaidChapters }
                .map { it.toSChapter(seriesSlug, mangaSubDirectory, dateFormat) }
                .filter { it.date_upload <= currentTimestamp }
        }

        val result = response.parseAs<HeanCmsSeriesDto>()

        val currentTimestamp = System.currentTimeMillis()

        return result.seasons.orEmpty()
            .flatMap { it.chapters.orEmpty() }
            .filter { it.price == 0 || showPaidChapters }
            .map { it.toSChapter(result.slug, mangaSubDirectory, dateFormat) }
            .filter { it.date_upload <= currentTimestamp }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBeforeLast("#")

    override fun pageListRequest(chapter: SChapter) =
        GET(apiUrl + chapter.url.replace("/$mangaSubDirectory/", "/chapter/"), authHeaders())

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<HeanCmsPagePayloadDto>()

        if (result.isPaywalled() && result.chapter.chapterData == null) {
            throw Exception(intl["paid_chapter_error"])
        }

        return if (useNewChapterEndpoint) {
            result.chapter.chapterData?.images.orEmpty().mapIndexed { i, img ->
                Page(i, imageUrl = img.toAbsoluteUrl())
            }
        } else {
            result.data.orEmpty().mapIndexed { i, img ->
                Page(i, imageUrl = img.toAbsoluteUrl())
            }
        }
    }

    protected open fun String.toAbsoluteUrl(): String {
        return if (startsWith("https://") || startsWith("http://")) this else "$cdnUrl/$coverPath$this"
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    protected open fun getStatusList(): List<Status> = listOf(
        Status(intl["status_all"], "All"),
        Status(intl["status_ongoing"], "Ongoing"),
        Status(intl["status_onhiatus"], "Hiatus"),
        Status(intl["status_dropped"], "Dropped"),
        Status(intl["status_completed"], "Completed"),
        Status(intl["status_canceled"], "Canceled"),
    )

    protected open fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty(intl["sort_by_title"], "title"),
        SortProperty(intl["sort_by_views"], "total_views"),
        SortProperty(intl["sort_by_latest"], "latest"),
        SortProperty(intl["sort_by_created_at"], "created_at"),
    )

    private var genresList: List<Genre> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiUrl/tags", headers)).execute()
                val genres = json.decodeFromString<List<HeanCmsGenreDto>>(response.body.string())

                genresList = genres.map { Genre(it.name, it.id) }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchFilters()

        val filters = mutableListOf<Filter<*>>(
            StatusFilter(intl["status_filter_title"], getStatusList()),
            SortByFilter(intl["sort_by_filter_title"], getSortProperties()),
        )

        if (filtersState == FiltersState.FETCHED) {
            filters += listOfNotNull(
                GenreFilter(intl["genre_filter_title"], genresList),
            )
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = intl["pref_show_paid_chapter_title"]
            summaryOn = intl["pref_show_paid_chapter_summary_on"]
            summaryOff = intl["pref_show_paid_chapter_summary_off"]
            setDefaultValue(SHOW_PAID_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)

        if (enableLogin) {
            EditTextPreference(screen.context).apply {
                key = USER_PREF
                title = intl["pref_username_title"]
                summary = intl["pref_credentials_summary"]
                setDefaultValue("")

                setOnPreferenceChangeListener { _, _ ->
                    preferences.tokenData = HeanCmsTokenPayloadDto()
                    true
                }
            }.also(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = PASSWORD_PREF
                title = intl["pref_password_title"]
                summary = intl["pref_credentials_summary"]
                setDefaultValue("")

                setOnPreferenceChangeListener { _, _ ->
                    preferences.tokenData = HeanCmsTokenPayloadDto()
                    true
                }
            }.also(screen::addPreference)
        }
    }

    protected inline fun <reified T> Response.parseAs(): T = use {
        it.body.string().parseAs()
    }

    protected inline fun <reified T> String.parseAs(): T = json.decodeFromString(this)

    protected inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    private val SharedPreferences.showPaidChapters: Boolean
        get() = getBoolean(SHOW_PAID_CHAPTERS_PREF, SHOW_PAID_CHAPTERS_DEFAULT)

    private val SharedPreferences.user: String
        get() = getString(USER_PREF, "") ?: ""

    private val SharedPreferences.password: String
        get() = getString(PASSWORD_PREF, "") ?: ""

    private var SharedPreferences.tokenData: HeanCmsTokenPayloadDto
        get() {
            val jsonString = getString(TOKEN_PREF, "{}")!!
            return json.decodeFromString(jsonString)
        }
        set(data) {
            edit().putString(TOKEN_PREF, json.encodeToString(data)).apply()
        }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private const val PER_PAGE_CHAPTERS = 1000

        const val SEARCH_PREFIX = "slug:"

        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private const val SHOW_PAID_CHAPTERS_DEFAULT = false

        private const val USER_PREF = "pref_user"
        private const val PASSWORD_PREF = "pref_password"

        private const val TOKEN_PREF = "pref_token"

        private val tokenExpiredAtDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }
}
