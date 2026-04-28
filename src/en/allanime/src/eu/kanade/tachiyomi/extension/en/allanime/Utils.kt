package eu.kanade.tachiyomi.extension.en.allanime

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Response
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

inline fun <reified T> Response.parseAs(): T = parseAs(json)

fun Response.decryptPageList(): PageListData {
    val encrypted = parseAs<Data<EncryptedData>>().data.encrypted
    val decrypted = decryptAesGcm(encrypted)
    return decrypted.parseAs<PageListData>(json)
}

private fun decryptAesGcm(encoded: String): String {
    val keyBytes = MessageDigest.getInstance("SHA-256")
        .digest(AES_KEY.toByteArray(Charsets.UTF_8))

    val data = Base64.decode(encoded, Base64.DEFAULT)
    val iv = data.copyOfRange(1, 13)
    val ciphertext = data.copyOfRange(13, data.size)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, iv),
        )
    }

    return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
}

private const val AES_KEY = "Xot36i3lK3:v1"

private const val THUMBNAIL_CDN = "https://wp.youtube-anime.com/aln.youtube-anime.com/"
private val titleSpecialCharactersRegex = Regex("[^a-z\\d]+")

val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
}
