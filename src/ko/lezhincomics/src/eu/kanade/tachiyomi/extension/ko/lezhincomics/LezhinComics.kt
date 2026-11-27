package eu.kanade.tachiyomi.extension.ko.lezhincomics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.time.Clock

@Source
abstract class LezhinComics :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val apiUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegments("lz-api/v2/").build()

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrlHost }
        .addInterceptor(::pageInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(ListFilter.POPULAR, page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(ListFilter.LATEST, page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return search(page, query.trim(), filters.firstInstance<CompletedFilter>().state)
        return browse(filters.firstInstance<ListFilter>().selected, page, filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty())
    }

    private suspend fun browse(list: ListOption, page: Int, genre: String = ""): MangasPage {
        val ranking = list.path == "ranking"
        val url = apiUrl.newBuilder().addPathSegment("content-list").addPathSegment(list.path).apply {
            list.params.forEach { (key, value) -> addQueryParameter(key, value) }
            // The site's ranking page shows one list of up to 100
            if (ranking) {
                addQueryParameter("genres", genre)
                addQueryParameter("limit", "100")
            } else {
                addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
                addQueryParameter("limit", PAGE_SIZE.toString())
            }
        }.build()
        val result = client.get(url, listHeaders, Fetch.XHR).parseAs<ListDto<ContentDto>>()
        return MangasPage(result.data.map { it.toSManga() }, !ranking && result.hasNext)
    }

    private val showAdult get() = preferences.getBoolean(PREF_SHOW_ADULT, false)

    // The site's client asks for adult series with these headers once adult mode is on
    private val listHeaders get() = headers.newBuilder().apply {
        if (showAdult) {
            set("x-lz-adult", "1")
            set("x-lz-allowadult", "true")
        }
    }.build()

    private var searchOffset = 0

    // Search lists adult series, which open only after an adult-verified login; pages skip them unless shown
    private suspend fun search(page: Int, query: String, completedOnly: Boolean): MangasPage {
        if (page == 1) searchOffset = 0
        val mangas = mutableListOf<SManga>()
        var hasNextPage: Boolean
        do {
            val url = apiUrl.newBuilder().addPathSegment("advanced-search")
                .addQueryParameter("q", query)
                .addQueryParameter("t", "all")
                .addQueryParameter("order", "popular")
                .apply { if (completedOnly) addQueryParameter("state", "completed") }
                .addQueryParameter("offset", searchOffset.toString())
                .addQueryParameter("limit", PAGE_SIZE.toString())
                .build()
            val list = client.get(url, listHeaders, Fetch.XHR.from("/ko/search")).parseAs<ListDto<ContentDto>>()
            searchOffset += list.data.size
            mangas += list.data.filter { showAdult || !it.adult }.map { it.toSManga() }
            hasNextPage = list.hasNext && list.data.isNotEmpty()
        } while (mangas.size < PAGE_SIZE / 2 && hasNextPage)
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrlHost || segments.getOrNull(0) != "ko" || segments.getOrNull(1) != "comic") return null
        val alias = segments.getOrNull(2)?.ifEmpty { null } ?: return null
        val manga = SManga.create().apply { this.url = alias }
        return comicPage(manga).toSManga(manga).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = comicPage(manga)
        val now = Clock.System.now().toEpochMilliseconds()
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, false)
        return SMangaUpdate(
            comic.toSManga(manga),
            comic.episodes
                .filter { showLocked || it.isFree(now) }
                .map { it.toSChapter(manga.url, now) },
        )
    }

    private suspend fun comicPage(manga: SManga): ComicPageDto {
        val response = client.get(getMangaUrl(manga), Fetch.DOCUMENT)
        // Adult series redirect to the login or adult verification page
        if (response.request.url.pathSegments.getOrNull(1) != "comic") {
            response.close()
            throw Exception("로그인과 성인 인증이 필요한 작품입니다. WebView에서 로그인해 주세요")
        }
        return response.extractNextJs<ComicPageDto>()
            ?: throw Exception("작품 정보를 찾을 수 없습니다. WebView에서 확인해 주세요")
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("ko/comic")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("ko/comic")
        .addPathSegments(chapter.url)
        .build()
        .toString()

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = comicPage(manga).related()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val viewer = viewer(chapter.url) ?: return emptyList()
        val episode = viewer.state.episode
        val media = viewer.state.media
        return media.scrollView.ifEmpty { media.pageView }
            .filter { it.cutType == null || it.cutType == "contents" }
            .mapIndexed { index, cut ->
                val url = IMAGE_URL.toHttpUrl().newBuilder()
                    .addPathSegments(cut.path.removePrefix("/") + ".webp")
                    .addQueryParameter("purchased", viewer.permission.isCollected.toString())
                    .addQueryParameter("q", QUALITY)
                    .addQueryParameter("updated", episode.updatedAt.toString())
                    .sign(viewer.signature)
                    // "<chapter url>[,<shuffle seed>]"
                    .fragment(listOfNotNull(chapter.url, episode.id.takeIf { media.imageShuffle && !cut.isGif }).joinToString(","))
                    .build()
                Page(index, imageUrl = url.toString())
            }
    }

    private class Viewer(val state: ViewerStateDto, val permission: PermissionDto, val signature: SignedUrlDto)

    private class ViewerPage(val viewer: Viewer?, val error: ErrorDto?)

    private fun sessionCookie(name: String) = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == name }?.value

    // The viewer page is rendered with the session cookies of the WebView login; null for a paid episode
    private suspend fun viewer(chapter: String): Viewer? {
        val chapterUrl = getChapterUrl(SChapter.create().apply { url = chapter })
        suspend fun fetch() = client.get(chapterUrl, Fetch.DOCUMENT, cacheControl = CacheControl.FORCE_NETWORK).viewerPage()
        val accessToken = sessionCookie(ACCESS_TOKEN_COOKIE)
        var page = fetch()
        if (page.error?.code == LOGIN_REQUIRED && refreshSession(accessToken)) page = fetch()
        page.viewer?.let { return it }
        return when (page.error?.code) {
            PAYMENT_REQUIRED -> null
            LOGIN_REQUIRED -> throw Exception("로그인이 필요한 회차입니다. WebView에서 로그인해 주세요")
            null -> throw Exception("회차 정보를 찾을 수 없습니다. WebView에서 확인해 주세요")
            else -> throw Exception(page.error.description.substringAfter("] "))
        }
    }

    private val sessionLock = Mutex()

    // The access token cookie lasts a day; the site renews it with the refresh token cookie
    private suspend fun refreshSession(failedAccessToken: String?): Boolean = sessionLock.withLock {
        // Renewed meanwhile by another call
        if (sessionCookie(ACCESS_TOKEN_COOKIE) != failedAccessToken) return@withLock true
        val hasRefreshToken = sessionCookie(REFRESH_TOKEN_COOKIE) != null
        if (hasRefreshToken) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/authentication/refresh-token")
                .addQueryParameter("redirect", "/ko")
                .build()
            client.get(url, Fetch.DOCUMENT, cacheControl = CacheControl.FORCE_NETWORK, ensureSuccess = false).close()
        }
        hasRefreshToken
    }

    private fun Response.viewerPage(): ViewerPage {
        val document = asJsoup()
        val state = document.extractNextJs<ViewerStateDto>()
        val permission = document.extractNextJs<PermissionDto> { it is JsonObject && "isCollected" in it && "firstCheckType" in it }
        val signature = document.extractNextJs<SignedUrlDto>()
        if (state != null && permission != null && signature != null) return ViewerPage(Viewer(state, permission, signature), null)
        val error = document.extractNextJs<ErrorDto> { it is JsonObject && (it["code"] as? JsonPrimitive)?.intOrNull?.let { code -> code < 0 } == true }
        return ViewerPage(null, error)
    }

    private fun HttpUrl.Builder.sign(signature: SignedUrlDto) = setQueryParameter("Policy", signature.policy)
        .setQueryParameter("Signature", signature.signature)
        .setQueryParameter("Key-Pair-Id", signature.keyPairId)

    private val signatureLock = Mutex()

    private var lastSignature: Pair<String, SignedUrlDto>? = null

    // Concurrent page loads of one episode share the fresh signature
    private fun freshSignature(chapter: String, expiredPolicy: String?): SignedUrlDto = runBlocking {
        signatureLock.withLock {
            lastSignature?.takeIf { it.first == chapter && it.second.policy != expiredPolicy }?.second
                ?: (viewer(chapter) ?: throw Exception("구매가 필요한 회차입니다")).signature.also { lastSignature = chapter to it }
        }
    }

    // Signed image urls expire after 5 minutes; the app keeps page urls in its page list cache
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.url.host != IMAGE_HOST) return chain.proceed(request)
        val fragment = request.url.fragment?.split(',') ?: return chain.proceed(request)
        val shuffleKey = fragment.getOrNull(1)?.toLongOrNull()
        var response = chain.proceed(request)
        if (response.code == 403) {
            response.close()
            val signature = try {
                freshSignature(fragment[0], request.url.queryParameter("Policy"))
            } catch (e: Exception) {
                throw e as? IOException ?: IOException(e.message, e)
            }
            request = request.newBuilder().url(request.url.newBuilder().sign(signature).build()).build()
            response = chain.proceed(request)
        }
        if (shuffleKey == null || !response.isSuccessful) return response
        return unshuffle(response, shuffleKey)
    }

    private fun unshuffle(response: Response, seed: Long): Response {
        val shuffled = response.body.byteStream().use(BitmapFactory::decodeStream)
            ?: throw IOException("이미지를 불러오지 못했습니다")
        val page = Unshuffler.unshuffle(shuffled, seed)
        shuffled.recycle()
        val buffer = Buffer()
        page.compress(Bitmap.CompressFormat.WEBP, 90, buffer.outputStream())
        page.recycle()
        return response.newBuilder().body(buffer.asResponseBody("image/webp".toMediaType())).build()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrl.newBuilder().addPathSegments("genre-list/all").build(), Fetch.XHR)
        .parseAs<ListDto<GenreDto>>().data
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            ListFilter(),
            data?.parseAs<List<GenreDto>>()?.let(::GenreFilter),
            CompletedFilter(),
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "유료 회차 표시"
            summary = "구매가 필요한 회차를 🔒 표시와 함께 목록에 보여줍니다"
            setDefaultValue(false)
        }.also(screen::addPreference)
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_ADULT
            title = "성인 작품 표시"
            summary = "성인 인증을 마친 계정으로 WebView에서 로그인해야 볼 수 있습니다"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val LOGIN_REQUIRED = -62640
        private const val PAYMENT_REQUIRED = -62402
        private const val ACCESS_TOKEN_COOKIE = "_LZ_AT"
        private const val REFRESH_TOKEN_COOKIE = "_LZ_RT"
        private const val IMAGE_HOST = "rcdn.lezhin.com"
        private const val IMAGE_URL = "https://$IMAGE_HOST/v2"
        private const val QUALITY = "40"
        private const val PREF_SHOW_LOCKED = "show_locked"
        private const val PREF_SHOW_ADULT = "show_adult"
    }
}
