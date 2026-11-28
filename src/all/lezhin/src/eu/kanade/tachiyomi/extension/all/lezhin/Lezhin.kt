package eu.kanade.tachiyomi.extension.all.lezhin

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLongOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Source
abstract class Lezhin :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "de", "es", "fr", "th"),
        classLoader = this::class.java.classLoader!!,
    )

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegments("api/balcony-api-v2").build()

    private val balconyId
        get() = when (lang) {
            "es" -> "LEZHIN_ES"
            "fr" -> "LEZHINFR_COM"
            "de" -> "LEZHINDE_COM"
            else -> "LEZHINTH_COM"
        }

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("x-balcony-id", balconyId)
            .set("x-platform", "WEB")
            .build()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrlHost }
        .addInterceptor(::pageInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "POPULAR")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "LATEST")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) return search(page, query)
        return browse(
            page,
            filters.firstInstance<SortFilter>().value,
            filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty(),
            filters.firstInstance<FreeFilter>().state,
        )
    }

    // The site's genre tab lists every series id in order, then loads the cards of one page
    private suspend fun browse(page: Int, sort: String, genres: List<String> = emptyList(), freeOnly: Boolean = false): MangasPage {
        val idsUrl = apiUrl.newBuilder()
            .addPathSegments("contents/tab/genre/COMIC")
            .addQueryParameter("adultToggle", "false")
            .addQueryParameter("sort", sort)
            .apply { if (genres.isNotEmpty()) addQueryParameter("groupGenres", genres.joinToString(",")) }
            .build()
        // The API sends no cache headers: later pages reuse the list page 1 stored
        val cache = if (page == 1) CacheControl.FORCE_NETWORK else LIST_CACHE
        val ids = browseIds(idsUrl, freeOnly, cache)
        val pageIds = ids.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)
        if (pageIds.isEmpty()) return MangasPage(emptyList(), false)
        val body = DetailsBodyDto(pageIds.joinToString(","), "MAIN", "COMIC").toJsonRequestBody()
        val cards = client.post(apiUrl.newBuilder().addPathSegments("contents/tab/details").build(), apiHeaders, Fetch.XHR, body)
            .parseAs<ApiResultDto<List<ListItemDto>>>().data
        return MangasPage(cards.map { it.toSManga() }, page * PAGE_SIZE < ids.size)
    }

    private suspend fun browseIds(idsUrl: HttpUrl, freeOnly: Boolean, cache: CacheControl): List<Long> {
        val ids = client.get(idsUrl, apiHeaders, Fetch.XHR, cache).parseAs<ApiResultDto<List<Long>>>().data
        if (!freeOnly) return ids
        // The free tab has the site's free series, in its own order and without genres
        val freeUrl = apiUrl.newBuilder()
            .addPathSegments("contents/tab/free/comic")
            .addQueryParameter("adultToggle", "false")
            .build()
        val freeIds = client.get(freeUrl, apiHeaders, Fetch.XHR, cache).parseAs<ApiResultDto<List<Long>>>().data.toSet()
        return ids.filter { it in freeIds }
    }

    private var searchCursor: String? = null

    private suspend fun search(page: Int, query: String): MangasPage {
        if (page == 1) searchCursor = null
        val url = apiUrl.newBuilder()
            .addPathSegments("search/all")
            .addQueryParameter("searchText", query)
            .addQueryParameter("isIncludeAdult", "true")
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .addQueryParameter("isCheckDevice", "true")
            .addQueryParameter("contentsThumbnailType", "MAIN")
            .addQueryParameter("searchMethod", "click")
            .addQueryParameter("sort", "POPULAR")
            // The cursor comes percent-encoded and goes back as is
            .apply { searchCursor?.let { addEncodedQueryParameter("nextPagination", it) } }
            .build()
        val result = client.get(url, apiHeaders, Fetch.XHR.from("/search")).parseAs<ApiResultDto<SearchDto>>().data
        searchCursor = result.nextPagination
        return MangasPage(result.contents.map { it.toSManga() }, result.hasNext)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host.removePrefix("www.") != baseUrlHost.removePrefix("www.") || segments.size < 2 || segments[0] !in setOf("detail", "viewer")) return null
        return apiContent(segments[1]).toSManga(JsonObject(emptyMap())).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) apiContent(manga.url).toSManga(manga.memo) else manga },
        chapters = {
            if (!fetchChapters) return@SMangaUpdate chapters
            val showPaid = preferences.getBoolean(PREF_SHOW_PAID, false)
            pageContent(manga).episodes
                .filter { showPaid || !it.isPaid }
                .map { it.toSChapter(manga.url) }
                .reversed()
        },
    )

    private suspend fun apiContent(alias: String): ContentDto {
        val url = apiUrl.newBuilder()
            .addPathSegments("contents")
            .addPathSegment(alias)
            .addQueryParameter("isNotLoginAdult", "false")
            .addQueryParameter("isPorch", "false")
            .build()
        return client.get(url, apiHeaders, Fetch.XHR.from("/detail/$alias")).parseAs<ApiResultDto<ContentDto?>>().data
            ?: throw Exception(intl["series_unavailable"])
    }

    // The series page is rendered with the session of the WebView login, so bought episodes show as such
    private suspend fun pageContent(manga: SManga): SeriesPageContentDto = client.get(getMangaUrl(manga), Fetch.DOCUMENT)
        .extractNextJs<DetailPageDto> { it is JsonObject && "ssrDetail" in it }?.ssrDetail
        ?: throw Exception(intl["series_unavailable"])

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = apiUrl.newBuilder()
            .addPathSegments("contents/related-contents")
            .addPathSegment(manga.memo.getLongOrNull(MEMO_ID)?.toString() ?: throw Exception(intl["refresh_series"]))
            .addQueryParameter("isIncludeAdult", "false")
            .addQueryParameter("contentsThumbnailType", "VERTICAL")
            .build()
        return client.get(url, apiHeaders, Fetch.XHR.from("/detail/${manga.url}")).parseAs<ApiResultDto<List<ListItemDto>>>().data
            .map { it.toSManga() }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("detail")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("viewer")
        .addPathSegments(chapter.url)
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> = episode(chapter.url).images.mapIndexed { index, image ->
        val url = image.imagePath.toHttpUrl().newBuilder().fragment(chapter.url).build()
        Page(index, imageUrl = url.toString())
    }

    private suspend fun episode(chapter: String): EpisodeDto {
        val chapterUrl = getChapterUrl(SChapter.create().apply { url = chapter })
        val data = client.get(chapterUrl, Fetch.DOCUMENT, cacheControl = CacheControl.FORCE_NETWORK)
            .extractNextJs<ViewerPageDto>()?.episodeData
        data?.result?.let { return it }
        throw Exception(
            when (data?.error?.code) {
                "NOT_LOGIN_USER" -> intl["login_required"]
                "UNAUTHORIZED_CONTENTS" -> intl["purchase_required"]
                else -> intl["episode_unavailable"]
            },
        )
    }

    private val refreshLock = Mutex()

    private class RefreshedUrls(val chapter: String, val urls: Map<String, String>, val at: TimeMark)

    private var refreshed: RefreshedUrls? = null

    // Signed image urls expire after two to five minutes; a failed page gets its url from a fresh viewer page
    private fun freshImageUrl(chapter: String, path: String): String = runBlocking {
        refreshLock.withLock {
            val urls = refreshed?.takeIf { it.chapter == chapter && it.at.elapsedNow() < REFRESH_REUSE }?.urls
                ?: episode(chapter).images.associate { it.imagePath.toHttpUrl().encodedPath to it.imagePath }
                    .also { refreshed = RefreshedUrls(chapter, it, TimeSource.Monotonic.markNow()) }
            urls[path] ?: throw IOException(intl["episode_unavailable"])
        }
    }

    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val chapter = request.url.fragment
        if (response.code != 403 || chapter == null) return response
        response.close()
        val url = try {
            freshImageUrl(chapter, request.url.encodedPath)
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e.message, e)
        }
        return chain.proceed(request.newBuilder().url(url).build())
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder()
            .addPathSegments("tag/genre/v2")
            .addQueryParameter("contentsType", "COMIC")
            .addQueryParameter("isIncludeAdult", "true")
            .build()
        // "etc" groups the genres listed after it and isn't one the genre tab accepts
        return client.get(url, apiHeaders, Fetch.XHR).parseAs<ApiResultDto<List<GenreDto>>>().data
            .filter { it.id != null }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            SortFilter(intl["sort"], arrayOf(intl["sort_popular"], intl["sort_latest"])),
            FreeFilter(intl["free_only"]),
            data?.parseAs<List<GenreDto>>()?.let { genres ->
                GenreFilter(intl["genre"], genres.map { GenreCheckBox(it.name, it.internalName) })
            },
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PAID
            title = intl["show_paid_title"]
            summary = intl["show_paid_summary"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 30
        private val REFRESH_REUSE = 1.minutes
        private val LIST_CACHE = CacheControl.Builder().maxStale(10, TimeUnit.MINUTES).build()
        private const val PREF_SHOW_PAID = "show_paid"
    }
}
