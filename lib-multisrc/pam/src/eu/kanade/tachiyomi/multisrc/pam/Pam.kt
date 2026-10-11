package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import android.webkit.WebResourceResponse
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.ece.Ece
import keiyoushi.lib.i18n.Intl
import keiyoushi.lib.secretstream.SecretStream
import keiyoushi.lib.secretstream.State
import keiyoushi.lib.secretstream.X25519
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

abstract class Pam :
    KeiSource(),
    ConfigurableSource {

    protected val baseHttpUrl get() = baseUrl.toHttpUrl()

    private val preferences by getPreferencesLazy()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::imageInterceptor)
        // The reader's static bundle is scraped for its signer and doesn't need throttling.
        rateLimit(1, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT && !it.encodedPath.startsWith("/build/") }
    }

    private var version: String? = null
    private var csrfToken: String? = null
    private val tokenMutex = Mutex()

    private suspend fun apiHeaders(
        includeXSRFToken: Boolean,
        includeCSRFToken: Boolean,
        includeVersion: Boolean,
    ): Headers = tokenMutex.withLock {
        var xsrfToken = client.cookieJar.loadForRequest(baseHttpUrl)
            .firstOrNull { it.name == "XSRF-TOKEN" }?.let { URLDecoder.decode(it.value, "UTF-8") }

        if (
            (includeXSRFToken && xsrfToken == null) ||
            (includeCSRFToken && csrfToken == null) ||
            (includeVersion && version == null)
        ) {
            val document = client.get(baseHttpUrl).asJsoup()

            version = document.selectFirst("#app")!!
                .attr("data-page")
                .parseAs<Version>().version

            csrfToken = document.selectFirst("meta[name=csrf-token]")!!
                .attr("content")

            xsrfToken = client.cookieJar.loadForRequest(baseHttpUrl)
                .first { it.name == "XSRF-TOKEN" }.let { URLDecoder.decode(it.value, "UTF-8") }
        }

        headersBuilder().apply {
            set("Accept", "application/json")
            set("X-Requested-With", "XMLHttpRequest")
            if (includeVersion) {
                set("X-Inertia", "true")
                set("X-Inertia-Version", version!!)
            }
            if (includeXSRFToken) {
                set("X-XSRF-TOKEN", xsrfToken!!)
            }
            if (includeCSRFToken) {
                set("X-CSRF-TOKEN", csrfToken!!)
            }
        }.build()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", popularFilters)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", latestFilters)

    protected abstract val popularFilters: FilterList
    protected abstract val latestFilters: FilterList

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val apiHeaders = apiHeaders(
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = false,
        )

        if (query.isNotEmpty()) {
            val url = baseHttpUrl.newBuilder().apply {
                addPathSegments("api/v1/search/series")
                addQueryParameter("q", query)
            }.build()

            val data = client.get(url, apiHeaders).parseAs<SearchResponse>().data

            return MangasPage(
                mangas = data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = false,
            )
        }

        val url = baseHttpUrl.newBuilder().apply {
            addPathSegment("library")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            filters.filterIsInstance<TriStateGroupFilter>().forEach { group ->
                when (group.name) {
                    "Genres", "Genres/Thèmes" -> {
                        group.included.takeIf { it.isNotEmpty() }?.also { addQueryParameter("include_genres", it.joinToString(",")) }
                        group.excluded.takeIf { it.isNotEmpty() }?.also { addQueryParameter("exclude_genres", it.joinToString(",")) }
                    }
                    "Types" -> {
                        group.included.takeIf { it.isNotEmpty() }?.also { addQueryParameter("include_types", it.joinToString(",")) }
                        group.excluded.takeIf { it.isNotEmpty() }?.also { addQueryParameter("exclude_types", it.joinToString(",")) }
                    }
                }
            }

            filters.firstInstanceOrNull<CheckBoxGroup>()?.also { status ->
                if (status.checked.isNotEmpty()) {
                    addQueryParameter("status", status.checked.joinToString(","))
                }
            }
            filters.firstInstanceOrNull<SortFilter>()?.also { sort ->
                addQueryParameter("orderby", sort.sort)
                if (sort.ascending) {
                    addQueryParameter("order", "asc")
                }
            }
        }.build()

        val data = client.get(url, apiHeaders).parseAs<LibraryResponse>().series

        return MangasPage(
            mangas = data.data.map { it.toSManga(::createThumbnailUrl) },
            hasNextPage = data.meta?.let { it.current < it.last } ?: false,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/serie/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val apiHeaders = apiHeaders(
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = true,
        )
        val data = client.get(getMangaUrl(manga), apiHeaders).parseAs<MangaResponse>().props.serie

        // Some sites leave the chapters out of the page and serve them from a paged API instead.
        val sourceChapters = when {
            data.chapters != null -> data.chapters
            fetchChapters -> fetchChapterList(data.uid)
            else -> null
        }

        return SMangaUpdate(
            manga = mangaDetailsParse(data),
            chapters = sourceChapters?.let { chapterListParse(data, it) } ?: chapters,
        )
    }

    private fun mangaDetailsParse(data: MangaResponse.Props.Manga): SManga = SManga.create().apply {
        url = data.slug
        title = data.title
        thumbnail_url = createThumbnailUrl(data.image)
        author = data.author
        artist = data.artist
        description = buildString {
            data.description?.also {
                append(it.trim(), "\n\n")
            }
            data.releaseYear?.also {
                append(intl["release_year"], ": ", it, "\n\n")
            }
            data.alternativeName?.also {
                append(intl["alternative_names"], ": ", it)
            }
        }.trim()
        genre = buildList {
            data.type?.name?.also(::add)
            data.genres.mapTo(this) { it.name }
        }.joinToString()
        status = when (data.status?.lowercase()) {
            "ongoing", "upcoming" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "dropped" -> SManga.CANCELLED
            "onhold" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    protected open fun createThumbnailUrl(imagePath: String?): String? {
        if (imagePath == null) return null
        return "$baseUrl$imagePath#$THUMBNAIL_FRAGMENT"
    }

    private fun chapterListParse(data: MangaResponse.Props.Manga, chapters: List<MangaResponse.Props.Chapter>): List<SChapter> {
        val hidePremium = preferences.getBoolean(HIDE_PREMIUM_PREF, false)

        return chapters
            .filter { !(it.isPremium && hidePremium) }
            .map {
                SChapter.create().apply {
                    url = "/serie/${data.slug}/chapter/${it.slug}"
                    name = buildString {
                        if (it.isPremium) {
                            append("\uD83D\uDD12 ")
                        }
                        append(it.title)
                    }
                    date_upload = it.createdAt.substringBefore(".").let { dateStr ->
                        dateFormat.tryParseDateTime(dateStr)
                    }
                }
            }.asReversed()
    }

    /** Oldest first, like the inline list. */
    private suspend fun fetchChapterList(uid: String): List<MangaResponse.Props.Chapter> {
        val now = System.currentTimeMillis()
        val isFree = { freeAt: String -> dateFormat.tryParseDateTime(freeAt.substringBefore(".")) in 1..now }
        val apiHeaders = apiHeaders(includeXSRFToken = true, includeCSRFToken = false, includeVersion = false)

        return buildList {
            var page = 1
            do {
                val url = "$baseUrl/api/v1/series/$uid/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("sort", "asc")
                    .build()
                val response = client.get(url, apiHeaders).parseAs<ChapterListResponse>()

                response.items.mapTo(this) { it.toChapter(isFree) }
            } while (page++ < response.lastPage)
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
        .withZone(TimeZone.getTimeZone("UTC").toZoneId())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PREMIUM_PREF
            title = intl["pref_hide_premium_title"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    @Volatile
    private var readerModule: ReaderModule? = null
    private val readerModuleMutex = Mutex()

    private suspend fun readerModule(): ReaderModule = readerModuleMutex.withLock {
        readerModule ?: client.fetchReaderModule(baseUrl, headers).also { readerModule = it }
    }

    private val readerScript by lazy { javaClass.getResource("/assets/reader.js")!!.readText() }

    // One handshake WebView at a time, also during library updates and downloads.
    private val readerMutex = Mutex()

    private val secureRandom = SecureRandom()

    private class ChapterSession(
        val chapterToken: String,
        val sharedSecret: ByteArray,
        val clientPubkeyB64: String,
        /** Reader v2 only: input keying material for this chapter's encrypted pages. */
        val contentKey: ByteArray? = null,
        /** Reader v2 only: the manifest's page ticket. */
        val pageTicket: String? = null,
    )

    private class ChapterState(
        val session: ChapterSession,
        val manifest: ManifestResponse?,
    )

    private val sessions = ConcurrentHashMap<String, ChapterSession>()
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

    private fun sessionKey(serieSlug: String, chapterSlug: String) = "${name.take(3).lowercase()}-$serieSlug--$chapterSlug"

    private suspend fun openChapter(body: PageListResponse): ChapterState {
        val props = body.props
        val serverPub = Base64.decode(props.serverPubkey, Base64.DEFAULT)
        require(serverPub.size == 32) { "server pubkey must be 32 bytes" }

        val priv = ByteArray(32).also(secureRandom::nextBytes)
        val clientPub = X25519.publicKey(priv)
        val shared = X25519.scalarMult(priv, serverPub)
        val clientPubkeyB64 = Base64.encodeToString(clientPub, Base64.NO_WRAP)

        try {
            if (!props.readerV2) {
                val token = props.chapterToken ?: throw IOException("Chapter token missing")
                return ChapterState(ChapterSession(token, shared, clientPubkeyB64), null)
            }

            return openReader(body, priv, clientPubkeyB64, shared)
        } finally {
            // The site's signer needs the private key for its own key exchange, so it is wiped only afterwards.
            priv.fill(0)
        }
    }

    /**
     * Reader v2 runs in the site's own code: its attestation reports the WebView's fingerprint and
     * mints the chapter token, and its signer signs the manifest request and unmasks the page key.
     */
    private suspend fun openReader(
        body: PageListResponse,
        privateKey: ByteArray,
        clientPubkeyB64: String,
        sharedSecret: ByteArray,
    ): ChapterState = readerMutex.withLock {
        val props = body.props
        val reader = readerModule()
        val csrfToken = apiHeaders(includeXSRFToken = false, includeCSRFToken = true, includeVersion = false)["X-CSRF-TOKEN"]!!
        val bridge = "pam${hexNonce(8)}"
        val input = ReaderInput(
            bridge = bridge,
            csrfToken = csrfToken,
            sharedUrl = reader.sharedUrl,
            glueUrl = reader.glueUrl,
            exports = reader.exports,
            attestation = props.attestation ?: throw IOException("Missing attestation challenge"),
            serverPubkey = props.serverPubkey,
            privateKey = Base64.encodeToString(privateKey, Base64.NO_WRAP),
            clientPubkey = clientPubkeyB64,
            uid = props.data.uid,
            manifestVersion = MANIFEST_VERSION,
            chapterUrl = "$baseUrl/serie/${props.data.serie.slug}/chapter/${props.data.slug}",
            reloadHeaders = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "X-Inertia" to "true",
                "X-Inertia-Version" to body.version,
                "X-Inertia-Partial-Component" to body.component,
                "X-Inertia-Partial-Data" to "chapter_token,attestation",
            ),
        ).toJsonString()

        val script = "($readerScript)($input)"
        val result = try {
            runWebView<ReaderResult>(timeout = 60.seconds) {
                userAgent = headers["User-Agent"]!!
                blockImages = true
                // Keep the WebView on the site's own host.
                interceptRequest { request ->
                    val requestHost = request.url.host
                    if (requestHost == null || requestHost == baseHttpUrl.host) null else blockedResponse()
                }
                jsBridge(bridge) { resolve(it.parseAs()) }
                var started = false
                onPageFinished {
                    if (!started) {
                        started = true
                        evaluateJs(script)
                    }
                }
                loadData(baseUrl, "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }

        result.error?.let { error ->
            // Most likely the site rebuilt its reader; pick the new one up on the next attempt.
            readerModule = null
            throw IOException(error)
        }

        // Anything the page hands back is used in page URLs and headers.
        val token = result.token?.takeIf(String::isNotBlank)
        val manifest = result.manifest?.takeIf { MANIFEST_BASE_REGEX.matches(it.base) && it.pt?.let(PAGE_TICKET_REGEX::matches) != false }
        val contentKey = result.contentKey?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }?.takeIf { it.size == 32 }
        if (token == null || manifest == null || contentKey == null) throw IOException("Unexpected reader result")

        ChapterState(ChapterSession(token, sharedSecret, clientPubkeyB64, contentKey, manifest.pt), manifest)
    }

    private fun blockedResponse() = WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))

    private suspend fun ensureSession(serieSlug: String, chapterSlug: String): ChapterSession {
        val id = sessionKey(serieSlug, chapterSlug)
        sessions[id]?.let { return it }

        return sessionMutexes.getOrPut(id) { Mutex() }.withLock {
            sessions[id]?.let { return@withLock it }

            val apiHeaders = apiHeaders(
                includeXSRFToken = true,
                includeCSRFToken = false,
                includeVersion = true,
            )
            val body = client.get("$baseUrl/serie/$serieSlug/chapter/$chapterSlug", apiHeaders)
                .parseAs<PageListResponse>()

            openChapter(body).session.also { sessions[id] = it }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val apiHeaders = apiHeaders(
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = true,
        )
        val body = client.get("$baseUrl${chapter.url}", apiHeaders).parseAs<PageListResponse>()
        val props = body.props
        val id = sessionKey(props.data.serie.slug, props.data.slug)
        val state = openChapter(body)
        sessions[id] = state.session

        // Reader v1 pages are signed right before they are loaded, see getImageUrl.
        val manifest = state.manifest
            ?: return (1..props.pageCount).map { idx ->
                Page(
                    index = idx - 1,
                    url = "/serie/${props.data.serie.slug}/chapter/${props.data.slug}/page/$idx",
                )
            }

        // Some sites list oversized variants they never serve.
        val variant = manifest.variants.minByOrNull { abs(it - MAX_VARIANT_WIDTH) }?.let { "-$it" }.orEmpty()

        return (1..manifest.count).map { idx ->
            Page(
                index = idx - 1,
                url = "$id#$idx",
                imageUrl = "$baseUrl${manifest.base}$idx$variant.ece#$id",
            )
        }
    }

    private fun hexNonce(byteCount: Int = 16): String {
        val b = ByteArray(byteCount).also(secureRandom::nextBytes)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(key: String, msg: String): String = hmacSha256Hex(key, msg.toByteArray(Charsets.US_ASCII))

    private fun hmacSha256Hex(key: String, msg: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        }
        return mac.doFinal(msg).joinToString("") { "%02x".format(it) }
    }

    override suspend fun getImageUrl(page: Page): String {
        val seg = "$baseUrl${page.url}".toHttpUrl().pathSegments
        require(seg.size >= 6 && seg[0] == "serie" && seg[2] == "chapter" && seg[4] == "page") {
            "unexpected page URL shape: ${page.url}"
        }
        val serieSlug = seg[1]
        val chapterSlug = seg[3]
        val pageIndex = seg[5].toInt()

        val session = ensureSession(serieSlug, chapterSlug)
        val sessionId = sessionKey(serieSlug, chapterSlug)

        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonce = hexNonce()
        val sig = hmacSha256Hex(session.chapterToken, "$pageIndex$ts$nonce")

        return baseHttpUrl.newBuilder()
            .addPathSegment("serie").addPathSegment(serieSlug)
            .addPathSegment("chapter").addPathSegment(chapterSlug)
            .addPathSegment("page").addPathSegment(pageIndex.toString())
            .addQueryParameter("token", session.chapterToken)
            .addQueryParameter("ts", ts)
            .addQueryParameter("nonce", nonce)
            .addQueryParameter("sig", sig)
            .fragment(sessionId)
            .build()
            .toString()
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val session = request.url.fragment?.let(sessions::get) ?: return chain.proceed(request)

        if (session.contentKey != null) {
            val response = chain.proceed(
                request.newBuilder().apply { session.pageTicket?.let { header("X-Pt", it) } }.build(),
            )
            if (!response.isSuccessful) return response

            return response.newBuilder()
                .body(
                    Ece.decrypt(response.body.bytes(), session.contentKey)
                        .toResponseBody("image/webp".toMediaType()),
                )
                .build()
        }

        val response = chain.proceed(
            request.newBuilder().header("X-Client-Pubkey", session.clientPubkeyB64).build(),
        )
        val pageNameRaw = response.header("X-Page-Name") ?: return response
        val keyHintB64 = response.header("X-Key-Hint") ?: return response
        val keyHint = Base64.decode(keyHintB64, Base64.DEFAULT)
        require(keyHint.size >= 32) { "X-Key-Hint must decode to >= 32 bytes" }

        val streamKey = run {
            val sha = MessageDigest.getInstance("SHA-256").run {
                update(session.sharedSecret)
                update(pageNameRaw.toByteArray(Charsets.UTF_8))
                digest()
            }
            ByteArray(32) { i -> (sha[i].toInt() xor keyHint[i].toInt()).toByte() }
        }

        val networkSource = response.body.source()
        networkSource.skip(PREFIX_LENGTH.toLong())
        val ssHeader = networkSource.readByteArray(STREAM_HEADER_LENGTH.toLong())

        val decryptedSource = object : okio.Source {
            private val secretStream = SecretStream()
            private val state = State().apply {
                secretStream.initPull(this, ssHeader, streamKey)
            }
            private val decryptedBuffer = Buffer()
            private var isFinished = false

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (decryptedBuffer.size == 0L) {
                    if (isFinished) return -1

                    networkSource.request(CHUNK_SIZE.toLong())

                    val chunkSize = minOf(CHUNK_SIZE.toLong(), networkSource.buffer.size)

                    if (chunkSize == 0L) {
                        isFinished = true
                        return -1
                    }

                    val encryptedData = Buffer().apply {
                        networkSource.read(this, chunkSize)
                    }.readByteArray()

                    val result = secretStream.pull(state, encryptedData, encryptedData.size)
                        ?: throw IOException("Decryption failed")

                    decryptedBuffer.write(result.message)

                    if (result.tag.toInt() == SecretStream.TAG_FINAL) {
                        isFinished = true
                    }
                }

                return decryptedBuffer.read(sink, byteCount)
            }

            override fun timeout(): Timeout = networkSource.timeout()

            override fun close() = networkSource.close()
        }.buffer()

        return response.newBuilder()
            .body(decryptedSource.asResponseBody("image/jpg".toMediaType()))
            .build()
    }
}

private const val THUMBNAIL_FRAGMENT = "thumbnail"
private const val MANIFEST_VERSION = 2
private val MANIFEST_BASE_REGEX = Regex("""/p/[\w-]+/\d+/[\w-]+/""")
private val PAGE_TICKET_REGEX = Regex("""[!-~]+""")
private const val MAX_VARIANT_WIDTH = 2160
private const val HIDE_PREMIUM_PREF = "pref_hide_premium_chapters"
private const val CHUNK_SIZE = 65536 + 17 // libsodium secretstream chunk + ABYTES
private const val PREFIX_LENGTH = 192
private const val STREAM_HEADER_LENGTH = 24
