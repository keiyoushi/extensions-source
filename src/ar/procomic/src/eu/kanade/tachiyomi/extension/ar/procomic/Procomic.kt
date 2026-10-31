package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.LruCache
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.imagedecoder.ImageDecoder
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.security.PrivateKey
import kotlin.reflect.KMutableProperty0

@Source
abstract class Procomic :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val apiUrl = "https://$API_HOST"
    private val siteHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(::pageInterceptor)

    override fun Headers.Builder.configureHeaders() = this
        .set("User-Agent", ProChanSigner.READ_UA)
        .removeAll("Origin")
        .removeAll("Referer")

    private var popularPage = 1
    private var latestPage = 1
    private var searchPage = 1

    override suspend fun getPopularManga(page: Int): MangasPage = feed(page, ::popularPage) {
        addQueryParameter("sort", "popular")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = feed(page, ::latestPage) {
        addQueryParameter("sort", "latest")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = feed(page, ::searchPage) {
        if (query.isNotBlank()) addQueryParameter("search", query)
        addQueryParameter("sort", filters.firstInstance<SortFilter>().selected)
        filters.firstInstance<TypeFilter>().selected?.let { addQueryParameter("type", it) }
        filters.firstInstanceOrNull<GenreFilter>()?.let { genres ->
            genres.included.takeIf { it.isNotEmpty() }?.let { addQueryParameter("includeGenres", it.joinToString(",")) }
            genres.excluded.takeIf { it.isNotEmpty() }?.let { addQueryParameter("excludeGenres", it.joinToString(",")) }
        }
    }

    // A genre filter or the novel drop can empty a page while later pages still have entries; skip the empty ones.
    private suspend fun feed(page: Int, cursor: KMutableProperty0<Int>, params: HttpUrl.Builder.() -> Unit): MangasPage {
        if (page == 1) cursor.set(1)
        while (true) {
            val url = "$apiUrl/api/android/feed/all-series".toHttpUrl().newBuilder()
                .apply(params)
                .addQueryParameter("limit", LIST_LIMIT.toString())
                .addQueryParameter("page", cursor.get().toString())
                .addQueryParameter("g", "1")
                .build()
            val data = signedGet(url).parseAs<FeedDto>()
            cursor.set(cursor.get() + 1)
            val mangas = data.data.filter { it.isReadable }.map { it.toSManga(API_HOST) }
            if (mangas.isNotEmpty() || !data.hasNextPage) return MangasPage(mangas, data.hasNextPage)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != siteHost && url.host != MIRROR_HOST) return null
        val segments = url.pathSegments.filterNot { it in LOCALES }
        val id = segments.lastOrNull()?.substringAfterLast('-')?.toIntOrNull() ?: return null
        val seriesId = if (segments.first() == "chapter") {
            runCatchingHttp { signedGet(chapterApiUrl(id)).parseAs<ChapterRefDto>().contentId } ?: return null
        } else {
            id
        }
        val series = runCatchingHttp { signedGet(contentApiUrl(seriesId)).parseAs<ContentWrapperDto>().data } ?: return null
        return series.takeIf { it.isReadable }?.toDetails(API_HOST)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) {
            async { signedGet(contentApiUrl(manga.url.toInt())).parseAs<ContentWrapperDto>().data.toDetails(API_HOST) }
        } else {
            null
        }
        val chapterList = if (fetchChapters) async { getChapterList(manga) } else null
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val url = "$apiUrl/api/android/manga/${manga.url}/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("limit", CHAPTER_LIMIT.toString())
            .addQueryParameter("order", "desc")
            .build()

        val first = signedGet(url.newBuilder().addQueryParameter("page", "1").build()).parseAs<ChapterListDto>()
        val pages = (first.meta.total + CHAPTER_LIMIT - 1) / CHAPTER_LIMIT
        val rest = coroutineScope {
            (2..pages).map { page ->
                async { signedGet(url.newBuilder().addQueryParameter("page", page.toString()).build()).parseAs<ChapterListDto>() }
            }.awaitAll()
        }

        val slug = manga.memo["slug"]!!.string
        val showLocked = preferences.getBoolean(SHOW_LOCKED_KEY, false)
        return (listOf(first) + rest).asSequence()
            .flatMap { it.data.asSequence() }
            .filter { it.isArabic }
            .filter { showLocked || !it.isLocked }
            .map { it.toSChapter(slug) }
            .toList()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(lang)
        .addPathSegment("${manga.memo["slug"]!!.string}-${manga.url}")
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(lang)
        .addPathSegment("chapter")
        .addPathSegment("${chapter.memo["slug"]!!.string}-${chapter.memo["number"]!!.string}-${chapter.url}")
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.toInt()
        val reader = fetchReader(chapterId) ?: return emptyList()
        readerCache.put(chapterId, reader)
        return buildList {
            reader.images.indices.forEach { add(Page(size, imageUrl = pageUrl(IMAGE_HOST, chapterId, it))) }
            reader.maps.indices.forEach { add(Page(size, imageUrl = pageUrl(MAP_HOST, chapterId, it))) }
        }
    }

    private suspend fun fetchReader(chapterId: Int): ReaderDto? {
        val chapter = try {
            signedGet(chapterApiUrl(chapterId)).parseAs<ReaderWrapperDto>().data
        } catch (e: HttpException) {
            // A locked chapter answers 403; return no pages, as for an empty chapter.
            if (e.code == 403) return null else throw e
        }
        val deferred = chapter.deferredMedia?.token?.let { token ->
            val url = "$apiUrl/api/android/chapter/$chapterId/deferred-media".toHttpUrl().newBuilder()
                .addQueryParameter("token", token)
                .build()
            signedGet(url, chapter.readerSession?.token).parseAs<ReaderWrapperDto>().data
        }
        return ReaderDto(
            images = chapter.images + deferred?.images.orEmpty(),
            maps = chapter.maps + deferred?.maps.orEmpty(),
        )
    }

    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        return when (url.host) {
            IMAGE_HOST -> {
                val (chapterId, index) = url.pageRef()
                val current = reader(chapterId)
                var response = chain.proceed(request.newBuilder().url(current.images[index]).build())
                if (response.code == 410) {
                    response.close()
                    response = chain.proceed(request.newBuilder().url(reader(chapterId, current).images[index]).build())
                }
                response
            }
            MAP_HOST -> {
                val (chapterId, index) = url.pageRef()
                val buffer = stitch(chapterId, index, chain.call())
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
                    .build()
            }
            else -> chain.proceed(request)
        }
    }

    // Signed pieces expire together, so a 410 on any of them means refetching the chapter to re-mint the map.
    private fun stitch(chapterId: Int, index: Int, call: Call): Buffer {
        val current = reader(chapterId)
        tryStitch(current.maps[index], call)?.let { return it }
        return tryStitch(reader(chapterId, current).maps[index], call) ?: throw IOException()
    }

    private fun tryStitch(map: MapEntryDto, call: Call): Buffer? {
        val (columns, rows) = map.grid()
        val (width, height) = map.dim
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)
        try {
            for (cell in 0 until columns * rows) {
                if (call.isCanceled()) throw IOException("Canceled")
                val response = runBlocking { client.get(map.pieces[map.order[cell]].toHttpUrl(), ensureSuccess = false) }
                if (response.code == 410) {
                    response.close()
                    return null
                }
                val piece = ImageDecoder.decode(response.use { it.body.bytes() })
                val column = cell % columns
                val row = cell / columns
                // Cell edges from exact fractions so a non-divisible width/height leaves no gap.
                val destination = Rect(
                    column * width / columns,
                    row * height / rows,
                    (column + 1) * width / columns,
                    (row + 1) * height / rows,
                )
                canvas.drawBitmap(piece, null, destination, null)
                piece.recycle()
            }
            return Buffer().apply { page.compress(Bitmap.CompressFormat.JPEG, 90, outputStream()) }
        } finally {
            page.recycle()
        }
    }

    // Loads the chapter's pages, deduping concurrent loads. `stale` is the expired copy a 410 is replacing;
    // a load for it is skipped if another thread already re-minted the chapter.
    private fun reader(chapterId: Int, stale: ReaderDto? = null): ReaderDto = runBlocking {
        readerCache[chapterId]?.takeIf { it !== stale }?.let { return@runBlocking it }
        readerMutex.withLock {
            readerCache[chapterId]?.takeIf { it !== stale }?.let { return@runBlocking it }
            // reader() runs inside the image interceptor, which may throw only IOException.
            val data = try {
                fetchReader(chapterId)
            } catch (e: HttpException) {
                throw IOException(e)
            }
            data?.also { readerCache.put(chapterId, it) } ?: throw IOException()
        }
    }

    // --- Signing and device registration ---

    private val registerMutex = Mutex()
    private val readerMutex = Mutex()
    private val readerCache = LruCache<Int, ReaderDto>(4)

    @Volatile
    private var deviceId: String? = null

    @Volatile
    private var deviceKey: PrivateKey? = null

    private suspend fun signedGet(url: HttpUrl, readerSession: String? = null): Response {
        val (id, key) = device()
        val headers = ProChanSigner.readHeaders("GET", url, id, key).toMutableMap()
        readerSession?.let { headers["X-ProChan-Reader-Session"] = it }
        return client.get(url, headers.toHeaders())
    }

    private suspend fun device(): Pair<String, PrivateKey> {
        deviceId?.let { id -> deviceKey?.let { return id to it } }
        return registerMutex.withLock {
            deviceId?.let { id -> deviceKey?.let { return@withLock id to it } }
            val storedId = preferences.getString(DEVICE_ID_KEY, null)
            val storedKey = preferences.getString(DEVICE_KEY_KEY, null)
            if (storedId != null && storedKey != null) {
                val key = ProChanSigner.decodePrivateKey(storedKey)
                deviceId = storedId
                deviceKey = key
                storedId to key
            } else {
                register()
            }
        }
    }

    private suspend fun register(): Pair<String, PrivateKey> {
        val keyPair = ProChanSigner.newKeyPair()
        val url = "$apiUrl/api/android/device/register".toHttpUrl()
        val body = RegisterRequestDto(ProChanSigner.encodePublicKey(keyPair.public)).toJsonString().toByteArray()
        val headers = ProChanSigner.registerHeaders(url, body)
        val id = client.post(url, headers.toHeaders(), body.toRequestBody(JSON_MEDIA_TYPE))
            .parseAs<RegisterResponseDto>().deviceId
        preferences.edit()
            .putString(DEVICE_ID_KEY, id)
            .putString(DEVICE_KEY_KEY, ProChanSigner.encodePrivateKey(keyPair.private))
            .apply()
        deviceId = id
        deviceKey = keyPair.private
        return id to keyPair.private
    }

    // --- Filters ---

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = signedGet("$apiUrl/api/android/feed/genres".toHttpUrl().newBuilder().addQueryParameter("g", "1").build())
        .parseAs<GenresWrapperDto>().data.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>().orEmpty()
        return FilterList(
            buildList {
                add(TypeFilter())
                add(SortFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_KEY
            title = "إظهار الفصول المقفلة"
            summary = "الفصول التي تحتاج عملات أو رابط مختصر أو اشتراك لفتحها على الموقع"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun chapterApiUrl(id: Int) = "$apiUrl/api/android/chapter/$id".toHttpUrl()

    private fun contentApiUrl(id: Int) = "$apiUrl/api/android/content/$id".toHttpUrl().newBuilder()
        .addQueryParameter("g", "1")
        .build()

    private fun pageUrl(host: String, chapterId: Int, index: Int) = HttpUrl.Builder()
        .scheme("http")
        .host(host)
        .addPathSegment(chapterId.toString())
        .addQueryParameter("i", index.toString())
        .build()
        .toString()

    private fun HttpUrl.pageRef() = pathSegments[0].toInt() to queryParameter("i")!!.toInt()

    // "grid_CxR" = C columns by R rows; "vertical_N" = N full-height columns.
    private fun MapEntryDto.grid(): Pair<Int, Int> = when {
        mode.startsWith("vertical_") -> mode.removePrefix("vertical_").toInt() to 1
        else -> mode.removePrefix("grid_").split("x").let { (columns, rows) -> columns.toInt() to rows.toInt() }
    }

    private inline fun <T> runCatchingHttp(block: () -> T): T? = try {
        block()
    } catch (_: HttpException) {
        null
    }

    companion object {
        private const val API_HOST = "pro-chan.com"
        private const val MIRROR_HOST = "procomic.net"
        private val LOCALES = setOf("ar", "en")
        private const val LIST_LIMIT = 50
        private const val CHAPTER_LIMIT = 100
        private const val IMAGE_HOST = "procomic.image"
        private const val MAP_HOST = "procomic.map"
        private const val DEVICE_ID_KEY = "device_id"
        private const val DEVICE_KEY_KEY = "device_key"
        private const val SHOW_LOCKED_KEY = "showLockedChapters"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
