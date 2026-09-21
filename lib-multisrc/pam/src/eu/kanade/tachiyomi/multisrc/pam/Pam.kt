package eu.kanade.tachiyomi.multisrc.pam

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.i18n.Intl
import keiyoushi.lib.secretstream.SecretStream
import keiyoushi.lib.secretstream.State
import keiyoushi.lib.secretstream.X25519
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

abstract class Pam :
    HttpSource(),
    ConfigurableSource {

    protected val baseHttpUrl = baseUrl.toHttpUrl()

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    override val client = network.client.newBuilder()
        .addInterceptor(::imageInterceptor)
        .rateLimit(1, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT }
        .build()

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
            .firstOrNull { it.name == "XSRF-TOKEN" }?.let { URLDecoder.decode(it.value, "UTF-8") }

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
                .first { it.name == "XSRF-TOKEN" }.let { URLDecoder.decode(it.value, "UTF-8") }
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

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilters)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilters)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    protected abstract val popularFilters: FilterList
    protected abstract val latestFilters: FilterList

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

        return apiRequest(
            url,
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = false,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.queryParameter("q") != null) {
            val data = response.parseAs<SearchResponse>().data

            return MangasPage(
                mangas = data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = false,
            )
        } else {
            val data = response.parseAs<LibraryResponse>().series

            return MangasPage(
                mangas = data.data.map { it.toSManga(::createThumbnailUrl) },
                hasNextPage = data.meta?.let { it.current < it.last } ?: false,
            )
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$baseUrl/serie/${manga.url}".toHttpUrl()

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
    }

    protected open fun createThumbnailUrl(imagePath: String?): String? {
        if (imagePath == null) return null
        return "$baseUrl$imagePath#$THUMBNAIL_FRAGMENT"
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaResponse>().props.serie
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
                    date_upload = it.createdAt.substringBefore(".").let { dateStr ->
                        dateFormat.tryParse(dateStr)
                    }
                }
            }.asReversed()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PREMIUM_PREF
            title = intl["pref_hide_premium_title"]
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

    /**
     * Baked into the reader's WASM signer, and rebuilt per site: the attestation endpoint
     * answers a wrong secret with an endless `refresh` rather than an error, so a site whose
     * values are not known here can never mint a chapter token. See IMPLEMENT.md for how to
     * recover them from a site's signer.
     */
    protected abstract val readerSecret: ByteArray

    protected abstract val kdfDomain: String

    /** Where [readerSecret] sits relative to the bytes signed by the attestation and manifest HMACs. */
    protected abstract fun signedPayload(payload: ByteArray): ByteArray

    /** Field order of the manifest signature payload. */
    protected abstract fun manifestPayload(uid: String, version: Int, ts: Long, nonce: String): String

    /** Order in which [readerSecret], the ECDH secret and the KDF info feed each page-key round. */
    protected abstract fun contentKeyMaterial(sharedSecret: ByteArray, info: ByteArray): List<ByteArray>

    /** How many times the page-key digest is folded over itself before unmasking the hint. */
    protected abstract val contentKeyRounds: Int

    private val secureRandom = SecureRandom()

    private class ChapterSession(
        val chapterToken: String,
        val sharedSecret: ByteArray,
        val clientPubkeyB64: String,
        /** Reader v2 only: input keying material for this chapter's encrypted pages. */
        val contentKey: ByteArray? = null,
    )

    private class ChapterState(
        val session: ChapterSession,
        val manifest: ManifestResponse?,
    )

    private val sessions = ConcurrentHashMap<String, ChapterSession>()
    private val sessionLocks = ConcurrentHashMap<String, Any>()

    private fun sessionKey(serieSlug: String, chapterSlug: String) = "${name.take(3).lowercase()}-$serieSlug--$chapterSlug"

    private fun openChapter(body: PageListResponse): ChapterState {
        val props = body.props
        val serverPub = Base64.decode(props.serverPubkey, Base64.DEFAULT)
        require(serverPub.size == 32) { "server pubkey must be 32 bytes" }

        val priv = ByteArray(32).also(secureRandom::nextBytes)
        val clientPub = X25519.publicKey(priv)
        val shared = X25519.scalarMult(priv, serverPub)
        priv.fill(0)
        val clientPubkeyB64 = Base64.encodeToString(clientPub, Base64.NO_WRAP)

        if (!props.readerV2) {
            val token = props.chapterToken ?: throw IOException("Chapter token missing")
            return ChapterState(ChapterSession(token, shared, clientPubkeyB64), null)
        }

        val token = attest(body, clientPubkeyB64)
        val manifest = requestManifest(props.data.uid, token, clientPubkeyB64)

        return ChapterState(
            ChapterSession(token, shared, clientPubkeyB64, contentKey(manifest, shared)),
            manifest,
        )
    }

    /**
     * Reader v2 mints the chapter token from an attestation exchange instead of shipping it
     * in the page props. The first exchange is always answered with `refresh`, which retires
     * the challenge embedded in the page: only the challenge handed back by the partial
     * reload gets a token.
     */
    private fun attest(body: PageListResponse, clientPubkeyB64: String): String {
        val attestation = body.props.attestation ?: throw IOException("Missing attestation challenge")
        val device = deviceReport(attestation.webglSeed)
        var challenge = attestation.challenge

        repeat(ATTESTATION_ATTEMPTS) {
            val request = AttestationRequest(
                c = challenge,
                v = hmacSha256Hex(device, challenge.toByteArray()),
                sp = hmacSha256Hex(challenge, signedPayload("$device\u0000$clientPubkeyB64".toByteArray())),
                d = device,
                pk = clientPubkeyB64,
            )
            val minted = client.newCall(
                apiRequest(
                    "$baseUrl/api/v1/t".toHttpUrl(),
                    request.toJsonRequestBody(),
                    includeXSRFToken = true,
                    includeCSRFToken = false,
                    includeVersion = false,
                ),
            ).execute().parseAs<AttestationResponse>().ct

            if (minted != null) return minted

            val reloaded = client.newCall(attestationReloadRequest(body)).execute()
                .parseAs<AttestationReload>().props
            reloaded.chapterToken?.also { return it }
            challenge = reloaded.attestation?.challenge ?: throw IOException("Attestation refused")
        }

        throw IOException("Attestation refused")
    }

    private fun attestationReloadRequest(body: PageListResponse): Request {
        val url = "$baseUrl/serie/${body.props.data.serie.slug}/chapter/${body.props.data.slug}".toHttpUrl()
        val headers = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("X-Inertia", "true")
            .set("X-Inertia-Version", body.version)
            .set("X-Inertia-Partial-Component", body.component)
            .set("X-Inertia-Partial-Data", "chapter_token,attestation")
            .build()

        return GET(url, headers)
    }

    /**
     * Stands in for the browser fingerprint the site collects through canvas and WebGL. The
     * server only checks that it matches the HMAC we send alongside it, so a fixed plausible
     * report is enough.
     */
    private fun deviceReport(webglSeed: String): String = """{"webdriver":false,"webgl_vendor":"Qualcomm","webgl_renderer":"Adreno (TM) 730",""" +
        """"webgl_proof":"${sha256Hex("webgl_proof")}","gl_sig":"8192|1|1|23",""" +
        """"device_memory":null,"hardware_concurrency":8,"effective_type":null,"save_data":false,""" +
        """"screen_width":1080,"screen_height":2340,"viewport_width":1080,"viewport_height":2130,""" +
        """"device_pixel_ratio":2.75,"max_touch_points":5,"has_touch":true,""" +
        """"locale":"en-US","timezone":"America/New_York","platform":"Linux armv8l",""" +
        """"canvas_hash":"${sha256Hex(webglSeed)}"}"""

    private fun requestManifest(uid: String, chapterToken: String, clientPubkeyB64: String): ManifestResponse {
        val ts = System.currentTimeMillis() / 1000
        val nonce = hexNonce()
        val request = ManifestRequest(
            v = MANIFEST_VERSION,
            c = uid,
            t = chapterToken,
            ts = ts,
            n = nonce,
            s = hmacSha256Hex(
                chapterToken,
                signedPayload(manifestPayload(uid, MANIFEST_VERSION, ts, nonce).toByteArray()),
            ),
        )

        val call = apiRequest(
            "$baseUrl/api/v1/m".toHttpUrl(),
            request.toJsonRequestBody(),
            includeXSRFToken = true,
            includeCSRFToken = false,
            includeVersion = false,
        ).newBuilder().header("X-Client-Pubkey", clientPubkeyB64).build()

        return client.newCall(call).execute().parseAs<ManifestResponse>()
    }

    /**
     * The manifest hint is the page key masked with a digest chain over the ECDH secret, so
     * it is worthless to any other session.
     */
    private fun contentKey(manifest: ManifestResponse, sharedSecret: ByteArray): ByteArray {
        val segments = manifest.base.split('/').filter(String::isNotEmpty)
        require(segments.size >= 4 && segments[0] == "p") { "unexpected manifest base: ${manifest.base}" }

        val info = "$kdfDomain|${segments[1]}|${segments[2]}".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        var folded = ByteArray(0)
        val material = contentKeyMaterial(sharedSecret, info)
        repeat(contentKeyRounds) {
            digest.update(folded)
            material.forEach(digest::update)
            folded = digest.digest()
        }

        val hint = Base64.decode(manifest.hint, Base64.DEFAULT)
        return ByteArray(32) { i -> (folded[i].toInt() xor hint[i].toInt()).toByte() }
    }

    private fun ensureSession(serieSlug: String, chapterSlug: String): ChapterSession {
        val id = sessionKey(serieSlug, chapterSlug)
        sessions[id]?.let { return it }

        val lock = sessionLocks[id] ?: Any().let { fresh ->
            sessionLocks.putIfAbsent(id, fresh) ?: fresh
        }
        synchronized(lock) {
            sessions[id]?.let { return it }

            val url = "$baseUrl/serie/$serieSlug/chapter/$chapterSlug".toHttpUrl()
            val req = apiRequest(
                url,
                includeXSRFToken = true,
                includeCSRFToken = false,
                includeVersion = true,
            )
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Could not rebuild chapter session: HTTP ${resp.code}")
                }
                resp.parseAs<PageListResponse>()
            }

            val sess = openChapter(body).session
            sessions[id] = sess
            return sess
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.parseAs<PageListResponse>()
        val props = body.props
        val id = sessionKey(props.data.serie.slug, props.data.slug)
        val state = openChapter(body)
        sessions[id] = state.session

        val manifest = state.manifest
            ?: return (1..props.pageCount).map { idx ->
                Page(
                    index = idx - 1,
                    url = "$id#$idx",
                    imageUrl = "$baseUrl/serie/${props.data.serie.slug}/chapter/${props.data.slug}/page/$idx#$id",
                )
            }

        val variant = manifest.variants.maxOrNull()?.let { "-$it" }.orEmpty()

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

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    override fun imageRequest(page: Page): Request {
        val parsed = page.imageUrl!!.toHttpUrl()
        if (parsed.encodedPath.endsWith(".ece")) {
            return GET(parsed, headers)
        }

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

        if (session.contentKey != null) {
            if (!response.isSuccessful) return response

            return response.newBuilder()
                .body(
                    decryptEce(response.body.bytes(), session.contentKey)
                        .toResponseBody("image/webp".toMediaType()),
                )
                .build()
        }

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

    /** RFC 8188 `aes128gcm`, the container reader v2 serves its pages in. */
    private fun decryptEce(payload: ByteArray, ikm: ByteArray): ByteArray {
        require(payload.size >= 21) { "ece: payload shorter than the header" }

        val salt = payload.copyOfRange(0, 16)
        val recordSize = ByteBuffer.wrap(payload, 16, 4).int
        var pos = 21 + (payload[20].toInt() and 0xFF)
        require(recordSize >= 18 && pos < payload.size) { "ece: malformed header" }

        val key = SecretKeySpec(hkdf(ikm, salt, ECE_KEY_INFO, 16), "AES")
        val nonce = hkdf(ikm, salt, ECE_NONCE_INFO, 12)
        val out = Buffer()
        var sequence = 0

        while (pos < payload.size) {
            val record = payload.copyOfRange(pos, minOf(pos + recordSize, payload.size))
            pos += record.size
            require(record.size >= 18) { "ece: record $sequence too short" }

            val iv = nonce.copyOf()
            var counter = sequence
            for (i in 11 downTo 0) {
                if (counter == 0) break
                iv[i] = (iv[i].toInt() xor (counter and 0xFF)).toByte()
                counter = counter ushr 8
            }

            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                doFinal(record)
            }

            // Records are zero-padded up to a delimiter byte: 2 on the last one, 1 elsewhere.
            var last = plain.size - 1
            while (last >= 0 && plain[last].toInt() == 0) last--
            val isFinal = pos >= payload.size
            require(last >= 0 && plain[last].toInt() == if (isFinal) 2 else 1) {
                "ece: record $sequence has the wrong delimiter"
            }

            out.write(plain, 0, last)
            sequence++
        }

        return out.readByteArray()
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(salt, "HmacSHA256"))
        }.doFinal(ikm)

        return Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(prk, "HmacSHA256"))
            update(info)
            update(1)
        }.doFinal().copyOf(length)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private const val THUMBNAIL_FRAGMENT = "thumbnail"
private const val ATTESTATION_ATTEMPTS = 3
private const val MANIFEST_VERSION = 2
private val ECE_KEY_INFO = "Content-Encoding: aes128gcm\u0000".toByteArray()
private val ECE_NONCE_INFO = "Content-Encoding: nonce\u0000".toByteArray()
private const val HIDE_PREMIUM_PREF = "pref_hide_premium_chapters"
private const val CHUNK_SIZE = 65536 + 17 // libsodium secretstream chunk + ABYTES
private const val PREFIX_LENGTH = 192
private const val STREAM_HEADER_LENGTH = 24
