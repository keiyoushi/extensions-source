package eu.kanade.tachiyomi.extension.zh.guazimanhua

import android.content.SharedPreferences
import android.util.Base64
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client for the 瓜子漫画 app API, reconstructed from guazi.apk (v1.6.1, versionCode 53).
 *
 * - Every request carries the global params identifier + versionCode and the headers
 *   deviceType: android and token: <guest token>; the app injects those from one place
 * - Guest login: POST /api/v2/login/visitor with only identifier, the token is cached in memory
 * - Pages: GET /api/v2/mcomic/pics?chapter_id={id}, each images[].img is base64 of AES/CBC ciphertext
 * - Decryption: the key is the MD5 hex string of "guazi" + the site-local date yyyyMMdd, used as
 *   32 raw bytes; the IV is characters 8..24 of that same string
 *
 * Only reached when the web reader has no images for a chapter, see [Guazimanhua.getPageList].
 */
internal class GuaziAppApi(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val preferences: SharedPreferences,
) {

    @Volatile
    private var token: String? = null

    // Referer/Origin of the site mean nothing to the app API, so drop them and send a plain app request
    private val apiHeaders = headers.newBuilder()
        .set("deviceType", "android")
        .removeAll("Referer")
        .removeAll("Origin")
        .build()

    suspend fun pageUrls(chapterId: String): List<String> {
        var lastError = 0
        repeat(2) {
            val response = client.get(picsUrl(chapterId), authorizedHeaders())
            // The Date header has to be read before parseAs, which closes the response
            val serverDate = response.headers.getDate("Date")
            val body = response.parseAs<AppPicsResponse>()
            if (body.errorCode == 0) {
                return body.data?.images.orEmpty().map { decrypt(it.img, serverDate) }
            }
            // A non-zero code is usually a stale guest token: drop it so the next pass logs in again
            lastError = body.errorCode
            token = null
        }
        throw Exception("Guazi app API returned error_code=$lastError")
    }

    private suspend fun authorizedHeaders(): Headers = apiHeaders.newBuilder()
        .set("token", ensureToken())
        .build()

    private suspend fun ensureToken(): String {
        token?.let { return it }

        val body = FormBody.Builder()
            .add("identifier", identifier())
            .add("versionCode", VERSION_CODE)
            .build()
        val response = client.post("$API_URL/api/v2/login/visitor", apiHeaders, body)
        return response.parseAs<AppTokenResponse>().data?.token
            ?.also { token = it }
            ?: throw Exception("Guazi app API did not return a guest token")
    }

    private fun picsUrl(chapterId: String): HttpUrl = "$API_URL/api/v2/mcomic/pics".toHttpUrl().newBuilder()
        .addQueryParameter("chapter_id", chapterId)
        .addQueryParameter("identifier", identifier())
        .addQueryParameter("versionCode", VERSION_CODE)
        .build()

    /** Persisted like the app does, so one install always logs in as the same guest account */
    private fun identifier(): String = preferences.getString(PREF_IDENTIFIER, null)
        ?: randomIdentifier().also { preferences.edit().putString(PREF_IDENTIFIER, it).apply() }

    private fun randomIdentifier(): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(Locale.ROOT, it) }
    }

    private fun decrypt(encoded: String, serverDate: Date?): String {
        val material = md5Hex("guazi" + keyDate(serverDate))

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(material.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(material.substring(8, 24).toByteArray(Charsets.UTF_8)),
        )
        return cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT)).decodeToString()
    }

    /**
     * The key is derived from the site's current date, so the response Date header is converted to
     * the site time zone; a device in another time zone would otherwise derive the key of the wrong
     * day and fail to decrypt.
     *
     * Calling toLocalDate() first matters: BASIC_ISO_DATE has an optional offset section, and
     * formatting a ZonedDateTime directly yields "20260911+0800", which does not decrypt.
     */
    private fun keyDate(serverDate: Date?): String = (serverDate?.toInstant() ?: Instant.now())
        .atZone(SITE_ZONE)
        .toLocalDate()
        .format(DATE_FORMAT)

    private fun md5Hex(input: String): String = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(Locale.ROOT, it) }
}

private const val API_URL = "https://api.guaziapp.com/index.php"

/** Taken from guazi.apk; the API rejects requests once this falls behind the current app version */
private const val VERSION_CODE = "53"

private const val PREF_IDENTIFIER = "app_identifier"

private val SITE_ZONE = ZoneId.of("Asia/Shanghai")

// BASIC_ISO_DATE is yyyyMMdd with plain digits, independent of the device locale
private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
