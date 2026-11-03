package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Base64
import android.util.LruCache
import android.webkit.WebResourceResponse
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
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.runWebViewBlocking
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Procomic :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy {
        // The device keys of the app-API versions.
        edit().remove("device_id").remove("device_key").remove("device_public_key").remove("attest_at").apply()
    }

    private val siteHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = this
        // The listing API 403s requests closer than about 5 s apart.
        .rateLimit(1, 5.seconds) { it.encodedPath == "/api/public/content" }
        .rateLimit(3) { it.host == siteHost || it.host == MIRROR_HOST }
        // Sensitive series render the reader only with the site's safe browsing switched off.
        .addCookie("safe_browsing" to "off")
        .addCookie({ MIRROR_HOST }, "safe_browsing" to "off")
        .addInterceptor(::retryStalls)
        .addInterceptor(::stitchInterceptor)

    @Volatile private var listToken: String? = null
    private val listTokenMutex = Mutex()

    private var popularPage = 1
    private var latestPage = 1
    private var searchPage = 1

    override suspend fun getPopularManga(page: Int): MangasPage = listing(page, ::popularPage) {
        addQueryParameter("sort", "popular")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = listing(page, ::latestPage) {
        addQueryParameter("sort", "latest_chapter")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val included = genres?.included.orEmpty()
        val excluded = genres?.excluded.orEmpty()
        // Genres are matched here, so take the API's largest pages.
        val limit = if (included.isEmpty() && excluded.isEmpty()) LIST_LIMIT else GENRE_LIST_LIMIT
        return listing(page, ::searchPage, limit, { it.hasGenres(included, excluded) }) {
            searchQuery(query, filters)
        }
    }

    private fun HttpUrl.Builder.searchQuery(query: String, filters: FilterList) {
        if (query.isNotBlank()) addQueryParameter("search", query)
        addQueryParameter("sort", filters.firstInstance<SortFilter>().selected)
        filters.firstInstance<TypeFilter>().selected?.let { addQueryParameter("type", it) }
        filters.firstInstance<StatusFilter>().selected?.let { addQueryParameter("progress", it) }
        filters.firstInstance<YearFilter>().selected?.let { addQueryParameter("year", it) }
    }

    // Skips API pages with nothing readable (most of Latest is novels), so the app's page number and the API's
    // drift apart.
    private suspend fun listing(
        page: Int,
        cursor: KMutableProperty0<Int>,
        limit: Int = LIST_LIMIT,
        keep: (ContentDto) -> Boolean = { true },
        query: HttpUrl.Builder.() -> Unit,
    ): MangasPage {
        if (page == 1) cursor.set(1)
        while (true) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/public/content")
                .apply(query)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("page", cursor.get().toString())
                .build()
            val data = listingPage(url).parseAs<ContentPageDto>()
            cursor.set(cursor.get() + 1)
            val mangas = data.data.filter { it.isReadable && keep(it) }.map { it.toDetails(siteHost) }
            if (mangas.isNotEmpty() || !data.hasNextPage) return MangasPage(mangas, data.hasNextPage)
        }
    }

    // Later pages and filters want a Turnstile token from the site's series page; tokens last about 5 min.
    private suspend fun listingPage(url: HttpUrl): Response {
        val token = listToken
        return try {
            client.get(url, listHeaders(token), Fetch.XHR.from(seriesUrl))
        } catch (e: HttpException) {
            if (e.code != 403) throw e
            client.get(url, listHeaders(newListToken(token)), Fetch.XHR.from(seriesUrl))
        }
    }

    // Reuses a token another request minted meanwhile.
    private suspend fun newListToken(stale: String?): String = listTokenMutex.withLock {
        listToken?.takeIf { it != stale }
            ?: getTurnstileToken(seriesUrl, TURNSTILE_SITE_KEY).also { listToken = it }
    }

    private val seriesUrl get() = "$baseUrl/$lang/series"

    private fun listHeaders(token: String?) = token?.let { headers.newBuilder().set("x-turnstile-token", it).build() } ?: headers

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != siteHost && url.host != MIRROR_HOST) return null
        val segments = url.pathSegments.filterNot { it in LOCALES }
        val id = segments.lastOrNull()?.substringAfterLast('-')?.toIntOrNull() ?: return null
        val seriesId = if (segments.first() == "chapter") {
            apiOrNull("chapters", id)?.parseAs<ChapterRefDto>()?.contentId ?: return null
        } else {
            id
        }
        val series = apiOrNull("content", seriesId)?.parseAs<ContentDto>() ?: return null
        return series.takeIf { it.isReadable }?.toDetails(siteHost)
    }

    private suspend fun apiOrNull(path: String, id: Int): Response? {
        val response = client.get(apiUrl(path, id), Fetch.XHR, ensureSuccess = false)
        if (response.isSuccessful) return response
        response.close()
        if (response.code == 404) return null
        throw HttpException(response.code)
    }

    private fun apiUrl(path: String, id: Int) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("api/public")
        .addPathSegment(path)
        .addPathSegment(id.toString())
        .build()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        // Entries added by the app-API versions have no type, which the chapter list needs.
        val details = if (fetchDetails || "type" !in manga.memo) {
            async { client.get(apiUrl("content", manga.url.toInt()), Fetch.XHR).parseAs<ContentDto>().toDetails(siteHost) }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async { getChapterList(if ("type" in manga.memo) manga else details!!.await()) }
        } else {
            null
        }
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/public")
            .addPathSegment(manga.memo["type"]!!.string)
            .addPathSegment(manga.url)
            .addPathSegment("chapters")
            .addQueryParameter("limit", CHAPTER_LIMIT.toString())
            .addQueryParameter("order", "desc")
            .build()

        val first = client.get(url.newBuilder().addQueryParameter("page", "1").build(), Fetch.XHR)
            .parseAs<ChapterPageDto>()
        val pages = (first.total + CHAPTER_LIMIT - 1) / CHAPTER_LIMIT
        val rest = coroutineScope {
            (2..pages).map { page ->
                async {
                    client.get(url.newBuilder().addQueryParameter("page", page.toString()).build(), Fetch.XHR)
                        .parseAs<ChapterPageDto>()
                }
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

    // The reader only hands out pages to its own client, so the chapter is opened in a WebView and each page is
    // read off the page it renders: a plain image, or the image pieces it lays out over a grid.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val reader = runWebView(READER_TIMEOUT) { readPages(chapterUrl) }
        readers.put(chapterUrl, reader)
        return reader.pages!!.mapIndexed { index, page ->
            val url = page.url ?: HttpUrl.Builder()
                .scheme("http")
                .host(PIECE_HOST)
                .fragment(PieceRefDto(chapterUrl, index, reader.origin!!, page.pieces!!).toJsonString())
                .build()
                .toString()
            Page(index, imageUrl = url)
        }
    }

    private fun WebViewScope<ReaderStateDto>.readPages(chapterUrl: String) {
        var reloads = 0
        var laidOut = -1
        var idlePolls = 0
        injectAtDocumentStart(RETRY_SCRIPT, setOf(baseUrl, "https://$MIRROR_HOST"))
        // The images themselves are fetched by the app; the reader only needs their urls. Ads and trackers
        // aren't loaded at all.
        interceptRequest { request ->
            val host = request.url.host ?: return@interceptRequest null
            val path = request.url.path.orEmpty()
            val site = listOf(siteHost, MIRROR_HOST).any { host == it || host.endsWith(".$it") }
            when {
                host.endsWith(IMAGE_DOMAIN) && (path.startsWith("/i/") || path.startsWith("/chapters/")) ->
                    WebResourceResponse("image/png", null, ByteArrayInputStream(BLANK_IMAGE))
                site || host.endsWith(IMAGE_DOMAIN) || host == TURNSTILE_HOST -> null
                else -> WebResourceResponse("text/plain", null, ByteArrayInputStream(ByteArray(0)))
            }
        }
        poll(POLL_INTERVAL) {
            evaluateJs(READER_SCRIPT) { result ->
                // Null while the page is navigating.
                val state = result.parseAs<ReaderStateDto?>() ?: return@evaluateJs
                if (state.pages != null) return@evaluateJs resolve(state)
                if (state.laidOut != laidOut) {
                    laidOut = state.laidOut
                    idlePolls = 0
                } else {
                    idlePolls++
                }
                // Reload when the site errors, or stops laying out pages before the last one.
                val stuck = state.error || (laidOut >= 0 && idlePolls > STALL_POLLS)
                when {
                    stuck && reloads < MAX_RELOADS -> {
                        reloads++
                        laidOut = -1
                        idlePolls = 0
                        evaluateJs("location.reload()")
                    }
                    stuck -> reject(IOException("The site is not responding, try again later"))
                    idlePolls > STALL_POLLS -> reject(IOException("No pages found, open the chapter in WebView"))
                }
            }
        }
        loadUrl(chapterUrl)
    }

    // The latest pages read for each chapter; the piece urls in them expire after a while.
    private val readers = LruCache<String, ReaderStateDto>(4)

    // Reads the chapter again, unless another page already did since `stale` was read.
    @Synchronized
    private fun rereadPages(chapterUrl: String, stale: ReaderStateDto?, call: Call): ReaderStateDto {
        readers[chapterUrl]?.takeIf { it !== stale }?.let { return it }
        return runWebViewBlocking(call, READER_TIMEOUT) { readPages(chapterUrl) }
            .also { readers.put(chapterUrl, it) }
    }

    // The site often stops sending an API response just before its end. Read API bodies in full here, so a
    // stalled one times out quickly and is asked for again. The reader's own calls get the same in retry.js.
    private fun retryStalls(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/api/")) return chain.proceed(request)
        val stallChain = chain.withReadTimeout(STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        var attempt = 1
        while (true) {
            try {
                val response = stallChain.proceed(request)
                val body = response.body.use { it.bytes().toResponseBody(it.contentType()) }
                return response.newBuilder().body(body).build()
            } catch (e: IOException) {
                // A stall shows up as a read timeout, or as `IOException: timeout` from an HTTP/2 stream.
                if (attempt++ == STALL_ATTEMPTS) throw e
            }
        }
    }

    private fun stitchInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PIECE_HOST) return chain.proceed(request)
        val buffer = try {
            stitchPage(request.url.fragment!!.parseAs<PieceRefDto>(), chain.call())
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // An interceptor may only throw IOException.
            throw IOException(e)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun stitchPage(ref: PieceRefDto, call: Call): Buffer {
        val current = readers[ref.chapter]
        val (origin, pieces) = current?.pages?.getOrNull(ref.index)?.pieces?.let { current.origin!! to it }
            ?: (ref.origin to ref.pieces)
        stitch(origin, pieces, call)?.let { return it }
        val fresh = rereadPages(ref.chapter, current, call)
        val freshPieces = fresh.pages!!.getOrNull(ref.index)?.pieces ?: throw IOException("Page not found")
        return stitch(fresh.origin!!, freshPieces, call) ?: throw IOException("Page expired")
    }

    // Null when the piece urls have expired.
    private fun stitch(origin: String, pieces: List<PieceDto>, call: Call): Buffer? {
        var bitmap: Bitmap? = null
        try {
            lateinit var canvas: Canvas
            for (piece in pieces) {
                // The reader cancels pages it scrolled past; stop fetching their pieces.
                if (call.isCanceled()) throw IOException("Canceled")
                val response = runBlocking { client.get(piece.url, Fetch.IMAGE.from("$origin/"), ensureSuccess = false) }
                val bytes = response.use {
                    if (it.code in EXPIRED_CODES) return null
                    if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                    it.body.bytes()
                }
                val image = ImageDecoder.decode(bytes)
                if (bitmap == null) {
                    // The page's size, from the first piece's size and its share of the page.
                    bitmap = Bitmap.createBitmap(
                        (image.width / piece.width).roundToInt(),
                        (image.height / piece.height).roundToInt(),
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    canvas = Canvas(bitmap)
                }
                val destination = Rect(
                    (piece.left * canvas.width).roundToInt(),
                    (piece.top * canvas.height).roundToInt(),
                    ((piece.left + piece.width) * canvas.width).roundToInt(),
                    ((piece.top + piece.height) * canvas.height).roundToInt(),
                )
                canvas.drawBitmap(image, null, destination, null)
                image.recycle()
            }
            return Buffer().apply { bitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, outputStream()) }
        } finally {
            bitmap?.recycle()
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/categories/series/genres", Fetch.XHR)
        .parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>().orEmpty()
        return FilterList(
            buildList {
                add(TypeFilter())
                add(StatusFilter())
                add(YearFilter())
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

    companion object {
        private const val LIST_LIMIT = 12
        private const val GENRE_LIST_LIMIT = 50
        private const val CHAPTER_LIMIT = 100
        private const val MIRROR_HOST = "procomic.net"
        private val LOCALES = setOf("ar", "en")
        private const val IMAGE_DOMAIN = ".procomic.im"
        private const val PIECE_HOST = "procomic.pieces"
        private val READER_TIMEOUT = 4.minutes
        private val EXPIRED_CODES = setOf(403, 404, 410)
        private const val TURNSTILE_HOST = "challenges.cloudflare.com"
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAACk1d7qWJi_of2qU"
        private val POLL_INTERVAL = 750.milliseconds
        private const val STALL_POLLS = 60
        private const val MAX_RELOADS = 2
        private const val STALL_TIMEOUT_SECONDS = 5
        private const val STALL_ATTEMPTS = 4
        private const val SHOW_LOCKED_KEY = "showLockedChapters"
        private val READER_SCRIPT by lazy { Procomic::class.java.getResource("/assets/reader.js")!!.readText() }
        private val RETRY_SCRIPT by lazy { Procomic::class.java.getResource("/assets/retry.js")!!.readText() }
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

        // 2x2 transparent PNG; the reader treats loaded 1x1 images as blocked.
        private val BLANK_IMAGE = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAC0lEQVR42mNgQAcAABIAAeRVjecAAAAASUVORK5CYII=", Base64.DEFAULT)
    }
}
