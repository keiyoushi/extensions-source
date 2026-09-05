package eu.kanade.tachiyomi.extension.en.philiascans

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
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class PhiliaScans :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val apiUrl: String
        get() = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 2 }, OrderFilter()))

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesResponse>()
        val mangas = result.items.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, result.hasNextPage())
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter(), OrderFilter()))

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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
        return GET(url.build(), headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        OrderFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga(baseUrl)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val slug = response.request.url.pathSegments[2]
        return response.parseAs<ChapterResponse>().items
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url}"

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("/")
        val mangaSlug = parts.first()
        val chapterSlug = parts.last()
        return GET("$apiUrl/manga/$mangaSlug/chapters/$chapterSlug", headers)
    }

    private val tokenHeaders = headersBuilder()
        .set("Accept", "application/json")
        .set("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6")
        .set("Sec-Fetch-Mode", "cors")
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedExpiresAtMs: Long = 0L

    private fun getReaderAccessToken(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedToken?.let { token ->
                if (cachedExpiresAtMs - now > TOKEN_SKEW_MS) return token
            }
        }

        val response = client.newCall(POST("$apiUrl/reader/access-token", tokenHeaders)).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            when (code) {
                429 -> throw Exception("Rate limited by Philia Scans. Wait a moment and try again.")
                401 -> throw Exception("Log in via WebView to renew access.")
                else -> throw Exception("Failed to get reader access token (HTTP $code).")
            }
        }

        val result = response.parseAs<TokenResponse>()
        cachedToken = result.token
        cachedExpiresAtMs = result.expiresAt * 1000L
        return result.token
    }

    private fun readerHeaders(token: String) = tokenHeaders.newBuilder().add("X-Reader-Access-Token", token).build()

    private fun fetchPageKeys(chapterId: Int, token: String): Pair<String, PageKeys> {
        val response = client.newCall(GET("$apiUrl/chapters/$chapterId/page-keys", readerHeaders(token))).execute()
        if (response.code == 404) {
            response.close()
            val refreshed = getReaderAccessToken(forceRefresh = true)
            return refreshed to client.newCall(GET("$apiUrl/chapters/$chapterId/page-keys", readerHeaders(refreshed)))
                .execute()
                .parseAs()
        }
        return token to response.parseAs()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val response = client.newCall(pageListRequest(chapter)).execute()
        val result = response.parseAs<ViewerResponse>()
        if (!result.hasAccess) throw Exception("Log in via Webview and purchased this chapter to read.")

        val (token, pageKeyResponse) = fetchPageKeys(result.chapter.id, getReaderAccessToken())
        val headers = readerHeaders(token)

        val isScrambled = if (result.chapter.scrambled) "1" else "0"

        val (payloadA, payloadB) = if (pageKeyResponse.sessionDefault) {
            val openResponse = client.newCall(POST("$apiUrl/chapters/${result.chapter.id}/open", headers)).execute().parseAs<OpenResponse>()
            val drmCall = client.newCall(GET("$apiUrl/chapters/${result.chapter.id}/get-drm?session=${openResponse.sessionId}", headers)).execute()
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

        result.chapter.pages.sortedBy { it.position }.mapIndexed { i, page ->
            val imageUrl = if (page.url.startsWith("http")) page.url else "$baseUrl/${page.url}"
            Page(i, imageUrl = "$imageUrl#$isScrambled;${page.mime};${pageKeyResponse.chapterKeyB64};${pageKeyResponse.gridSize};$payloadA;$payloadB;$i")
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities =============================

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
