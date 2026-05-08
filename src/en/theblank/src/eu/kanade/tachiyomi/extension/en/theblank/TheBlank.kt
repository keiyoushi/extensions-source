package eu.kanade.tachiyomi.extension.en.theblank

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.theblank.decryption.SecretStream
import eu.kanade.tachiyomi.extension.en.theblank.decryption.State
import eu.kanade.tachiyomi.extension.en.theblank.decryption.X25519
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TheBlank :
    HttpSource(),
    ConfigurableSource {
    override val name = "The Blank"
    override val lang = "en"
    override val baseUrl = "https://theblank.net"
    private val baseHttpUrl = baseUrl.toHttpUrl()
    override val supportsLatest = true
    override val versionId = 2
    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            return@addInterceptor if (request.url.fragment == THUMBNAIL_FRAGMENT) {
                thumbnailClient.newCall(request).execute()
            } else {
                chain.proceed(request)
            }
        }
        .addInterceptor(::imageInterceptor)
        .rateLimit(1)
        .build()

    private val thumbnailClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", "https://${baseHttpUrl.host}")
        .set("Referer", "$baseUrl/")

    private var version: String? = null
    private var csrfToken: String? = null

    @Synchronized
    private fun apiRequest(
        url: HttpUrl,
        body: RequestBody? = null,
        includeXSRFToken: Boolean,
        includeCSRFToken: Boolean,
        includeVersion: Boolean,
    ): Request {
        var xsrfToken = client.cookieJar.loadForRequest(baseHttpUrl)
            .firstOrNull { it.name == "XSRF-TOKEN" }?.value

        if (
            (includeXSRFToken && xsrfToken == null) ||
            (includeCSRFToken && csrfToken == null) ||
            (includeVersion && version == null)
        ) {
            val document = client.newCall(GET(baseHttpUrl, headers)).execute()
                .also {
                    if (!it.isSuccessful) {
                        it.close()
                        throw Exception("HTTP Error ${it.code}")
                    }
                }
                .asJsoup()

            version = document.selectFirst("#app")!!
                .attr("data-page")
                .parseAs<Version>().version

            csrfToken = document.selectFirst("meta[name=csrf-token]")!!
                .attr("content")

            xsrfToken = client.cookieJar.loadForRequest(baseHttpUrl)
                .first { it.name == "XSRF-TOKEN" }.value
        }

        val headers = headersBuilder().apply {
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

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseHttpUrl.newBuilder().apply {
                addPathSegments("api/v1/search/series")
                addQueryParameter("q", query)
            }.build()

            return apiRequest(
                url,
                includeXSRFToken = true,
                includeCSRFToken = false,
                includeVersion = false,
            )
        }

        val url = baseHttpUrl.newBuilder().apply {
            addPathSegment("library")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            filters.firstInstanceOrNull<GenreFilter>()?.also { genre ->
                genre.included.also { included ->
                    if (included.isNotEmpty()) {
                        addQueryParameter("include_genres", included.joinToString(","))
                    }
                }
                genre.excluded.also { excluded ->
                    if (excluded.isNotEmpty()) {
                        addQueryParameter("exclude_genres", excluded.joinToString(","))
                    }
                }
            }
            filters.firstInstanceOrNull<TypeFilter>()?.also { type ->
                type.included.also { included ->
                    if (included.isNotEmpty()) {
                        addQueryParameter("include_types", included.joinToString(","))
                    }
                }
                type.excluded.also { excluded ->
                    if (excluded.isNotEmpty()) {
                        addQueryParameter("exclude_types", excluded.joinToString(","))
                    }
                }
            }
            filters.firstInstanceOrNull<StatusFilter>()?.also { status ->
                if (status.checked.isNotEmpty()) {
                    addQueryParameter("status", status.checked.joinToString(","))
                }
            }
            filters.firstInstance<SortFilter>().also { sort ->
                addQueryParameter("orderby", sort.sort)
                if (sort.ascending) {
                    addQueryParameter("order", "asc")
                }
            }
        }.build()

        return apiRequest(
            url,
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = false,
        )
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Text search ignores filters!"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.queryParameter("q") != null) {
            val data = response.parseAs<List<BrowseManga>>()

            return MangasPage(
                mangas = data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = false,
            )
        } else {
            val data = response.parseAs<LibraryResponse>().series

            return MangasPage(
                mangas = data.data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = data.meta.current < data.meta.last,
            )
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("serie")
            .addPathSegment(manga.url)
            .build()

        return apiRequest(
            url,
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = true,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/serie/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaResponse>().props.serie

        return SManga.create().apply {
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
                    append("Release: ", it, "\n\n")
                }
                data.alternativeName?.also {
                    append("Alternative name: ", it)
                }
            }.trim()
            genre = buildList {
                data.type?.name?.also(::add)
                data.genres.mapTo(this) { it.name }
            }.joinToString()
            status = when (data.status) {
                "ongoing", "upcoming" -> SManga.ONGOING
                "finished" -> SManga.COMPLETED
                "dropped" -> SManga.CANCELLED
                "onhold" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun createThumbnailUrl(imagePath: String?): String? {
        return baseHttpUrl.newBuilder()
            .encodedPath(imagePath ?: return null)
            .fragment(THUMBNAIL_FRAGMENT)
            .toString()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaResponse>().props.serie
        val hidePremium = preferences.getBoolean(HIDE_PREMIUM_PREF, false)

        return data.chapters
            .filter { !(it.isPremium && hidePremium) }
            .map {
                SChapter.create().apply {
                    url = baseUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("serie")
                        addPathSegment(data.slug)
                        addPathSegment("chapter")
                        addPathSegment(it.slug)
                    }.build().encodedPath
                    name = buildString {
                        if (it.isPremium) {
                            append("\uD83D\uDD12 ")
                        }
                        append(it.title)
                    }
                    date_upload = dateFormat.tryParse(it.createdAt)
                }
            }.asReversed()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PREMIUM_PREF
            title = "Hide Premium Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl${chapter.url}".toHttpUrl()

        return apiRequest(
            url,
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = true,
        )
    }

    private val secureRandom = SecureRandom()

    private class ChapterSession(
        val chapterToken: String,
        val sharedSecret: ByteArray,
        val clientPubkeyB64: String,
    )

    private val sessions = ConcurrentHashMap<String, ChapterSession>()
    private val sessionLocks = ConcurrentHashMap<String, Any>()

    private fun sessionKey(serieSlug: String, chapterSlug: String) = "tb-$serieSlug--$chapterSlug"

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

    // Refetches the chapter page to rebuild the handshake on cache miss —
    // hit after a process restart when Tachiyomi rehydrates Page objects
    // from disk but the in-memory session map is empty.
    private fun ensureSession(serieSlug: String, chapterSlug: String): ChapterSession {
        val id = sessionKey(serieSlug, chapterSlug)
        sessions[id]?.let { return it }

        val lock = sessionLocks[id] ?: Any().let { fresh ->
            sessionLocks.putIfAbsent(id, fresh) ?: fresh
        }
        synchronized(lock) {
            sessions[id]?.let { return it }

            val url = baseHttpUrl.newBuilder()
                .addPathSegment("serie").addPathSegment(serieSlug)
                .addPathSegment("chapter").addPathSegment(chapterSlug)
                .build()
            val req = apiRequest(
                url,
                includeXSRFToken = true,
                includeCSRFToken = false,
                includeVersion = true,
            )
            val props = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Could not rebuild chapter session: HTTP ${resp.code}")
                }
                resp.parseAs<PageListResponse>().props
            }

            val sess = handshakeFrom(props)
            sessions[id] = sess
            return sess
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val props = response.parseAs<PageListResponse>().props
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

        val session = ensureSession(serieSlug, chapterSlug)
        val sessionId = sessionKey(serieSlug, chapterSlug)

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

        val h = headersBuilder()
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

        // streamKey = SHA-256(sharedSecret || filename) XOR keyHint[:32]
        // — KDF recovered from pam.wasm; not a documented derivation.
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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private const val THUMBNAIL_FRAGMENT = "thumbnail"
private const val HIDE_PREMIUM_PREF = "pref_hide_premium_chapters"
private const val CHUNK_SIZE = 65536 + 17 // libsodium secretstream chunk + ABYTES
private const val PREFIX_LENGTH = 128
private const val STREAM_HEADER_LENGTH = 24
