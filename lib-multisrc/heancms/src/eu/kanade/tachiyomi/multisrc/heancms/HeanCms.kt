package eu.kanade.tachiyomi.multisrc.heancms

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
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
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

abstract class HeanCms :
    KeiSource(),
    ConfigurableSource {

    protected open val apiUrl: String
        get() = baseUrl.replace("://", "://api.")

    protected val preferences: SharedPreferences by getPreferencesLazy()

    /**
     * Whether the source supports login and gated (paid) chapters behind a bearer token.
     */
    protected open val enableLogin = false

    protected open val coverPath: String = ""

    protected open val cdnUrl: String
        get() = apiUrl

    protected open val mangaSubDirectory: String = "series"

    protected open val latestSortBy = "desc"

    private suspend fun authHeaders(): Headers {
        if (!enableLogin || preferences.user.isEmpty() || preferences.password.isEmpty()) {
            return headers
        }

        val tokenData = preferences.tokenData
        val token = if (tokenData.isExpired()) {
            getToken()
        } else {
            tokenData.token
        }

        return if (token != null) {
            headers.newBuilder().add("Authorization", "Bearer $token").build()
        } else {
            headers
        }
    }

    private suspend fun getToken(): String? {
        val body = FormBody.Builder()
            .add("email", preferences.user)
            .add("password", preferences.password)
            .build()

        val response = client.post("$apiUrl/login", headers, body, ensureSuccess = false)

        if (!response.isSuccessful) {
            val result = response.parseAs<HeanCmsErrorsDto>()
            val message = result.errors?.firstOrNull()?.message ?: "Unknown error occurred while logging in"

            throw Exception(message)
        }

        val result = response.parseAs<HeanCmsTokenPayloadDto>()

        preferences.tokenData = result

        return result.token
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = queryUrlBuilder(page, "", status = "All", order = "desc", orderBy = "total_views", tagIds = "[]")
        return parseSearchMangaList(client.get(url))
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = queryUrlBuilder(page, "", status = "All", order = latestSortBy, orderBy = "latest", tagIds = "[]")
        return parseSearchMangaList(client.get(url))
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortByFilter = filters.firstInstanceOrNull<SortByFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

        val tagIds = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",", prefix = "[", postfix = "]")

        val url = queryUrlBuilder(
            page = page,
            query = query,
            status = statusFilter?.selected?.value ?: "All",
            order = if (sortByFilter?.state?.ascending == true) "asc" else "desc",
            orderBy = sortByFilter?.selected ?: "total_views",
            tagIds = tagIds,
        )

        return parseSearchMangaList(client.get(url))
    }

    private fun queryUrlBuilder(
        page: Int,
        query: String,
        status: String,
        order: String,
        orderBy: String,
        tagIds: String,
    ): HttpUrl = "$apiUrl/query".toHttpUrl().newBuilder()
        .addQueryParameter("query_string", query)
        .addQueryParameter("status", status)
        .addQueryParameter("order", order)
        .addQueryParameter("orderBy", orderBy)
        .addQueryParameter("series_type", "Comic")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("perPage", "12")
        .addQueryParameter("tags_ids", tagIds)
        .addQueryParameter("adult", "true")
        .build()

    private fun parseSearchMangaList(response: Response): MangasPage {
        val result = response.parseAs<HeanCmsQuerySearchDto>()
        val mangaList = result.data.map {
            it.toSManga(cdnUrl, coverPath, mangaSubDirectory)
        }

        return MangasPage(mangaList, result.meta?.hasNextPage() ?: false)
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/tags").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<HeanCmsGenreDto>>()?.map { Genre(it.name, it.id) }

        val filters = mutableListOf<Filter<*>>(
            StatusFilter("Status", getStatusList()),
            SortByFilter("Sort By", getSortProperties()),
        )

        if (!genres.isNullOrEmpty()) {
            filters += GenreFilter("Genres", genres)
        }

        return FilterList(filters)
    }

    protected open fun getStatusList(): List<Status> = listOf(
        Status("All", "All"),
        Status("Ongoing", "Ongoing"),
        Status("On hiatus", "Hiatus"),
        Status("Dropped", "Dropped"),
        Status("Completed", "Completed"),
        Status("Canceled", "Canceled"),
    )

    protected open fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty("Title", "title"),
        SortProperty("Views", "total_views"),
        SortProperty("Latest", "latest"),
        SortProperty("Created at", "created_at"),
    )

    // ============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val genreNames = manga.genre?.split(", ")
            ?.filter(String::isNotBlank)?.toSet().orEmpty()

        val filters = getFilterList()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()?.apply {
            state.forEach { it.state = it.name in genreNames }
        }

        val result = if (genreFilter?.state?.any { it.state } == true) {
            getSearchMangaList(1, "", filters)
        } else {
            val author = manga.author?.trim().orEmpty()
            if (author.isEmpty()) return emptyList()
            getSearchMangaList(1, author, FilterList())
        }

        return result.mangas
    }

    // ============================== Details ==============================

    private suspend fun fetchSeries(slug: String): HeanCmsSeriesDto {
        val apiHeaders = headers.newBuilder().add("Accept", ACCEPT_JSON).build()
        return client.get("$apiUrl/series/$slug", apiHeaders).parseAs()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            throw Exception("Unsupported URL")
        }

        val slug = url.pathSegments.getOrNull(1)
            ?: throw Exception("Unsupported URL")

        return fetchSeries(slug)
            .toSManga(cdnUrl, coverPath, mangaSubDirectory)
            .apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String {
        val seriesSlug = manga.url.substringAfterLast("/").substringBefore("#")
        return "$baseUrl/$mangaSubDirectory/$seriesSlug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        if (!manga.url.contains("#")) {
            throw Exception("The URL of the series has changed. Migrate from $name to $name to update the URL")
        }

        val slug = manga.url.substringAfterLast("/").substringBefore("#")
        val seriesId = manga.url.substringAfterLast("#")

        val mangaDeferred = async {
            if (fetchDetails) fetchSeries(slug).toSManga(cdnUrl, coverPath, mangaSubDirectory) else manga
        }
        val chaptersDeferred = async {
            if (fetchChapters) fetchChapters(seriesId, slug) else chapters
        }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchChapters(seriesId: String, seriesSlug: String): List<SChapter> {
        val showPaidChapters = preferences.showPaidChapters
        val currentTimestamp = System.currentTimeMillis()
        val apiHeaders = headers.newBuilder().add("Accept", ACCEPT_JSON).build()

        val chapterList = mutableListOf<HeanCmsChapterDto>()
        var page = 1
        var hasNextPage: Boolean
        do {
            val url = "$apiUrl/chapter/query".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("perPage", PER_PAGE_CHAPTERS.toString())
                .addQueryParameter("series_id", seriesId)
                .build()

            val result = client.get(url, apiHeaders).parseAs<HeanCmsChapterPayloadDto>()
            chapterList.addAll(result.data)
            hasNextPage = result.meta.hasNextPage()
            page++
        } while (hasNextPage)

        return chapterList
            .filter { it.price == 0 || showPaidChapters }
            .map { it.toSChapter(seriesSlug, mangaSubDirectory) }
            .filter { it.date_upload <= currentTimestamp }
    }

    // ============================== Pages ==============================

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBeforeLast("#")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl + chapter.url.replace("/$mangaSubDirectory/", "/chapter/")
        val result = client.get(url, authHeaders()).parseAs<HeanCmsPagePayloadDto>()

        if (result.isPaywalled() && result.chapter.chapterData == null) {
            throw Exception("Paid chapter unavailable.")
        }

        return result.chapter.chapterData?.images.orEmpty().mapIndexed { i, img ->
            Page(i, imageUrl = img.toAbsoluteUrl())
        }
    }

    protected open fun String.toAbsoluteUrl(): String = if (startsWith("https://") || startsWith("http://")) this else "$cdnUrl/$coverPath$this"

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return Request.Builder().url(page.imageUrl!!).headers(imageHeaders).build()
    }

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = "Display paid chapters"
            summaryOn = "Paid chapters will appear."
            summaryOff = "Only free chapters will be displayed."
            setDefaultValue(SHOW_PAID_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)

        if (enableLogin) {
            EditTextPreference(screen.context).apply {
                key = USER_PREF
                title = "Username/Email"
                summary = "Ignored if empty."
                setDefaultValue("")

                setOnPreferenceChangeListener { _, _ ->
                    preferences.tokenData = HeanCmsTokenPayloadDto()
                    true
                }
            }.also(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = PASSWORD_PREF
                title = "Password"
                summary = "Ignored if empty."
                setDefaultValue("")

                setOnPreferenceChangeListener { _, _ ->
                    preferences.tokenData = HeanCmsTokenPayloadDto()
                    true
                }
            }.also(screen::addPreference)
        }
    }

    private val SharedPreferences.showPaidChapters: Boolean
        get() = getBoolean(SHOW_PAID_CHAPTERS_PREF, SHOW_PAID_CHAPTERS_DEFAULT)

    private val SharedPreferences.user: String
        get() = getString(USER_PREF, "") ?: ""

    private val SharedPreferences.password: String
        get() = getString(PASSWORD_PREF, "") ?: ""

    private var SharedPreferences.tokenData: HeanCmsTokenPayloadDto
        get() {
            val jsonString = getString(TOKEN_PREF, "{}")!!
            return jsonString.parseAs()
        }
        set(data) {
            edit().putString(TOKEN_PREF, data.toJsonString()).apply()
        }

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private const val PER_PAGE_CHAPTERS = 1000

        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private const val SHOW_PAID_CHAPTERS_DEFAULT = false

        private const val USER_PREF = "pref_user"
        private const val PASSWORD_PREF = "pref_password"

        private const val TOKEN_PREF = "pref_token"
    }
}
