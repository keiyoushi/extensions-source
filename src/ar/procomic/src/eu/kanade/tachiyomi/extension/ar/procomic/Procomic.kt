package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.LruCache
import android.webkit.WebResourceRequest
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
import keiyoushi.lib.cronet.cronet
import keiyoushi.lib.imagedecoder.ImageDecoder
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.WebMessageReplyProxy
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Source
abstract class Procomic :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy {
        // The device keys of the app-API versions.
        edit().remove("device_id").remove("device_key").remove("device_public_key").remove("attest_at").apply()
    }

    private val siteHost get() = baseUrl.toHttpUrl().host
    private val mirrorHost = MIRROR_URL.toHttpUrl().host
    private val hosts get() = listOf(siteHost, mirrorHost)

    // The host whose reader last asked for a chapter's media: the site reads on one host, sends the other's chapters
    // to the series page, and has swapped the two before.
    @Volatile private var learnedReaderHost: String? = null
    private val readerHost get() = learnedReaderHost ?: siteHost

    override fun OkHttpClient.Builder.configureClient() = this
        // The listing API 403s requests closer than about 5 s apart.
        .rateLimit(1, 5.seconds) { it.encodedPath == "/api/public/content" }
        .rateLimit(3) { it.host in hosts }
        // Sensitive series render the reader only with the site's safe browsing switched off.
        .addCookie("safe_browsing" to "off")
        .addCookie({ mirrorHost }, "safe_browsing" to "off")
        .addInterceptor(::retryStalls)
        .addInterceptor(::pageInterceptor)
        .addInterceptor(::keepSharedImage)
        .cronet()

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
        if (url.host !in hosts) return null
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
        .host(readerHost)
        .addPathSegment(lang)
        .addPathSegment("${manga.memo["slug"]!!.string}-${manga.url}")
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .host(readerHost)
        .addPathSegment(lang)
        .addPathSegment("chapter")
        .addPathSegment("${chapter.memo["slug"]!!.string}-${chapter.memo["number"]!!.string}-${chapter.url}")
        .build()
        .toString()

    // The site's reader hands out pages only to a browser running it, so each chapter is opened in the reader in a
    // WebView and read like a person would: every page scrolls the reader to it, and the images the reader loads
    // are shared with the app.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        return List(session(chapterUrl).pageCount()) { index ->
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(PAGE_HOST)
                .fragment(PageRefDto(chapterUrl, index).toJsonString())
                .build()
            Page(index, imageUrl = url.toString())
        }
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessions = object : LruCache<String, ReaderSession>(MAX_SESSIONS) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: ReaderSession, newValue: ReaderSession?) {
            oldValue.close()
        }
    }

    @Synchronized
    private fun session(chapterUrl: String): ReaderSession = sessions[chapterUrl]?.takeUnless { it.closed }
        ?: ReaderSession(chapterUrl).also { sessions.put(chapterUrl, it) }

    // A chapter open in the site's reader, closed after a while without page requests.
    private inner class ReaderSession(private val chapterUrl: String) {
        private val started = CompletableDeferred<Unit>()

        // The main frame's script world, from its latest ready message: the reader looks for scripts of ours on its page.
        @Volatile
        private var reader: WebMessageReplyProxy? = null
        private val images = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()

        private var reloads = 0
        private var reloaded: Double? = null

        @Volatile
        private var lastUsed = TimeSource.Monotonic.markNow()

        @Volatile
        var closed = false
            private set

        @Volatile
        private var failure: Throwable? = null

        private val triedHosts = mutableListOf<String>()

        private lateinit var scope: WebViewScope<Unit>

        @Volatile
        private var checkSince: TimeMark? = null

        private val job = sessionScope.launch {
            try {
                runWebView<Unit>(SESSION_TIMEOUT) { open() }
            } catch (e: Throwable) {
                failure = e.takeUnless { it is CancellationException || it is WebViewTimeoutException }
                started.completeExceptionally(e)
            } finally {
                closed = true
            }
        }

        fun close() {
            closed = true
            job.cancel()
        }

        private fun WebViewScope<Unit>.open() {
            scope = this
            val origins = hosts.map { "https://$it" }.toSet()
            webMessageBridge(BRIDGE, origins, WORLD) { message, proxy ->
                if (message == READY && proxy.isMainFrame == true) reader = proxy
            }
            addJavaScriptOnEvent("$BRIDGE.postMessage('$READY')", worldName = WORLD, allowedOriginRules = origins)
            interceptRequest { request ->
                val url = request.url
                if (url.host in hosts && url.path.orEmpty().startsWith(DEFERRED_MEDIA_PATH)) {
                    learnedReaderHost = url.host
                    checkSince = null
                    hideFromUser()
                }
                share(request)
            }
            poll(IDLE_TIMEOUT / 4) {
                if (lastUsed.elapsedNow() > IDLE_TIMEOUT) resolve(Unit)
            }
            onPageFinished { landed(it) }
            loadUrl(tryHost(readerHost))
            started.complete(Unit)
        }

        private fun tryHost(host: String): String {
            triedHosts += host
            return chapterUrl.toHttpUrl().newBuilder().host(host).build().toString()
        }

        // The other host, for a chapter the site sent off the chapter route.
        private fun WebViewScope<Unit>.landed(url: String) {
            val page = url.toHttpUrlOrNull()?.takeIf { it.host in hosts } ?: return
            if ("chapter" in page.pathSegments) return
            // A late event from a host already left.
            if (page.host in triedHosts && page.host != triedHosts.last()) return
            val next = hosts.firstOrNull { it !in triedHosts }
                ?: return reject(IOException("The site did not open the chapter, open it in WebView"))
            evaluateJs("location.replace(${tryHost(next).toJsonString()})")
        }

        // The check passes by itself on most connections; others get a checkbox, which only a visible page can take.
        private suspend fun awaitCheck() = withContext(Dispatchers.Main) {
            val since = checkSince ?: TimeSource.Monotonic.markNow().also { checkSince = it }
            if (since.elapsedNow() >= CHECK_GRACE && !scope.showToUser(TURNSTILE_AREA)) {
                scope.reject(IOException("Open the chapter in the reader to pass the site's security check"))
            }
        }

        // Sends the reader's page images through the source's client, which keeps a copy for the app.
        private fun WebViewScope<Unit>.share(request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (request.method != "GET" || url.host?.endsWith(IMAGE_DOMAIN) != true || IMAGE_PATHS.none(url.path.orEmpty()::startsWith)) return null
            // A failed load (a cancelled deferred) starts over.
            val image = images.compute(url.toString()) { _, old -> old?.takeUnless { it.isCancelled } ?: CompletableDeferred() }!!
            return proxyRequest(request, client) { tag(SharedImage::class.java, SharedImage(image)) }
        }

        suspend fun pageCount(): Int = waitFor(null) { slot(0).count }

        suspend fun page(index: Int, call: Call): Buffer {
            val slot = waitFor(call) { slot(index).takeIf { it.url != null || it.pieces != null } }
            val urls = listOfNotNull(slot.url) + slot.pieces.orEmpty().map { it.url }
            if (slot.origin!!.toHttpUrl().host !in hosts || urls.any { !it.toHttpUrl().host.endsWith(IMAGE_DOMAIN) }) {
                throw IOException("Unexpected page, open the chapter in WebView")
            }
            val page = slot.url?.let { Buffer().write(image(it, slot.origin)) }
                ?: stitch(slot.pieces!!) { image(it, slot.origin) }
            urls.forEach(images::remove)
            return page
        }

        private suspend fun slot(index: Int): SlotDto {
            lastUsed = TimeSource.Monotonic.markNow()
            if (closed) throw failure?.let { it as? IOException ?: IOException(it) } ?: IOException("The reader was closed, open the chapter again")
            started.await()
            val reader = reader ?: return SlotDto()
            val script = "(${javaClass.getResource("/assets/slot.js")!!.readText()})($index)"
            val result = withTimeoutOrNull(SCRIPT_TIMEOUT) {
                suspendCancellableCoroutine { continuation -> reader.evaluateJs(script) { continuation.resume(it) } }
            }
            val slot = result?.parseAs<SlotDto?>() ?: return SlotDto()
            if (slot.check) {
                awaitCheck()
                return SlotDto()
            }
            val failed = slot.error ?: return slot
            synchronized(this) {
                // Every poller sees the same failed page until the reload lands.
                if (failed == reloaded) return SlotDto()
                reloaded = failed
                if (reloads++ >= MAX_RELOADS) {
                    close()
                    throw IOException("The site is not responding, try again later")
                }
            }
            reader.evaluateJs("location.reload()")
            return SlotDto()
        }

        private suspend fun <T : Any> waitFor(call: Call?, block: suspend () -> T?): T = withTimeoutOrNull(PAGE_TIMEOUT) {
            var result = block()
            while (result == null) {
                if (call?.isCanceled() == true) throw IOException("Canceled")
                delay(POLL_INTERVAL)
                result = block()
            }
            result
        } ?: throw IOException("No pages found, open the chapter in WebView")

        // The copy of an image the reader loaded, or the image itself when the reader's load failed or came from its
        // own cache.
        private suspend fun image(url: String, origin: String): ByteArray {
            val bytes = images[url]?.let { shared -> runCatching { withTimeoutOrNull(IMAGE_TIMEOUT) { shared.await() } }.getOrNull() }
            return bytes ?: client.get(url, Fetch.IMAGE.from("$origin/")).use { it.body.bytes() }
        }
    }

    // The site often stops sending an API response just before its end. Read API bodies in full here, so a
    // stalled one times out quickly and is asked for again.
    private fun retryStalls(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host !in hosts || !request.url.encodedPath.startsWith("/api/")) return chain.proceed(request)
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

    private fun keepSharedImage(chain: Interceptor.Chain): Response {
        val image = chain.request().tag(SharedImage::class.java)?.image ?: return chain.proceed(chain.request())
        try {
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                image.completeExceptionally(HttpException(response.code))
                return response
            }
            image.complete(response.peekBody(Long.MAX_VALUE).bytes())
            return response
        } catch (e: IOException) {
            image.completeExceptionally(e)
            throw e
        }
    }

    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)
        val ref = request.url.fragment!!.parseAs<PageRefDto>()
        val buffer = try {
            runBlocking { session(ref.chapter).page(ref.index, chain.call()) }
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
            .body(buffer.asResponseBody(null, buffer.size))
            .build()
    }

    private suspend fun stitch(pieces: List<PieceDto>, image: suspend (String) -> ByteArray): Buffer {
        var bitmap: Bitmap? = null
        try {
            lateinit var canvas: Canvas
            for (piece in pieces) {
                val decoded = ImageDecoder.decode(image(piece.url))
                if (bitmap == null) {
                    // The page's size, from the first piece's size and its share of the page.
                    bitmap = Bitmap.createBitmap(
                        (decoded.width / piece.width).roundToInt(),
                        (decoded.height / piece.height).roundToInt(),
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
                canvas.drawBitmap(decoded, null, destination, null)
                decoded.recycle()
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
        private const val MIRROR_URL = "https://procomic.net"
        private const val DEFERRED_MEDIA_PATH = "/chapter-deferred-media/"
        private val LOCALES = setOf("ar", "en")
        private const val IMAGE_DOMAIN = ".procomic.im"

        // Paths of the reader's page images, older chapters use /api/cdn-image.
        private val IMAGE_PATHS = listOf("/i/", "/chapters/", "/api/cdn-image")

        // Kept from the versions that stitched pieces here, so page lists cached by the app still load.
        private const val PAGE_HOST = "procomic.pieces"
        private const val BRIDGE = "procomicReader"
        private const val WORLD = "procomic"
        private const val READY = "ready"
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAACk1d7qWJi_of2qU"
        private val POLL_INTERVAL = 750.milliseconds
        private const val MAX_RELOADS = 2
        private const val MAX_SESSIONS = 2
        private val SESSION_TIMEOUT = 2.hours
        private val IDLE_TIMEOUT = 5.minutes
        private val PAGE_TIMEOUT = 2.minutes
        private val IMAGE_TIMEOUT = 5.seconds
        private val SCRIPT_TIMEOUT = 10.seconds

        // How long the security check may take to pass on its own.
        private val CHECK_GRACE = 30.seconds

        private const val STALL_TIMEOUT_SECONDS = 5
        private const val STALL_ATTEMPTS = 4
        private const val SHOW_LOCKED_KEY = "showLockedChapters"
    }
}

// A page image the reader loads, kept for the app.
private class SharedImage(val image: CompletableDeferred<ByteArray>)
