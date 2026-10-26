package eu.kanade.tachiyomi.extension.en.philiascans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Source
abstract class PhiliaScans :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl: String
        get() = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    private val emptyBody = ByteArray(0).toRequestBody(null)

    private val tokenHeaders
        get() = headersBuilder()
            .set("Accept", "application/json")
            .build()

    private val tokenMutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedExpiresAtMs: Long = 0L

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 2 }, OrderFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(), OrderFilter()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "20")
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query)
                addFilter("orderby", filters.firstInstanceOrNull<SortFilter>())
                addFilter("order", filters.firstInstanceOrNull<OrderFilter>())
                addFilter("types", filters.firstInstanceOrNull<TypeFilter>())
                addFilter("statuses", filters.firstInstanceOrNull<StatusFilter>())
                addFilter("genres", filters.firstInstanceOrNull<GenreFilter>())
            }
            .build()

        val result = client.get(url).parseAs<SeriesResponse>()
        return MangasPage(result.items.map { it.toSManga(baseUrl) }, result.hasNextPage())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (!fetchDetails) return@async manga
            client.get("$apiUrl/manga/${manga.url}").parseAs<DetailsResponse>().toSManga(baseUrl).apply {
                url = manga.url
            }
        }
        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            getChapterList(manga.url)
        }
        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private suspend fun getChapterList(slug: String): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return client.get("$apiUrl/manga/$slug/chapters").parseAs<ChapterResponse>().items
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url}"

    private suspend fun fetchPageKeys(chapterId: Int, token: String): Pair<String, PageKeys> {
        val response = client.get(
            "$apiUrl/chapters/$chapterId/page-keys",
            readerHeaders(token),
            ensureSuccess = false,
        )
        if (response.code == 404) {
            response.close()
            val refreshed = getReaderAccessToken(forceRefresh = true)
            return refreshed to client.get(
                "$apiUrl/chapters/$chapterId/page-keys",
                readerHeaders(refreshed),
            ).parseAs()
        }
        if (!response.isSuccessful) throwReaderError(response)
        return token to response.parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = chapter.url.split("/")
        val mangaSlug = parts.first()
        val chapterSlug = parts.last()
        val result = client.get("$apiUrl/manga/$mangaSlug/chapters/$chapterSlug").parseAs<ViewerResponse>()
        if (!result.hasAccess) throw Exception("Log in via WebView and purchase this chapter to read.")

        val (token, pageKeyResponse) = fetchPageKeys(result.chapter.id, getReaderAccessToken())
        val headers = readerHeaders(token)

        val isScrambled = if (result.chapter.scrambled) "1" else "0"

        val (payloadA, payloadB) = if (pageKeyResponse.sessionDefault) {
            val openResponse = client.post(
                "$apiUrl/chapters/${result.chapter.id}/open",
                headers,
                emptyBody,
            ).parseAs<OpenResponse>()
            val drmCall = client.get(
                "$apiUrl/chapters/${result.chapter.id}/get-drm".toHttpUrl().newBuilder()
                    .addQueryParameter("session", openResponse.sessionId)
                    .build(),
                headers,
                ensureSuccess = false,
            )
            val drmResponse = if (drmCall.isSuccessful) {
                drmCall.parseAs<DrmResponse>()
            } else {
                drmCall.close()
                null
            }
            openResponse.payloadA to drmResponse?.payloadB
        } else {
            null to null
        }

        val base = baseUrl.toHttpUrl()
        return result.chapter.pages.sortedBy { it.position }.mapIndexed { i, page ->
            val imageUrl = base.resolve(page.url)!!
            Page(i, imageUrl = "$imageUrl#$isScrambled;${page.mime};${pageKeyResponse.chapterKeyB64};${pageKeyResponse.gridSize};$payloadA;$payloadB;$i")
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres").parseAs<List<Genre>>()
        .map { it.toPair() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>()
        return FilterList(
            buildList {
                add(Filter.Header("Note: Search and active filters are applied together"))
                add(SortFilter())
                add(OrderFilter())
                add(TypeFilter())
                add(StatusFilter())
                if (!genres.isNullOrEmpty()) add(GenreFilter(genres))
            },
        )
    }

    private suspend fun getReaderAccessToken(forceRefresh: Boolean = false): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedToken?.let { token ->
                if (cachedExpiresAtMs - now > TOKEN_SKEW_MS) return token
            }
        }

        val response = client.post("$apiUrl/reader/access-token", tokenHeaders, emptyBody, ensureSuccess = false)
        if (!response.isSuccessful) throwReaderError(response)

        val result = response.parseAs<TokenResponse>()
        cachedToken = result.token
        cachedExpiresAtMs = result.expiresAt * 1000L
        return result.token
    }

    private fun throwReaderError(response: Response): Nothing {
        response.close()
        when (response.code) {
            429 -> throw Exception("Rate limited by Philia Scans. Wait a moment and try again.")
            401 -> throw Exception("Log in via WebView to renew access.")
            else -> throw HttpException(response.code)
        }
    }

    private fun readerHeaders(token: String) = tokenHeaders.newBuilder().add("X-Reader-Access-Token", token).build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            summary = "Hide chapters that require coins to read."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val TOKEN_SKEW_MS = 60_000L
    }
}
