package eu.kanade.tachiyomi.extension.en.allanime

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// The API gates chapterPages behind a rotating request signature (aaReq),
// reversed from the site bundle (buildId 12):
//   key     = partA XOR partB   (partB is inlined in the reader shell as window.__aaCrypto)
//   iv      = SHA-256("$epoch:$buildId:$queryHash:$ts")[0..12]
//   payload = {"v":1,"ts":ts,"epoch":epoch,"buildId":buildId,"qh":queryHash}
//   aaReq   = base64(0x01 | iv | AES-GCM(key, iv, payload))
// aaReq travels inside the extensions object, and the response payload comes
// back AES-GCM encrypted in a "tobeparsed" field, decrypted with the same key.
class PageSigner(
    private val client: OkHttpClient,
    private val headers: Headers,
) {
    private var cachedBootstrap: Bootstrap? = null

    fun getPageList(mangaId: String, chapterString: String, translationType: String): PageListData? {
        val bootstrap = getBootstrap() ?: return null
        val key = deriveKey(bootstrap.partB)
        val queryHash = sha256Hex(PAGE_LIST_QUERY)
        val aaReq = buildAaReq(key, bootstrap.epoch, queryHash)

        val variables = """{"mangaId":"$mangaId","translationType":"$translationType",""" +
            """"chapterString":"$chapterString","limit":$PAGE_SOURCE_LIMIT,"offset":0}"""
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$queryHash"},"aaReq":"$aaReq"}"""

        val url = API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", PAGE_LIST_QUERY)
            .addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions)
            .build()

        val encrypted = client.newCall(GET(url.toString(), headers)).execute()
            .parseAs<PageApiResponse>().data?.tobeparsed ?: return null

        return decrypt(encrypted, key).parseAs<PageListData>(json)
    }

    // partB and epoch are inlined in the current build's reader shell as
    // window.__aaCrypto; only that mirror ships it, so scan the mirrors for it.
    private fun getBootstrap(): Bootstrap? {
        cachedBootstrap?.let { if (it.switchAt > System.currentTimeMillis()) return it }

        for (host in MIRROR_HOSTS) {
            try {
                val body = client.newCall(
                    GET("https://$host/client-crypto/v1/bootstrap?buildId=$BUILD_ID", headers),
                ).execute().use { it.body.string() }

                val raw = aaCryptoRegex.find(body)?.groupValues?.get(1) ?: continue
                val obj = json.parseToJsonElement(raw).jsonObject
                val epoch = obj["epoch"]?.jsonPrimitive?.longOrNull ?: continue
                val partB = obj["partB"]?.jsonPrimitive?.contentOrNull ?: continue
                val switchAt = obj["switchAt"]?.jsonPrimitive?.longOrNull
                    ?: (System.currentTimeMillis() + TS_BUCKET_MS)

                return Bootstrap(epoch, partB, switchAt).also { cachedBootstrap = it }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun deriveKey(partB: String): SecretKeySpec {
        val a = PART_A_HEX.hexToBytes()
        val b = Base64.decode(partB, Base64.DEFAULT)
        require(b.size >= 32) { "part B too short" }

        val raw = ByteArray(32) { i -> (b[i].toInt() xor a[i % a.size].toInt()).toByte() }
        return SecretKeySpec(raw, "AES")
    }

    private fun buildAaReq(key: SecretKeySpec, epoch: Long, queryHash: String): String {
        val ts = System.currentTimeMillis() / TS_BUCKET_MS * TS_BUCKET_MS
        val payload = """{"v":1,"ts":$ts,"epoch":$epoch,"buildId":"$BUILD_ID","qh":"$queryHash"}"""
        val iv = sha256("$epoch:$BUILD_ID:$queryHash:$ts").copyOf(12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }.doFinal(payload.toByteArray(Charsets.UTF_8))

        val out = ByteArray(13 + cipher.size)
        out[0] = 1
        iv.copyInto(out, 1)
        cipher.copyInto(out, 13)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(value: String, key: SecretKeySpec): String {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        val version = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 13)
        val cipher = bytes.copyOfRange(13, bytes.size)

        return try {
            aesGcmDecrypt(key, iv, cipher)
        } catch (_: Exception) {
            val legacyKey = SecretKeySpec(sha256("$SECRET_PREFIX:v$version"), "AES")
            aesGcmDecrypt(legacyKey, iv, cipher)
        }
    }

    private fun aesGcmDecrypt(key: SecretKeySpec, iv: ByteArray, data: ByteArray): String {
        val plain = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }.doFinal(data)
        return String(plain, Charsets.UTF_8)
    }

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(value: String): String = sha256(value).joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private data class Bootstrap(val epoch: Long, val partB: String, val switchAt: Long)

    companion object {
        private const val PART_A_HEX =
            "78ebe40583e4f360cd9f56926b775a780054367c826123dcd0577a231eee4e73"
        private const val BUILD_ID = "12"
        private const val SECRET_PREFIX = "Xot36i3lK3"
        private const val TS_BUCKET_MS = 5 * 60 * 1000L
        private const val PAGE_SOURCE_LIMIT = 10
        private const val API_URL = "https://api.allanime.day/api"
        private val MIRROR_HOSTS = listOf("allmanga.to", "mkissa.to")
        private val aaCryptoRegex = Regex("""window\.__aaCrypto\s*=\s*(\{.*?\})\s*;""")
    }
}
