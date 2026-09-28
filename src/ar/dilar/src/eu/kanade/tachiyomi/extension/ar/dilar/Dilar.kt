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
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
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
    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        add("X-DH-Pub", clientPubB64)
        add("X-Crypto-Caps", "1,2,3,4,5,6,7,8,9,10,11,12")
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/rankings")
        val data = response.parseAs<RankingsDto>()
        val entries = data.topSeries
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/series?page=$page")
        val data = response.parseAs<SeriesListDto>()
        val entries = data.series
            .filterNot { it.isNovel() }
            .map { it.toSManga() }
        return MangasPage(entries, data.hasNextPage)
    }

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
        val chapterUrl = "$baseUrl/api/chapters/${chapter.url.substringAfterLast("#")}"
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val unlock = client.post("$chapterUrl/unlock/free", body).parseAs<UnlockDto>()
        val chapterHeaders = headers.newBuilder().set("X-Unlock-Free-Chapter", unlock.token).build()
        val encrypted = client.get(chapterUrl, chapterHeaders).parseAs<EncryptedResponseDto>()

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
        val iv = Base64.decode(data.iv, Base64.URL_SAFE)
        val ct = Base64.decode(data.ct, Base64.URL_SAFE)
        val tag = Base64.decode(data.tag, Base64.URL_SAFE)

        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(ecKeyPair.private)
            doPhase(serverPubKey, true)
        }.generateSecret()

        val spec = when (data.v) {
            1 -> CipherSpec(
                clientPubRaw + serverPubRaw,
                "dilar.response.ecies.v1|${data.e}".toByteArray(),
            )

            2 -> CipherSpec(
                serverPubRaw + clientPubRaw,
                "dilar.response.ecies.v2|${data.e}".toByteArray(),
            )

            3 -> CipherSpec(
                sha256(serverPubRaw + clientPubRaw),
                "dilar.response.ecies.v3|${data.e}".toByteArray(),
            )

            4 -> CipherSpec(
                sha256(clientPubRaw + serverPubRaw + iv),
                "dilar.response.ecies.v4|${data.e}|${data.iv}"
                    .toByteArray(),
            )

            5 -> CipherSpec(
                hmac(
                    key = iv,
                    data = serverPubRaw + clientPubRaw,
                ),
                "dilar.response.ecies.v5|${data.e}".toByteArray(),
            )

            6 -> CipherSpec(
                sha256(sha256(clientPubRaw) + sha256(serverPubRaw) + iv),
                "dilar.response.ecies.v6|${data.e}|${data.iv}".toByteArray(),
            )

            7 -> CipherSpec(
                hkdf(
                    ikm = iv,
                    salt = serverPubRaw,
                    info = "dilar.response.ecies.v7.salt".toByteArray(),
                    length = 32,
                ),
                "dilar.response.ecies.v7|${data.e}".toByteArray(),
            )

            8 -> CipherSpec(
                sha256(
                    joinBytes(
                        u16(clientPubRaw.size),
                        clientPubRaw,
                        u16(serverPubRaw.size),
                        serverPubRaw,
                        u16(iv.size),
                        iv,
                    ),
                ),
                "dilar.response.ecies.v8|${data.e}|${iv.toHex()}".toByteArray(),
            )

            9 -> CipherSpec(
                hmac(
                    key = iv,
                    data = joinBytes(
                        u16(serverPubRaw.size),
                        serverPubRaw,
                        u16(clientPubRaw.size),
                        clientPubRaw,
                    ),
                    algorithm = "HmacSHA512",
                ).copyOfRange(0, 32),
                "dilar.response.ecies.v9|${data.e}|${sha256(iv).toHex().take(16)}".toByteArray(),
            )

            10 -> CipherSpec(
                sha512(
                    joinBytes(
                        u16(clientPubRaw.size),
                        clientPubRaw,
                        u16(serverPubRaw.size),
                        serverPubRaw,
                        u16(iv.size),
                        iv,
                    ),
                ),
                "dilar.response.ecies.v10|${data.e}|${sha512(iv).toHex().take(24)}".toByteArray(),
                hash = "HmacSHA512",
            )

            11 -> CipherSpec(
                hmac(
                    key = serverPubRaw,
                    data = joinBytes(u16(iv.size), iv, u16(clientPubRaw.size), clientPubRaw),
                    algorithm = "HmacSHA512",
                ),
                "dilar.response.ecies.v11|${data.e}|${sha384(iv).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA512",
                derivedNonce = true,
            )

            12 -> CipherSpec(
                hmac(
                    key = clientPubRaw,
                    data = joinBytes(u16(serverPubRaw.size), serverPubRaw, u16(iv.size), iv),
                    algorithm = "HmacSHA512",
                ).copyOf(32),
                "dilar.response.ecies.v12|${data.e}|${sha256(joinBytes(u16(iv.size), iv)).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA384",
                derivedNonce = true,
                aad = v12Aad(data, serverPubRaw, iv, ct.size),
            )

            else -> error("Unsupported encryption protocol version: ${data.v}")
        }

        val keyMaterial = hkdf(
            ikm = sharedSecret,
            salt = spec.salt,
            info = spec.info,
            length = if (spec.derivedNonce) 44 else 32,
            algorithm = spec.hash,
        )
        val key = keyMaterial.copyOf(32)
        val nonce = if (spec.derivedNonce) keyMaterial.copyOfRange(32, 44) else iv

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            spec.aad?.let { updateAAD(it) }
        }

        return cipher.doFinal(ct + tag).toString(Charsets.UTF_8)
    }

    private class CipherSpec(
        val salt: ByteArray,
        val info: ByteArray,
        val hash: String = "HmacSHA256",
        val derivedNonce: Boolean = false,
        val aad: ByteArray? = null,
    )

    private fun v12Aad(
        data: EncryptedResponseDto,
        serverPubRaw: ByteArray,
        iv: ByteArray,
        ctSize: Int,
    ): ByteArray {
        val label = "dilar.response.ecies.v12".toByteArray()
        val version = data.v.toString().toByteArray()
        val e = data.e.toString().toByteArray()
        val size = u32(ctSize)

        return sha256(
            joinBytes(
                u16(label.size),
                label,
                u16(version.size),
                version,
                u16(e.size),
                e,
                u16(serverPubRaw.size),
                serverPubRaw,
                u16(iv.size),
                iv,
                u16(size.size),
                size,
            ),
        )
    }

    private fun u16(n: Int): ByteArray = byteArrayOf(((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte())

    private fun u32(n: Int): ByteArray = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun ByteArray.toBase64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun joinBytes(vararg parts: ByteArray): ByteArray {
        val size = parts.sumOf { it.size }
        val result = ByteArray(size)
        var offset = 0
        for (p in parts) {
            System.arraycopy(p, 0, result, offset, p.size)
            offset += p.size
        }
        return result
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

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
        algorithm: String = "HmacSHA256",
    ): ByteArray = Mac.getInstance(algorithm).apply {
        init(SecretKeySpec(key, algorithm))
    }.doFinal(data)

    // HKDF

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha384(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-384").digest(data)

    private fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    private fun hkdf(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
        algorithm: String = "HmacSHA256",
    ): ByteArray {
        val prk = hmac(salt, ikm, algorithm)
        val okm = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (okm.size() < length) {
            t = hmac(prk, t + info + byteArrayOf(counter.toByte()), algorithm)
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
