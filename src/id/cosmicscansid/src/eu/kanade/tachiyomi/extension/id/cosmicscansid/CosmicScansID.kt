package eu.kanade.tachiyomi.extension.id.cosmicscansid

import android.util.Base64
import android.util.LruCache
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.xorInPlace
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class CosmicScansID : KeiSource() {

    private val apiUrl = "https://cdncid.csmcscns.id/v1/manga"

    // Next page's cursor and the listing it belongs to
    private var nextCursor: Pair<String, String>? = null

    private val imageSessions = LruCache<String, ImageSession>(16)

    private val imageSessionMutex = Mutex()

    private var failedImageSession: FailedImageSession? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::imageInterceptor)
        .rateLimit(3, 1.seconds)

    // URL compat: handle old "/manga/slug" and new "/series/slug"
    private fun SManga.slug(): String = url
        .removePrefix("/manga/")
        .removePrefix("/series/")
        .trimEnd('/')

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = browse("popular", page) {
        addQueryParameter("order_by", "popular")
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = browse("update", page) {
        addQueryParameter("order_by", "update")
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "48")
                .addQueryParameter("q", query)
                .build()
            val result = client.get(url, Fetch.XHR).parseAs<MangaListResponse>()
            return MangasPage(result.data.map { it.toSManga() }, false)
        }

        return browse(searchKey(filters), page) { addFilters(filters) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val firstSegment = url.pathSegments.firstOrNull()
        if (firstSegment != "series" && firstSegment != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/series/$slug")
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ======================= Details and Chapters ==========================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url.substringAfterLast('/')}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug()
        val response = client.get("$apiUrl/mangaDetail/$slug", Fetch.XHR)
        val data = response.parseAs<MangaDetailResponse>().data
        val parsedChapters = data.chapters.orEmpty()
            .filter { it.redirectLink.isNullOrBlank() }
            .map { it.toSChapter() }
        return SMangaUpdate(
            manga = data.toSMangaDetails(slug),
            chapters = parsedChapters,
        )
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.substringAfterLast('/')
        val response = client.get("$apiUrl/readingPage/$slug", Fetch.XHR)
        val data = response.parseAs<ReadingPageResponse>().data
        if (!data.redirectLink.isNullOrBlank()) return emptyList()
        return List(data.pageCount!!) { Page(it, imageUrl = "https://$PAGE_HOST/$slug/$it") }
    }

    // Stable page urls: the site's image tokens expire within minutes of being minted
    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)
        val (slug, index) = request.url.pathSegments

        val session = requestImageSession(slug)
        val response = chain.proceed(request.newBuilder().url(session.imageUrl(slug, index)).build())
        if (response.code != 403) return response

        response.close()
        val renewed = requestImageSession(slug, rejected = session)
        return chain.proceed(request.newBuilder().url(renewed.imageUrl(slug, index)).build())
    }

    private fun requestImageSession(slug: String, rejected: ImageSession? = null): ImageSession = try {
        runBlocking { imageSession(slug, rejected) }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException(e.message, e)
    }

    // One session per chapter like the site's reader; a failed solve replays for 10 s
    private suspend fun imageSession(slug: String, rejected: ImageSession?): ImageSession = imageSessionMutex.withLock {
        imageSessions[slug]?.takeIf { it !== rejected }?.let { return it }

        val now = Clock.System.now()
        failedImageSession
            ?.takeIf { it.slug == slug && now - it.failedAt < 10.seconds }
            ?.let { throw it.error }

        try {
            val config = client.get("$apiUrl/img/config", Fetch.XHR).parseAs<ImageConfigResponse>().data
            val token = getTurnstileToken(baseUrl, config.turnstileSiteKey)
            val body = ImageSessionRequest(token, slug).toJsonRequestBody()
            client.post("$apiUrl/img/session", Fetch.XHR, body = body).parseAs<ImageSessionResponse>().data
                .also { imageSessions.put(slug, it) }
        } catch (e: Exception) {
            failedImageSession = FailedImageSession(slug, now, e)
            throw e
        }
    }

    private fun ImageSession.imageUrl(slug: String, index: String): String {
        val (signKey, maskKey) = axisKeys.map { it.decodeHex() }
        val masked = "$slug:$index:${Clock.System.now().toEpochMilliseconds()}".encodeToByteArray().xorInPlace(maskKey)
        return "$apiUrl/img/r/$sessionId.${masked.hmacSha256(signKey).base64Url()}.${masked.base64Url()}"
    }

    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private class FailedImageSession(val slug: String, val failedAt: Instant, val error: Exception)

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?): FilterList = getCosmicScansIDFilterList()

    // ============================= Utilities ==============================
    private suspend fun browse(key: String, page: Int, params: Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/filter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply(params)
        if (page > 1) {
            val cursor = nextCursor?.takeIf { it.first == key } ?: return MangasPage(emptyList(), false)
            url.addQueryParameter("after", cursor.second)
        }
        val result = client.get(url.build(), Fetch.XHR).parseAs<MangaListResponse>()
        nextCursor = result.cursor?.nextCursor?.let { key to it }
        return MangasPage(result.data.map { it.toSManga() }, result.cursor?.hasNext == true)
    }

    private fun Builder.addFilters(filters: FilterList): Builder = apply {
        filters.firstInstanceOrNull<OrderFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("order_by", it) }

        filters.firstInstanceOrNull<StatusFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("release_status", it) }

        filters.firstInstanceOrNull<TypeFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("type_manga", it) }

        filters.firstInstanceOrNull<ProjectFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("is_project", it) }

        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter { it.state }
            .forEach { addQueryParameter("genres_slug", it.slug) }
    }

    private fun searchKey(filters: FilterList): String = listOf(
        filters.firstInstanceOrNull<OrderFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<TypeFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<ProjectFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter { it.state }.joinToString(",") { it.slug },
    ).joinToString(":")

    companion object {
        private const val PAGE_SIZE = 24
        private const val PAGE_HOST = "cosmicscans.invalid"
    }
}
