package eu.kanade.tachiyomi.extension.en.allanime

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

val json: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

fun String.parseThumbnailUrl(): String = if (this.matches(AllManga.urlRegex)) {
    this
} else {
    "$THUMBNAIL_CDN$this?w=250"
}

fun String?.parseStatus(): Int {
    if (this == null) {
        return SManga.UNKNOWN
    }

    return when {
        this.contains("releasing", true) -> SManga.ONGOING
        this.contains("finished", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

fun String.titleToSlug() = this.trim()
    .lowercase(Locale.US)
    .replace(titleSpecialCharactersRegex, "-")

fun String.parseDescription(): String = Jsoup.parse(
    this.replace("<br>", "br2n"),
).text().replace("br2n", "\n")

fun String?.parseDate(): Long = runCatching {
    dateFormat.parse(this!!)!!.time
}.getOrDefault(0L)

inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

fun Response.parsePageList(): PageListData {
    val rawBody = body.string()
    val wrapper = json.decodeFromString<Data<EncryptedOrPageListData>>(rawBody)

    val encrypted = wrapper.data.tobeparsed
    if (encrypted != null) {
        val decrypted = decryptAesGcm(encrypted)
        return json.decodeFromString<PageListData>(decrypted)
    }

    // Fallback: unencrypted response (old format)
    return json.decodeFromString<ApiPageListResponse>(rawBody).data
}

private fun decryptAesGcm(encoded: String): String {
    val keyBytes = MessageDigest.getInstance("SHA-256")
        .digest(AES_KEY.toByteArray(Charsets.UTF_8))

    val data = Base64.decode(encoded, Base64.DEFAULT)
    val iv = data.copyOfRange(0, 12)
    val ciphertext = data.copyOfRange(12, data.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, iv),
        )
    }

    return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
}

private const val AES_KEY = "SimtVuagFbGR2K7P"

inline fun <reified T> List<*>.firstInstanceOrNull(): T? = filterIsInstance<T>().firstOrNull()

inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody = json.encodeToString(this)
    .toRequestBody(JSON_MEDIA_TYPE)

private const val THUMBNAIL_CDN = "https://wp.youtube-anime.com/aln.youtube-anime.com/"
private val titleSpecialCharactersRegex = Regex("[^a-z\\d]+")
private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
}
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
