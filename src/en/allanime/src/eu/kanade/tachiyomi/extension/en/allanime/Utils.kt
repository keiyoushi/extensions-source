package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
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

inline fun <reified T> Response.parseAs(): T = parseAs(json)

private const val THUMBNAIL_CDN = "https://wp.youtube-anime.com/aln.youtube-anime.com/"
private val titleSpecialCharactersRegex = Regex("[^a-z\\d]+")

val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
}
