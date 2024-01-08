package eu.kanade.tachiyomi.multisrc.heancms

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class HeanCms(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    protected val apiUrl: String = baseUrl.replace("://", "://api."),
) : ConfigurableSource, HttpSource() {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = intl.prefShowPaidChapterTitle
            summaryOn = intl.prefShowPaidChapterSummaryOn
            summaryOff = intl.prefShowPaidChapterSummaryOff
            setDefaultValue(SHOW_PAID_CHAPTERS_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(SHOW_PAID_CHAPTERS_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)
    }

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    protected open val slugStrategy = SlugStrategy.NONE

    protected open val useNewQueryEndpoint = false

    private var seriesSlugMap: Map<String, HeanCmsTitle>? = null

    /**
     * Custom Json instance to make usage of `encodeDefaults`,
     * which is not enabled on the injected instance of the app.
     */
    protected val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    protected val intl by lazy { HeanCmsIntl(lang) }

    protected open val coverPath: String = "cover/"

    protected open val mangaSubDirectory: String = "series"

    protected open val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.US)

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        if (useNewQueryEndpoint) {
            return newEndpointPopularMangaRequest(page)
        }

        val payloadObj = HeanCmsQuerySearchPayloadDto(
            page = page,
            order = "desc",
            orderBy = "total_views",
            status = "All",
            type = "Comic",
        )

        val payload = json.encodeToString(payloadObj).toRequestBody(JSON_MEDIA_TYPE)

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("Content-Type", payload.contentType().toString())
            .build()

        return POST("$apiUrl/series/querysearch", apiHeaders, payload)
    }

    protected fun newEndpointPopularMangaRequest(page: Int): Request {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", "")
            .addQueryParameter("series_status", "All")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "total_views")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", "[]")

        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.body.string()

        if (json.startsWith("{")) {
            val result = json.parseAs<HeanCmsQuerySearchDto>()
            val mangaList = result.data.map {
                if (slugStrategy != SlugStrategy.NONE) {
                    preferences.slugMap = preferences.slugMap.toMutableMap()
                        .also { map -> map[it.slug.toPermSlugIfNeeded()] = it.slug }
                }
                it.toSManga(apiUrl, coverPath, mangaSubDirectory, slugStrategy)
            }

            fetchAllTitles()

            return MangasPage(mangaList, result.meta?.hasNextPage ?: false)
        }

        val mangaList = json.parseAs<List<HeanCmsSeriesDto>>()
            .map {
                if (slugStrategy != SlugStrategy.NONE) {
                    preferences.slugMap = preferences.slugMap.toMutableMap()
                        .also { map -> map[it.slug.toPermSlugIfNeeded()] = it.slug }
                }
                it.toSManga(apiUrl, coverPath, mangaSubDirectory, slugStrategy)
            }

        fetchAllTitles()

        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        if (useNewQueryEndpoint) {
            return newEndpointLatestUpdatesRequest(page)
        }

        val payloadObj = HeanCmsQuerySearchPayloadDto(
            page = page,
            order = "desc",
            orderBy = "latest",
            status = "All",
            type = "Comic",
        )

        val payload = json.encodeToString(payloadObj).toRequestBody(JSON_MEDIA_TYPE)

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("Content-Type", payload.contentType().toString())
            .build()

        return POST("$apiUrl/series/querysearch", apiHeaders, payload)
    }

    protected fun newEndpointLatestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", "")
            .addQueryParameter("series_status", "All")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "latest")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", "[]")

        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val slug = query.substringAfter(SEARCH_PREFIX)
        val manga = SManga.create().apply {
            url = if (slugStrategy != SlugStrategy.NONE) {
                val mangaId = getIdBySlug(slug)
                "/$mangaSubDirectory/${slug.toPermSlugIfNeeded()}#$mangaId"
            } else {
                "/$mangaSubDirectory/$slug"
            }
        }

        return fetchMangaDetails(manga).map { MangasPage(listOf(it), false) }
    }

    private fun getIdBySlug(slug: String): Int {
        val result = runCatching {
            val response = client.newCall(GET("$apiUrl/series/$slug", headers)).execute()
            val json = response.body.string()

            val seriesDetail = json.parseAs<HeanCmsSeriesDto>()

            preferences.slugMap = preferences.slugMap.toMutableMap()
                .also { it[seriesDetail.slug.toPermSlugIfNeeded()] = seriesDetail.slug }

            seriesDetail.id
        }
        return result.getOrNull() ?: throw Exception(intl.idNotFoundError + slug)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (useNewQueryEndpoint) {
            return newEndpointSearchMangaRequest(page, query, filters)
        }

        if (query.isNotBlank()) {
            val searchPayloadObj = HeanCmsSearchPayloadDto(query)
            val searchPayload = json.encodeToString(searchPayloadObj)
                .toRequestBody(JSON_MEDIA_TYPE)

            val apiHeaders = headersBuilder()
                .add("Accept", ACCEPT_JSON)
                .add("Content-Type", searchPayload.contentType().toString())
                .build()

            return POST("$apiUrl/series/search", apiHeaders, searchPayload)
        }

        val sortByFilter = filters.firstInstanceOrNull<SortByFilter>()

        val payloadObj = HeanCmsQuerySearchPayloadDto(
            page = page,
            order = if (sortByFilter?.state?.ascending == true) "asc" else "desc",
            orderBy = sortByFilter?.selected ?: "total_views",
            status = filters.firstInstanceOrNull<StatusFilter>()?.selected?.value ?: "Ongoing",
            type = "Comic",
            tagIds = filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter(Genre::state)
                ?.map(Genre::id)
                .orEmpty(),
        )

        val payload = json.encodeToString(payloadObj).toRequestBody(JSON_MEDIA_TYPE)

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("Content-Type", payload.contentType().toString())
            .build()

        return POST("$apiUrl/series/querysearch", apiHeaders, payload)
    }

    protected fun newEndpointSearchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortByFilter = filters.firstInstanceOrNull<SortByFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

        val tagIds = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",", prefix = "[", postfix = "]")

        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", query)
            .addQueryParameter("series_status", statusFilter?.selected?.value ?: "All")
            .addQueryParameter("order", if (sortByFilter?.state?.ascending == true) "asc" else "desc")
            .addQueryParameter("orderBy", sortByFilter?.selected ?: "total_views")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", tagIds)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string()

        if (response.request.url.pathSegments.last() == "search") {
            fetchAllTitles()

            val result = json.parseAs<List<HeanCmsSearchDto>>()
            val mangaList = result
                .filter { it.type == "Comic" }
                .map {
                    it.slug = it.slug.toPermSlugIfNeeded()
                    it.toSManga(apiUrl, coverPath, mangaSubDirectory, seriesSlugMap.orEmpty(), slugStrategy)
                }

            return MangasPage(mangaList, false)
        }

        if (json.startsWith("{")) {
            val result = json.parseAs<HeanCmsQuerySearchDto>()
            val mangaList = result.data.map {
                if (slugStrategy != SlugStrategy.NONE) {
                    preferences.slugMap = preferences.slugMap.toMutableMap()
                        .also { map -> map[it.slug.toPermSlugIfNeeded()] = it.slug }
                }
                it.toSManga(apiUrl, coverPath, mangaSubDirectory, slugStrategy)
            }

            fetchAllTitles()

            return MangasPage(mangaList, result.meta?.hasNextPage ?: false)
        }

        val mangaList = json.parseAs<List<HeanCmsSeriesDto>>()
            .map {
                if (slugStrategy != SlugStrategy.NONE) {
                    preferences.slugMap = preferences.slugMap.toMutableMap()
                        .also { map -> map[it.slug.toPermSlugIfNeeded()] = it.slug }
                }
                it.toSManga(apiUrl, coverPath, mangaSubDirectory, slugStrategy)
            }

        fetchAllTitles()

        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun getMangaUrl(manga: SManga): String {
        val seriesSlug = manga.url
            .substringAfterLast("/")
            .substringBefore("#")
            .toPermSlugIfNeeded()

        val currentSlug = if (slugStrategy != SlugStrategy.NONE) {
            preferences.slugMap[seriesSlug] ?: seriesSlug
        } else {
            seriesSlug
        }

        return "$baseUrl/$mangaSubDirectory/$currentSlug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (slugStrategy != SlugStrategy.NONE && (manga.url.contains(TIMESTAMP_REGEX))) {
            throw Exception(intl.urlChangedError(name))
        }

        if (slugStrategy == SlugStrategy.ID && !manga.url.contains("#")) {
            throw Exception(intl.urlChangedError(name))
        }

        val seriesSlug = manga.url
            .substringAfterLast("/")
            .substringBefore("#")
            .toPermSlugIfNeeded()

        val seriesId = manga.url.substringAfterLast("#")

        fetchAllTitles()

        val seriesDetails = seriesSlugMap?.get(seriesSlug)
        val currentSlug = seriesDetails?.slug ?: seriesSlug
        val currentStatus = seriesDetails?.status ?: manga.status

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        return if (slugStrategy == SlugStrategy.ID) {
            GET("$apiUrl/series/id/$seriesId", apiHeaders)
        } else {
            GET("$apiUrl/series/$currentSlug#$currentStatus", apiHeaders)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaStatus = response.request.url.fragment?.toIntOrNull() ?: SManga.UNKNOWN

        val result = runCatching { response.parseAs<HeanCmsSeriesDto>() }

        val seriesResult = result.getOrNull() ?: throw Exception(intl.urlChangedError(name))

        if (slugStrategy != SlugStrategy.NONE) {
            preferences.slugMap = preferences.slugMap.toMutableMap()
                .also { it[seriesResult.slug.toPermSlugIfNeeded()] = seriesResult.slug }
        }

        val seriesDetails = seriesResult.toSManga(apiUrl, coverPath, mangaSubDirectory, slugStrategy)

        return seriesDetails.apply {
            status = status.takeUnless { it == SManga.UNKNOWN }
                ?: mangaStatus
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<HeanCmsSeriesDto>()

        if (slugStrategy == SlugStrategy.ID) {
            preferences.slugMap = preferences.slugMap.toMutableMap()
                .also { it[result.slug.toPermSlugIfNeeded()] = result.slug }
        }

        val currentTimestamp = System.currentTimeMillis()

        val showPaidChapters = preferences.showPaidChapters

        if (useNewQueryEndpoint) {
            return result.seasons.orEmpty()
                .flatMap { it.chapters.orEmpty() }
                .filter { it.price == 0 || showPaidChapters }
                .map { it.toSChapter(result.slug, mangaSubDirectory, dateFormat, slugStrategy) }
                .filter { it.date_upload <= currentTimestamp }
        }

        return result.chapters.orEmpty()
            .filter { it.price == 0 || showPaidChapters }
            .map { it.toSChapter(result.slug, mangaSubDirectory, dateFormat, slugStrategy) }
            .filter { it.date_upload <= currentTimestamp }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (slugStrategy == SlugStrategy.NONE) return baseUrl + chapter.url

        val seriesSlug = chapter.url
            .substringAfter("/$mangaSubDirectory/")
            .substringBefore("/")
            .toPermSlugIfNeeded()

        val currentSlug = preferences.slugMap[seriesSlug] ?: seriesSlug
        val chapterUrl = chapter.url.replaceFirst(seriesSlug, currentSlug)

        return baseUrl + chapterUrl
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (useNewQueryEndpoint) {
            if (slugStrategy != SlugStrategy.NONE) {
                val seriesPermSlug = chapter.url.substringAfter("/$mangaSubDirectory/").substringBefore("/")
                val seriesSlug = preferences.slugMap[seriesPermSlug] ?: seriesPermSlug
                val chapterUrl = chapter.url.replaceFirst(seriesPermSlug, seriesSlug)
                return GET(baseUrl + chapterUrl, headers)
            }
            return GET(baseUrl + chapter.url, headers)
        }

        val chapterId = chapter.url.substringAfterLast("#").substringBefore("-paid")

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .build()

        return GET("$apiUrl/series/chapter/$chapterId", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (useNewQueryEndpoint) {
            val paidChapter = response.request.url.fragment?.contains("-paid")

            val document = response.asJsoup()

            val images = document.selectFirst("div.min-h-screen > div.container > p.items-center")

            if (images == null && paidChapter == true) {
                throw IOException(intl.paidChapterError)
            }

            return images?.select("img").orEmpty().mapIndexed { i, img ->
                val imageUrl = if (img.hasClass("lazy")) img.absUrl("data-src") else img.absUrl("src")
                Page(i, "", imageUrl)
            }
        }

        val images = response.parseAs<HeanCmsReaderDto>().content?.images.orEmpty()
        val paidChapter = response.request.url.fragment?.contains("-paid")

        if (images.isEmpty() && paidChapter == true) {
            throw IOException(intl.paidChapterError)
        }

        return images.filterNot { imageUrl ->
            // Their image server returns HTTP 403 for hidden files that starts
            // with a dot in the file name. To avoid download errors, these are removed.
            imageUrl
                .removeSuffix("/")
                .substringAfterLast("/")
                .startsWith(".")
        }
            .mapIndexed { i, url ->
                Page(i, imageUrl = if (url.startsWith("http")) url else "$apiUrl/$url")
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    protected open fun fetchAllTitles() {
        if (!seriesSlugMap.isNullOrEmpty() || slugStrategy != SlugStrategy.FETCH_ALL) {
            return
        }

        val result = runCatching {
            var hasNextPage = true
            var page = 1
            val tempMap = mutableMapOf<String, HeanCmsTitle>()

            while (hasNextPage) {
                val response = client.newCall(allTitlesRequest(page)).execute()
                val json = response.body.string()

                if (json.startsWith("{")) {
                    val result = json.parseAs<HeanCmsQuerySearchDto>()
                    tempMap.putAll(parseAllTitles(result.data))
                    hasNextPage = result.meta?.hasNextPage ?: false
                    page++
                } else {
                    val result = json.parseAs<List<HeanCmsSeriesDto>>()
                    tempMap.putAll(parseAllTitles(result))
                    hasNextPage = false
                }
            }

            tempMap.toMap()
        }

        seriesSlugMap = result.getOrNull()
        preferences.slugMap = preferences.slugMap.toMutableMap()
            .also { it.putAll(seriesSlugMap.orEmpty().mapValues { (_, v) -> v.slug }) }
    }

    protected open fun allTitlesRequest(page: Int): Request {
        if (useNewQueryEndpoint) {
            val url = "$apiUrl/query".toHttpUrl().newBuilder()
                .addQueryParameter("series_type", "Comic")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("perPage", PER_PAGE_MANGA_TITLES.toString())

            return GET(url.build(), headers)
        }

        val payloadObj = HeanCmsQuerySearchPayloadDto(
            page = page,
            order = "desc",
            orderBy = "total_views",
            type = "Comic",
        )

        val payload = json.encodeToString(payloadObj).toRequestBody(JSON_MEDIA_TYPE)

        val apiHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("Content-Type", payload.contentType().toString())
            .build()

        return POST("$apiUrl/series/querysearch", apiHeaders, payload)
    }

    protected open fun parseAllTitles(result: List<HeanCmsSeriesDto>): Map<String, HeanCmsTitle> {
        return result
            .filter { it.type == "Comic" }
            .associateBy(
                keySelector = { it.slug.replace(TIMESTAMP_REGEX, "") },
                valueTransform = {
                    HeanCmsTitle(
                        slug = it.slug,
                        thumbnailFileName = it.thumbnail,
                        status = it.status?.toStatus() ?: SManga.UNKNOWN,
                    )
                },
            )
    }

    /**
     * Used to store the current slugs for sources that change it periodically and for the
     * search that doesn't return the thumbnail URLs.
     */
    data class HeanCmsTitle(val slug: String, val thumbnailFileName: String, val status: Int)

    /**
     * Used to specify the strategy to use when fetching the slug for a manga.
     * This is needed because some sources change the slug periodically.
     * [NONE]: Use series_slug without changes.
     * [ID]: Use series_id to fetch the slug from the API.
     * IMPORTANT: [ID] is only available in the new query endpoint.
     * [FETCH_ALL]: Convert the slug to a permanent slug by removing the timestamp.
     * At extension start, all the slugs are fetched and stored in a map.
     */
    enum class SlugStrategy {
        NONE, ID, FETCH_ALL
    }

    private fun String.toPermSlugIfNeeded(): String {
        return if (slugStrategy != SlugStrategy.NONE) {
            this.replace(TIMESTAMP_REGEX, "")
        } else {
            this
        }
    }

    protected open fun getStatusList(): List<Status> = listOf(
        Status(intl.statusAll, "All"),
        Status(intl.statusOngoing, "Ongoing"),
        Status(intl.statusOnHiatus, "Hiatus"),
        Status(intl.statusDropped, "Dropped"),
    )

    protected open fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty(intl.sortByTitle, "title"),
        SortProperty(intl.sortByViews, "total_views"),
        SortProperty(intl.sortByLatest, "latest"),
        SortProperty(intl.sortByCreatedAt, "created_at"),
    )

    protected open fun getGenreList(): List<Genre> = emptyList()

    override fun getFilterList(): FilterList {
        val genres = getGenreList()

        val filters = listOfNotNull(
            Filter.Header(intl.filterWarning),
            StatusFilter(intl.statusFilterTitle, getStatusList()),
            SortByFilter(intl.sortByFilterTitle, getSortProperties()),
            GenreFilter(intl.genreFilterTitle, genres).takeIf { genres.isNotEmpty() },
        )

        return FilterList(filters)
    }

    protected inline fun <reified T> Response.parseAs(): T = use {
        it.body.string().parseAs()
    }

    protected inline fun <reified T> String.parseAs(): T = json.decodeFromString(this)

    protected inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    protected var SharedPreferences.slugMap: MutableMap<String, String>
        get() {
            val jsonMap = getString(PREF_URL_MAP_SLUG, "{}")!!
            val slugMap = runCatching { json.decodeFromString<Map<String, String>>(jsonMap) }
            return slugMap.getOrNull()?.toMutableMap() ?: mutableMapOf()
        }
        set(newSlugMap) {
            edit()
                .putString(PREF_URL_MAP_SLUG, json.encodeToString(newSlugMap))
                .apply()
        }

    private val SharedPreferences.showPaidChapters: Boolean
        get() = getBoolean(SHOW_PAID_CHAPTERS_PREF, SHOW_PAID_CHAPTERS_DEFAULT)

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        val TIMESTAMP_REGEX = """-\d{13}$""".toRegex()

        private const val PER_PAGE_MANGA_TITLES = 10000

        const val SEARCH_PREFIX = "slug:"

        private const val PREF_URL_MAP_SLUG = "pref_url_map"

        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private const val SHOW_PAID_CHAPTERS_DEFAULT = false
    }
}
