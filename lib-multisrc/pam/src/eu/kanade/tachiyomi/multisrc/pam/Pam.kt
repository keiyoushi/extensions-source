package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
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
import java.io.IOException
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
        rateLimit(1, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", popularFilters)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", latestFilters)

    protected abstract val popularFilters: FilterList
    protected abstract val latestFilters: FilterList

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val search = baseHttpUrl.newBuilder().apply {
                addPathSegments("api/v1/search/series")
                addQueryParameter("q", query)
            }.build()

            val data = sitePages.load("$baseUrl/library", SiteRequest(fetch = search.toString())).fetched().parseAs<SearchResponse>().data

            return MangasPage(
                mangas = data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = false,
            )
        }

        val url = baseHttpUrl.newBuilder().apply {
            addPathSegment("library")

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

        // Later pages are loaded by the library page itself, as it does when scrolled.
        val data = if (page > 1) {
            val next = url.newBuilder().addQueryParameter("page", page.toString()).build()
            sitePages.load(url.toString(), SiteRequest(fetch = next.toString())).fetched().parseAs<LibraryResponse>().series
        } else {
            sitePages.load(url.toString()).page.parseAs<LibraryPage>().props.series
        }

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
        val sitePage = sitePages.load(getMangaUrl(manga), SiteRequest(chapters = fetchChapters))
        val data = sitePage.page.parseAs<MangaResponse>().props.serie

        val sourceChapters = data.chapters ?: sitePage.chapters?.let(::apiChapters)

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
    private fun apiChapters(items: List<ChapterListItem>): List<MangaResponse.Props.Chapter> {
        val now = System.currentTimeMillis()
        val isFree = { freeAt: String -> dateFormat.tryParseDateTime(freeAt.substringBefore(".")) in 1..now }
        return items.map { it.toChapter(isFree) }
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

    private val sitePages = SitePages(baseUrl, { client }) { headers["User-Agent"]!! }

    private val secureRandom = SecureRandom()

    private sealed interface ChapterSession

    /** Reader v1: pages signed with the chapter token, streams keyed by the key exchange. */
    private class SignedSession(
        val chapterToken: String,
        val sharedSecret: ByteArray,
        val clientPubkeyB64: String,
    ) : ChapterSession

    /** Reader v2: encrypted pages under the manifest's page ticket. */
    private class EncryptedSession(
        val contentKey: ByteArray,
        val siteHeaders: Headers,
    ) : ChapterSession

    private val sessions = ConcurrentHashMap<String, ChapterSession>()
    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

    private fun sessionKey(serieSlug: String, chapterSlug: String) = "${name.take(3).lowercase()}-$serieSlug--$chapterSlug"

    private fun openSignedSession(props: PageListResponse.Props): SignedSession {
        val serverPub = Base64.decode(props.serverPubkey, Base64.DEFAULT)
        require(serverPub.size == 32) { "server pubkey must be 32 bytes" }

        val priv = ByteArray(32).also(secureRandom::nextBytes)
        try {
            val clientPub = X25519.publicKey(priv)
            val shared = X25519.scalarMult(priv, serverPub)
            val token = props.chapterToken ?: throw IOException("Chapter token missing")
            return SignedSession(token, shared, Base64.encodeToString(clientPub, Base64.NO_WRAP))
        } finally {
            priv.fill(0)
        }
    }

    /** The reader v2 session and manifest from a chapter page loaded with the reader. */
    private fun encryptedSession(site: SitePage): Pair<EncryptedSession, ReaderResult.Manifest> {
        val result = site.reader!!

        // Used in page URLs and headers
        val manifest = result.manifest.takeIf { MANIFEST_BASE_REGEX.matches(it.base) && it.count > 0 }
        val contentKey = runCatching { Base64.decode(result.contentKey, Base64.DEFAULT) }.getOrNull()?.takeIf { it.size == 32 }
        if (manifest == null || contentKey == null) throw IOException("Unexpected reader result")
        // The site's X- headers on page requests, less the WebView's X-Requested-With
        val siteHeaders = Headers.Builder().apply {
            site.pageHeaders
                .filter { (name, value) ->
                    SITE_HEADER_REGEX.matches(name) &&
                        !name.equals("X-Requested-With", ignoreCase = true) &&
                        HEADER_VALUE_REGEX.matches(value)
                }
                .forEach { (name, value) -> add(name, value) }
        }.build()

        return EncryptedSession(contentKey, siteHeaders) to manifest
    }

    private suspend fun ensureSession(serieSlug: String, chapterSlug: String): SignedSession {
        val id = sessionKey(serieSlug, chapterSlug)
        (sessions[id] as? SignedSession)?.let { return it }

        return sessionMutexes.getOrPut(id) { Mutex() }.withLock {
            (sessions[id] as? SignedSession)?.let { return@withLock it }

            val props = sitePages.load("$baseUrl/serie/$serieSlug/chapter/$chapterSlug").page.parseAs<PageListResponse>().props

            openSignedSession(props).also { sessions[id] = it }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val site = sitePages.load("$baseUrl${chapter.url}", reader = true)
        val props = site.page.parseAs<PageListResponse>().props
        val id = sessionKey(props.data.serie.slug, props.data.slug)

        // Reader v1 pages are signed right before they are loaded, see getImageUrl.
        if (!props.readerV2) {
            sessions[id] = openSignedSession(props)
            return (1..props.pageCount).map { idx ->
                Page(
                    index = idx - 1,
                    url = "/serie/${props.data.serie.slug}/chapter/${props.data.slug}/page/$idx",
                )
            }
        }

        val (session, manifest) = encryptedSession(site)
        sessions[id] = session

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

        return when (session) {
            is EncryptedSession -> encryptedPage(chain, session)
            is SignedSession -> signedPage(chain, session)
        }
    }

    private fun encryptedPage(chain: Interceptor.Chain, session: EncryptedSession): Response {
        val request = chain.request()
        val response = chain.proceed(
            request.newBuilder().apply { session.siteHeaders.forEach { (name, value) -> header(name, value) } }.build(),
        )
        if (!response.isSuccessful) return response

        return response.newBuilder()
            .body(
                Ece.decrypt(response.body.bytes(), session.contentKey)
                    .toResponseBody("image/webp".toMediaType()),
            )
            .build()
    }

    private fun signedPage(chain: Interceptor.Chain, session: SignedSession): Response {
        val request = chain.request()
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
private val MANIFEST_BASE_REGEX = Regex("""/p/[\w-]+/\d+/[\w-]+/""")
private val HEADER_VALUE_REGEX = Regex("""[!-~]+""")
private val SITE_HEADER_REGEX = Regex("""X-[\w-]+""", RegexOption.IGNORE_CASE)
private const val MAX_VARIANT_WIDTH = 2160
private const val HIDE_PREMIUM_PREF = "pref_hide_premium_chapters"
private const val CHUNK_SIZE = 65536 + 17 // libsodium secretstream chunk + ABYTES
private const val PREFIX_LENGTH = 192
private const val STREAM_HEADER_LENGTH = 24
