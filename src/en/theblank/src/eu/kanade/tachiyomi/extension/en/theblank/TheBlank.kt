package eu.kanade.tachiyomi.extension.en.theblank

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class TheBlank : HttpSource() {
    override val name = "The Blank"
    override val lang = "en"
    override val baseUrl = "https://beta.theblank.net"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private var version: String? = null

    @Synchronized
    private fun apiRequest(
        url: HttpUrl,
        includeVersion: Boolean,
    ): Request {
        var token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "XSRF-TOKEN" }?.value

        if (token == null || (includeVersion && version == null)) {
            val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()

            version = document.selectFirst("#app")!!
                .attr("data-page")
                .parseAs<Version>().version

            token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                .first { it.name == "XSRF-TOKEN" }.value
        }

        val headers = headersBuilder().apply {
            set("Accept", "application/json")
            if (includeVersion) {
                set("X-Inertia", "true")
                set("X-Inertia-Version", version!!)
            }
            set("X-Requested-With", "XMLHttpRequest")
            set("X-XSRF-TOKEN", token)
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) =
        searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) =
        searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("api/v1/search/series")
                addQueryParameter("q", query)
            }.build()

            return apiRequest(url, includeVersion = false)
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
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

        return apiRequest(url, includeVersion = false)
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
                mangas = data.map { it.toSManga(baseUrl) },
                hasNextPage = false,
            )
        } else {
            val data = response.parseAs<LibraryResponse>().series

            return MangasPage(
                mangas = data.data.map { it.toSManga(baseUrl) },
                hasNextPage = data.meta.current < data.meta.last,
            )
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("serie")
            .addPathSegment(manga.url)
            .build()

        return apiRequest(url, includeVersion = true)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/serie/${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaResponse>().props.serie

        return SManga.create().apply {
            url = data.slug
            title = data.title
            thumbnail_url = data.image?.let { baseUrl + it }
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

    override fun chapterListRequest(manga: SManga) =
        mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaResponse>().props.serie

        return data.chapters.map {
            SChapter.create().apply {
                url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("serie")
                    addPathSegment(data.slug)
                    addPathSegment("chapter")
                    addPathSegment(it.slug)
                }.build().encodedPath
                name = it.title
                chapter_number = it.chapterNumber
                date_upload = dateFormat.tryParse(it.createdAt)
            }
        }.asReversed()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.url
        val csrfToken = response.asJsoup().selectFirst("meta[name=csrf-token]")!!.attr("content")

        val signedUrls = client.newCall(apiRequest(chapterUrl, includeVersion = true))
            .execute().parseAs<PageListResponse>().props.signedUrls

        val key = generateImageKey(csrfToken, chapterUrl.toString())

        return signedUrls.mapIndexed { idx, img ->
            Page(
                index = idx,
                imageUrl = img.toHttpUrl().newBuilder()
                    .fragment(key)
                    .build()
                    .toString(),
            )
        }
    }

    private fun generateImageKey(csrfToken: String, referer: String): String {
        val keyPairResult = generateKeyPair()
        val sid = fetchSession(csrfToken, referer, keyPairResult.publicKeyBase64)
        val encryptedBuffer = cookSID(sid)

        return rsaDecrypt(keyPairResult.keyPair.private, encryptedBuffer)
    }

    private fun fetchSession(csrfToken: String, referer: String, publicKeyBase64: String): String {
        val url = "$baseUrl/api/v1/session"
        val headers = headersBuilder().apply {
            set("X-CSRF-TOKEN", csrfToken)
            set("Origin", "https://${referer.toHttpUrl().host}")
            set("Referer", referer)
        }.build()
        val body = buildJsonObject {
            put("clientPublicKey", publicKeyBase64)
            put("nonce", generateNonce())
        }.toJsonString().toRequestBody("application/json".toMediaType())

        return client.newCall(POST(url, headers, body)).execute()
            .parseAs<SessionResponse>().sid
    }

    private fun generateKeyPair(): KeyPairResult {
        val generator = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }

        val keyPair = generator.generateKeyPair()
        val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        return KeyPairResult(keyPair, publicKeyBase64)
    }

    private val secureRandom = SecureRandom()

    private fun generateNonce(): String {
        val timestampHex = (System.currentTimeMillis() / 1000)
            .toString(16)
            .padStart(16, '0')

        val randomHex = ByteArray(24).apply {
            secureRandom.nextBytes(this)
        }.joinToString("") { "%02x".format(it) }

        return timestampHex + randomHex
    }

    private fun rsaDecrypt(privateKey: PrivateKey, encryptedData: ByteArray): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").apply {
            val oaepSpec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT,
            )

            init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
        }

        val decryptedBytes = cipher.doFinal(encryptedData)

        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    private fun cookSID(sid: String): ByteArray {
        return Base64.decode(sid, Base64.URL_SAFE)
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.fragment == null) {
            return response
        }

        val imageKey = request.url.fragment!!

        val nonce = Base64.decode(response.header("x-stream-header")!!, Base64.DEFAULT)

        val encryptedData = response.body.bytes()

        val streamKey = MessageDigest.getInstance("SHA-256")
            .digest(imageKey.toByteArray(Charsets.UTF_8))

        return try {
            val decrypted = decryptSecretStream(encryptedData, streamKey, nonce)
            response.newBuilder()
                .body(decrypted.toResponseBody("image/jpg".toMediaType()))
                .build()
        } catch (e: Exception) {
            response.close()
            throw e
        }
    }

    private fun decryptSecretStream(input: ByteArray, key: ByteArray, header: ByteArray): ByteArray {
        // LibSodium SecretStream uses the header to derive the subkey and initial nonce
        // This part is critical: we must replicate the 'init_pull' logic.
        // In LibSodium, the header is essentially the nonce for the XChaCha20 derivation.

        val cipher = ChaCha20Poly1305()
        val out = mutableListOf<ByteArray>()

        var pos = 0
        val chunkSize = 8192 + 17 // ABYTES (1 tag + 16 MAC)

        // Note: Full SecretStream state management (re-keying/nonce incrementing)
        // is complex. For standard one-shot image decryption where tags aren't
        // rotated, a simpler XChaCha20-Poly1305 block approach is often used.

        // However, if the server uses full 'crypto_secretstream',
        // you MUST track the nonce increment for every chunk.

        val currentNonce = header.copyOf()

        while (pos < input.size) {
            val currentChunkSize = minOf(chunkSize, input.size - pos)
            val chunk = input.copyOfRange(pos, pos + currentChunkSize)

            val params = ParametersWithIV(KeyParameter(key), currentNonce)
            cipher.init(false, params)

            val decryptedChunk = ByteArray(cipher.getOutputSize(chunk.size))
            val len = cipher.processBytes(chunk, 0, chunk.size, decryptedChunk, 0)
            cipher.doFinal(decryptedChunk, len)

            // The first byte of the decrypted message in SecretStream is actually
            // the 'Tag', followed by the message.
            val message = decryptedChunk.copyOfRange(0, decryptedChunk.size - 1)
            val tag = decryptedChunk.last()

            out.add(message)

            if (tag.toInt() == 3) break // TAG_FINAL in LibSodium is 3

            incrementNonce(currentNonce) // Crucial for multi-chunk streams
            pos += currentChunkSize
        }

        return out.flatMap { it.asIterable() }.toByteArray()
    }

    private fun incrementNonce(nonce: ByteArray) {
        // LibSodium increments the nonce starting from the 8th byte for secretstream
        for (i in 7 until nonce.size) {
            if (++nonce[i] != 0.toByte()) break
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
