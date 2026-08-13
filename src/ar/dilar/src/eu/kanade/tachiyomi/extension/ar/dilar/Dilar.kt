package eu.kanade.tachiyomi.extension.ar.dilar

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Dilar : KeiSource() {
    override val supportsLatest = false

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        add("X-DH-Pub", clientPubB64)
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/series?page=$page")
        val data = response.parseAs<SeriesListDto>()
        val entries = data.series
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, data.hasNextPage)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = SearchRequestDto(query, page).toJsonRequestBody()
        val response = client.post("$baseUrl/api/search/filter", body)
        val data = response.parseAs<SearchListDto>()
        val entries = data.rows.filterNot { it.isNovel() }
            .map { it.toSManga() }

        return MangasPage(entries, data.hasNextPage)
    }

    // Details & Chapters

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (!fetchDetails) return@async manga
            client.get("$baseUrl/api/series/${manga.getMangaId()}")
                .parseAs<SeriesDto>()
                .toSManga()
        }

        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            val response = client.get("$baseUrl/api/series/${manga.getMangaId()}/chapters")
            response.parseAs<ChapterListDto>().chapters.flatMap { chapter ->
                chapter.releases.map { it.toSChapter(chapter, manga.url) }
            }
        }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    // Pages

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url.substringBeforeLast("#")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/chapters/${chapter.url.substringAfterLast("#")}")
        val encrypted = response.parseAs<EncryptedResponseDto>()

        require(encrypted.v == 1) { "Unsupported encryption protocol version: ${encrypted.v}" }

        val data = decrypt(encrypted).parseAs<PageListDto>()
        return data.pages.sortedBy { it.order }
            .mapIndexed { index, page ->
                Page(index, imageUrl = "$baseUrl/uploads/releases/${data.storageKey}/hq/${page.url}")
            }
    }

    // ECIES decrypt

    private val ecKeyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(CURVE_NAME))
    }.generateKeyPair()

    private val clientPubRaw: ByteArray = pointToRaw(ecKeyPair.public as ECPublicKey)

    private val clientPubB64: String =
        Base64.encodeToString(clientPubRaw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decrypt(data: EncryptedResponseDto): String {
        val serverPubRaw = Base64.decode(data.epk, Base64.URL_SAFE)
        val serverPubKey = rawToPoint(serverPubRaw)

        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(ecKeyPair.private)
            doPhase(serverPubKey, true)
        }.generateSecret()

        val key = hkdfSha256(
            ikm = sharedSecret,
            salt = clientPubRaw + serverPubRaw,
            info = "dilar.response.ecies.v1|${data.e}".toByteArray(),
            length = 32,
        )

        val iv = Base64.decode(data.iv, Base64.URL_SAFE)
        val ct = Base64.decode(data.ct, Base64.URL_SAFE)
        val tag = Base64.decode(data.tag, Base64.URL_SAFE)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }

        return cipher.doFinal(ct + tag).toString(Charsets.UTF_8)
    }

    private fun pointToRaw(publicKey: ECPublicKey): ByteArray {
        val x = publicKey.w.affineX.toFixedBytes(32)
        val y = publicKey.w.affineY.toFixedBytes(32)
        return byteArrayOf(0x04) + x + y
    }

    private fun rawToPoint(raw: ByteArray): ECPublicKey {
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "Invalid P-256 raw public key" }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))

        val curveParams = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec(CURVE_NAME))
        }.getParameterSpec(ECParameterSpec::class.java)

        val spec = ECPublicKeySpec(ECPoint(x, y), curveParams)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    private fun BigInteger.toFixedBytes(length: Int): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size == length -> raw
            raw.size > length -> raw.copyOfRange(raw.size - length, raw.size) // drop sign byte
            else -> ByteArray(length - raw.size) + raw
        }
    }

    // HKDF-SHA256

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(key, "HmacSHA256"))
    }.doFinal(data)

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm)
        val okm = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (okm.size() < length) {
            t = hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            okm.write(t)
            counter++
        }
        return okm.toByteArray().copyOf(length)
    }

    // common

    private fun SManga.getMangaId(): String = this.url.substringBeforeLast("/")

    fun createThumbnail(mangaId: String, cover: String): String {
        val thumbnail = "large_${cover.substringBeforeLast(".")}.webp"

        return "$baseUrl/uploads/manga/cover/$mangaId/$thumbnail"
    }

    companion object {
        private const val CURVE_NAME = "secp256r1"
    }
}
