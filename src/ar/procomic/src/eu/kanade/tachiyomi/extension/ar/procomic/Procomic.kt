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
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
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
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.security.KeyPair
import java.util.concurrent.TimeUnit
import kotlin.reflect.KMutableProperty0
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Source
abstract class Procomic :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    private val apiUrl = "https://$API_HOST"
    private val siteHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = this
        .rateLimit(4) { it.host == API_HOST }
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(::pageInterceptor)

    override fun Headers.Builder.configureHeaders() = this
        .set("User-Agent", ProChanSigner.READ_UA)
        .removeAll("Referer")

    private var popularPage = 1
    private var latestPage = 1
    private var searchPage = 1

    override suspend fun getPopularManga(page: Int): MangasPage = paged(page, ::popularPage) { cursor ->
        val url = homeFeedUrl("top-by-views", cursor) {
            addQueryParameter("category", "all")
            addQueryParameter("days", "10")
        }
        val data = signedGet(url).parseAs<TopFeedDto>()
        data.data.filter { it.isReadable }.map { it.toSManga(API_HOST) } to (data.meta.hasMore && data.data.isNotEmpty())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = paged(page, ::latestPage) { cursor ->
        val url = homeFeedUrl("latest-updates", cursor) {
            addQueryParameter("category", "comics")
        }
        val data = signedGet(url).parseAs<LatestFeedDto>()
        data.data.filter { it.isReadable }.map { it.toSManga() } to (data.meta.hasMore && data.data.isNotEmpty())
    }

    private fun homeFeedUrl(path: String, page: Int, params: HttpUrl.Builder.() -> Unit) = "$apiUrl/api/android/feed".toHttpUrl().newBuilder()
        .addPathSegment(path)
        .apply(params)
        .addQueryParameter("limit", HOME_LIMIT.toString())
        .addQueryParameter("page", page.toString())
        .addQueryParameter("g", "1")
        .build()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = paged(page, ::searchPage) { cursor ->
        val url = "$apiUrl/api/android/feed/all-series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search", query)
            addQueryParameter("sort", filters.firstInstance<SortFilter>().selected)
            filters.firstInstance<TypeFilter>().selected?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.let { genres ->
                genres.included.takeIf { it.isNotEmpty() }?.let { addQueryParameter("includeGenres", it.joinToString(",")) }
                genres.excluded.takeIf { it.isNotEmpty() }?.let { addQueryParameter("excludeGenres", it.joinToString(",")) }
            }
            addQueryParameter("limit", LIST_LIMIT.toString())
            addQueryParameter("page", cursor.toString())
            addQueryParameter("g", "1")
        }.build()
        val data = signedGet(url).parseAs<FeedDto>()
        data.data.filter { it.isReadable }.map { it.toSManga(API_HOST) } to data.hasNextPage
    }

    // A filter or the novel and blocked-series drop can empty a page while later pages still have entries; skip the empty ones.
    private suspend fun paged(page: Int, cursor: KMutableProperty0<Int>, fetch: suspend (Int) -> Pair<List<SManga>, Boolean>): MangasPage {
        if (page == 1) cursor.set(1)
        while (true) {
            val (mangas, hasNextPage) = fetch(cursor.get())
            cursor.set(cursor.get() + 1)
            if (mangas.isNotEmpty() || !hasNextPage) return MangasPage(mangas, hasNextPage)
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
            val headers = buildMap {
                chapter.readerSession?.token?.let { put("X-ProChan-Reader-Session", it) }
                put("X-ProChan-Attestation", "failed")
            }
            signedGet(url, headers).parseAs<ReaderWrapperDto>().data
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
                val response = retryOnStall { chain.proceed(request.newBuilder().url(current.images[index]).build()).buffered() }
                if (response.code != 410) return response
                response.close()
                retryOnStall { chain.proceed(request.newBuilder().url(reader(chapterId, current).images[index]).build()).buffered() }
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
                val bytes = retryOnStall {
                    runBlocking { client.get(map.pieces[map.order[cell]].toHttpUrl(), Fetch.IMAGE, ensureSuccess = false) }
                        .use { if (it.code == 410) null else it.body.bytes() }
                } ?: return null
                val piece = ImageDecoder.decode(bytes)
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

    private class Device(val id: String, val keys: KeyPair)

    @Volatile
    private var currentDevice: Device? = null

    private suspend fun signedGet(url: HttpUrl, extraHeaders: Map<String, String> = emptyMap()): Response {
        val device = device()
        val response = signedAttempt(url, extraHeaders, device)
        // The server can forget a registered device; register the same key again once.
        if (response.code == 401 && response.body.contentType()?.subtype == "json" && response.parseAs<ErrorDto>().value in FORGOTTEN_DEVICE_CODES) {
            return signedAttempt(url, extraHeaders, reregister(device)).ensureSuccess()
        }
        return response.ensureSuccess()
    }

    // Re-signed on every attempt: the server rejects a reused nonce.
    private suspend fun signedAttempt(url: HttpUrl, extraHeaders: Map<String, String>, device: Device): Response = retryOnStall {
        val headers = ProChanSigner.readHeaders("GET", url, device.id, device.keys.private) + extraHeaders
        client.get(url, headers.toHeaders(), Fetch.NONE, ensureSuccess = false).buffered()
    }

    private fun Response.ensureSuccess(): Response {
        if (isSuccessful) return this
        close()
        throw HttpException(code)
    }

    // The site cuts large transfers off mid-body at random; a fresh request usually gets the whole body.
    private inline fun <T> retryOnStall(block: () -> T): T {
        repeat(READ_ATTEMPTS - 1) {
            try {
                return block()
            } catch (_: IOException) {
            }
        }
        return block()
    }

    // Reads the body inside the attempt, so a stall surfaces where it can be retried.
    private fun Response.buffered(): Response {
        val type = body.contentType()
        val bytes = body.use { it.bytes() }
        return newBuilder().body(bytes.toResponseBody(type)).build()
    }

    private suspend fun device(): Device {
        currentDevice?.takeUnless { attestDue() }?.let { return it }
        return registerMutex.withLock {
            val device = currentDevice ?: storedDevice() ?: register()
            currentDevice = device
            if (attestDue()) attest(device)
            device
        }
    }

    private fun storedDevice(): Device? {
        val id = preferences.getString(DEVICE_ID_KEY, null) ?: return null
        val privateKey = preferences.getString(DEVICE_KEY_KEY, null) ?: return null
        val publicKey = preferences.getString(DEVICE_PUBLIC_KEY_KEY, null) ?: return null
        return Device(id, KeyPair(ProChanSigner.decodePublicKey(publicKey), ProChanSigner.decodePrivateKey(privateKey)))
    }

    private suspend fun reregister(stale: Device): Device = registerMutex.withLock {
        currentDevice?.takeIf { it !== stale }?.let { return@withLock it }
        register(stale.keys).also { currentDevice = it }
    }

    private suspend fun register(keys: KeyPair = ProChanSigner.newKeyPair()): Device {
        val url = "$apiUrl/api/android/device/register".toHttpUrl()
        val body = RegisterRequestDto(ProChanSigner.encodePublicKey(keys.public)).toJsonString().toByteArray()
        val id = retryOnStall {
            val headers = ProChanSigner.registerHeaders(url, body).toHeaders()
            client.post(url, headers, Fetch.NONE, body.toRequestBody(JSON_MEDIA_TYPE)).buffered()
        }.parseAs<RegisterResponseDto>().deviceId
        preferences.edit()
            .putString(DEVICE_ID_KEY, id)
            .putString(DEVICE_KEY_KEY, ProChanSigner.encodePrivateKey(keys.private))
            .putString(DEVICE_PUBLIC_KEY_KEY, ProChanSigner.encodePublicKey(keys.public))
            .remove(ATTEST_AT_KEY)
            .apply()
        return Device(id, keys)
    }

    private fun attestDue() = Clock.System.now() - Instant.fromEpochMilliseconds(preferences.getLong(ATTEST_AT_KEY, 0)) > ATTEST_INTERVAL

    // The app's attestation for a key without a hardware certificate chain: an empty chain and the error.
    // Like the app, a failed attempt doesn't fail the read and is retried after ATTEST_INTERVAL.
    private suspend fun attest(device: Device) {
        preferences.edit().putLong(ATTEST_AT_KEY, Clock.System.now().toEpochMilliseconds()).apply()
        try {
            val nonce = devicePost("attest/challenge", "{}".toByteArray(), device).parseAs<AttestChallengeDto>().nonce
            val body = AttestRequestDto(ProChanSigner.encodePublicKey(device.keys.public), nonce, emptyList(), "IllegalStateException")
            devicePost("attest", body.toJsonString().toByteArray(), device).close()
        } catch (_: IOException) {
        } catch (_: HttpException) {
        }
    }

    private suspend fun devicePost(path: String, body: ByteArray, device: Device): Response {
        val url = "$apiUrl/api/android/device/$path".toHttpUrl()
        val headers = ProChanSigner.deviceHeaders(url, body, device.id, device.keys.private)
        return client.post(url, headers.toHeaders(), Fetch.NONE, body.toRequestBody(JSON_MEDIA_TYPE))
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

        // Small pages: the site cuts responses over ~97 KB off mid-transfer.
        private const val LIST_LIMIT = 10
        private const val READ_ATTEMPTS = 5
        private const val HOME_LIMIT = 18
        private const val CHAPTER_LIMIT = 100
        private const val IMAGE_HOST = "procomic.image"
        private const val MAP_HOST = "procomic.map"
        private const val DEVICE_ID_KEY = "device_id"
        private const val DEVICE_KEY_KEY = "device_key"
        private val FORGOTTEN_DEVICE_CODES = setOf("device_required", "unknown_device")
        private const val DEVICE_PUBLIC_KEY_KEY = "device_public_key"
        private const val ATTEST_AT_KEY = "attest_at"
        private val ATTEST_INTERVAL = 1.hours
        private const val SHOW_LOCKED_KEY = "showLockedChapters"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
