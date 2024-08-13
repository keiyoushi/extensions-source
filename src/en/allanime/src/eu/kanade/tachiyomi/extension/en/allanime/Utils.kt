package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

val json: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

fun String.parseThumbnailUrl(): String {
    return if (this.matches(AllManga.urlRegex)) {
        this
    } else {
        "$thumbnail_cdn$this?w=250"
    }
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

fun String.parseDescription(): String {
    return Jsoup.parse(
        this.replace("<br>", "br2n"),
    ).text().replace("br2n", "\n")
}

fun String?.parseDate(): Long {
    return runCatching {
        dateFormat.parse(this!!)!!.time
    }.getOrDefault(0L)
}

inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

inline fun <reified T> List<*>.firstInstanceOrNull(): T? =
    filterIsInstance<T>().firstOrNull()

inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
    json.encodeToString(this)
        .toRequestBody(JSON_MEDIA_TYPE)

private const val thumbnail_cdn = "https://wp.youtube-anime.com/aln.youtube-anime.com/"
private val titleSpecialCharactersRegex = Regex("[^a-z\\d]+")
private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
}
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
