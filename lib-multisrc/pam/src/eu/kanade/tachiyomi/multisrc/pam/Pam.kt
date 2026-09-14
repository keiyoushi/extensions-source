package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
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
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

abstract class Pam :
    KeiSource(),
    ConfigurableSource {

    protected val baseHttpUrl = baseUrl.toHttpUrl()

    private val preferences by getPreferencesLazy()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::imageInterceptor)
        rateLimit(1, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT }
    }

    private var version: String? = null
    private var csrfToken: String? = null

    private val apiMutex = Mutex()

    private suspend fun apiRequest(
        url: HttpUrl,
        body: RequestBody? = null,
        includeXSRFToken: Boolean,
        includeCSRFToken: Boolean,
        includeVersion: Boolean,
    ): Request = apiMutex.withLock {
        var xsrfToken = client.cookieJar.loadForRequest(baseHttpUrl)
            .firstOrNull { it.name == "XSRF-TOKEN" }?.value

        if (
            (includeXSRFToken && xsrfToken == null) ||
            (includeCSRFToken && csrfToken == null) ||
            (includeVersion && version == null)
        ) {
            val bootstrap = client.get(baseHttpUrl, ensureSuccess = false)
            ensureSuccess(bootstrap)
            val document = bootstrap.asJsoup()

            version = document.selectFirst("#app")!!
                .attr("data-page")
                .parseAs<Version>().version

            csrfToken = document.selectFirst("meta[name=csrf-token]")!!
                .attr("content")

            xsrfToken = client.cookieJar.loadForRequest(baseHttpUrl)
                .first { it.name == "XSRF-TOKEN" }.value
        }

        val headers = headers.newBuilder().apply {
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

        return if (body != null) {
            POST(url.toString(), headers, body)
        } else {
            GET(url, headers)
        }
    }

    private fun ensureSuccess(response: Response) {
        if (response.isSuccessful) return
        response.close()
        if (response.code == 403 || response.code == 503) {
            throw Exception("Solve captcha in WebView and retry")
        }
        throw IOException("HTTP ${response.code}")
    }

    private suspend inline fun <T> withVersionRetry(
        response: Response,
        includeXSRFToken: Boolean,
        includeCSRFToken: Boolean,
        includeVersion: Boolean,
        parse: (Response) -> T,
    ): T {
        if (response.code == 409 && includeVersion) {
            apiMutex.withLock {
                version = null
                csrfToken = null
            }
            response.close()
            val retry = apiRequest(
                response.request.url,
                includeXSRFToken = includeXSRFToken,
                includeCSRFToken = includeCSRFToken,
                includeVersion = includeVersion,
            )
            client.newCall(retry).await().use { fresh ->
                ensureSuccess(fresh)
                return parse(fresh)
            }
        }
        ensureSuccess(response)
        return parse(response)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = searchLibrary(page, popularFilters)

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchLibrary(page, latestFilters)

    protected abstract val popularFilters: FilterList
    protected abstract val latestFilters: FilterList

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = baseHttpUrl.newBuilder().apply {
                addPathSegments("api/v1/search/series")
                addQueryParameter("q", query)
            }.build()

            val request = apiRequest(
                url,
                includeXSRFToken = true,
                includeCSRFToken = false,
                includeVersion = false,
            )
            val response = client.newCall(request).await()
            val data = withVersionRetry(response, includeXSRFToken = true, includeCSRFToken = false, includeVersion = false) {
                it.parseAs<SearchResponse>().data
            }

            return MangasPage(
                mangas = data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = false,
            )
        }

        return searchLibrary(page, filters)
    }

    private suspend fun searchLibrary(page: Int, filters: FilterList): MangasPage {
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

        val request = apiRequest(
            url,
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = false,
        )
        val response = client.newCall(request).await()
        val data = withVersionRetry(response, includeXSRFToken = true, includeCSRFToken = false, includeVersion = false) {
            it.parseAs<LibraryResponse>().series
        }

        return MangasPage(
            mangas = data.data.map { it.toSManga(::createThumbnailUrl) },
            hasNextPage = data.meta?.let { it.current < it.last } ?: false,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/serie/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHttpUrl.host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "serie") return null
        val manga = SManga.create().apply { this.url = segments[1] }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val request = apiRequest(
            "$baseUrl/serie/${manga.url}".toHttpUrl(),
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = true,
        )
        val response = client.newCall(request).await()
        val data = withVersionRetry(response, includeXSRFToken = true, includeCSRFToken = false, includeVersion = true) {
            it.parseAs<MangaResponse>().props.serie
        }

        return SMangaUpdate(parseDetails(data), parseChapters(data))
    }

    private fun parseDetails(data: MangaResponse.Props.Manga): SManga = SManga.create().apply {
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

    private fun parseChapters(data: MangaResponse.Props.Manga): List<SChapter> {
        val hidePremium = preferences.getBoolean(HIDE_PREMIUM_PREF, false)

        return data.chapters
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
                    date_upload = dateFormat.tryParseDateTime(it.createdAt.substringBefore("."), ZoneId.of("UTC"))
                }
            }.asReversed()
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PREMIUM_PREF
            title = intl["pref_hide_premium_title"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val request = apiRequest(
            "$baseUrl${chapter.url}".toHttpUrl(),
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = true,
        )
        val response = client.newCall(request).await()
        val props = withVersionRetry(response, includeXSRFToken = true, includeCSRFToken = false, includeVersion = true) {
            it.parseAs<PageListResponse>().props
        }
        val sess = handshakeFrom(props)
        val id = sessionKey(props.data.serie.slug, props.data.slug)
        sessions[id] = sess

        return (1..props.pageCount).map { idx ->
            Page(
                index = idx - 1,
                url = "$id#$idx",
                imageUrl = "$baseUrl/serie/${props.data.serie.slug}/chapter/${props.data.slug}/page/$idx#$id",
            )
        }
    }

    private val secureRandom = SecureRandom()

    private class ChapterSession(
        val chapterToken: String,
        val sharedSecret: ByteArray,
        val clientPubkeyB64: String,
    )

    private val sessions = ConcurrentHashMap<String, ChapterSession>()

    private fun sessionKey(serieSlug: String, chapterSlug: String) = "${name.take(3).lowercase()}-$serieSlug--$chapterSlug"

    private fun handshakeFrom(props: PageListResponse.Props): ChapterSession {
        val serverPub = Base64.decode(props.serverPubkey, Base64.DEFAULT)
        require(serverPub.size == 32) { "server pubkey must be 32 bytes" }

        val priv = ByteArray(32).also(secureRandom::nextBytes)
        val clientPub = X25519.publicKey(priv)
        val shared = X25519.scalarMult(priv, serverPub)
        priv.fill(0)

        return ChapterSession(
            chapterToken = props.chapterToken,
            sharedSecret = shared,
            clientPubkeyB64 = Base64.encodeToString(clientPub, Base64.NO_WRAP),
        )
    }

    private fun hexNonce(byteCount: Int = 16): String {
        val b = ByteArray(byteCount).also(secureRandom::nextBytes)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(key: String, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        }
        return mac.doFinal(msg.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
    }

    override fun imageRequest(page: Page): Request {
        val parsed = page.imageUrl!!.toHttpUrl()
        val seg = parsed.pathSegments
        require(seg.size >= 6 && seg[0] == "serie" && seg[2] == "chapter" && seg[4] == "page") {
            "unexpected page URL shape: ${parsed.encodedPath}"
        }
        val serieSlug = seg[1]
        val chapterSlug = seg[3]
        val pageIndex = seg[5].toInt()

        val sessionId = sessionKey(serieSlug, chapterSlug)
        val session = sessions[sessionId] ?: throw IOException("Missing chapter session, reopen the chapter")

        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonce = hexNonce()
        val sig = hmacSha256Hex(session.chapterToken, "$pageIndex$ts$nonce")

        val url = baseHttpUrl.newBuilder()
            .addPathSegment("serie").addPathSegment(serieSlug)
            .addPathSegment("chapter").addPathSegment(chapterSlug)
            .addPathSegment("page").addPathSegment(pageIndex.toString())
            .addQueryParameter("token", session.chapterToken)
            .addQueryParameter("ts", ts)
            .addQueryParameter("nonce", nonce)
            .addQueryParameter("sig", sig)
            .fragment(sessionId)
            .build()

        val h = headers.newBuilder()
            .set("X-Client-Pubkey", session.clientPubkeyB64)
            .build()

        return GET(url, h)
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val sessionId = request.url.fragment ?: return response
        val session = sessions[sessionId] ?: return response

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
private const val HIDE_PREMIUM_PREF = "pref_hide_premium_chapters"
private const val CHUNK_SIZE = 65536 + 17 // libsodium secretstream chunk + ABYTES
private const val PREFIX_LENGTH = 192
private const val STREAM_HEADER_LENGTH = 24
