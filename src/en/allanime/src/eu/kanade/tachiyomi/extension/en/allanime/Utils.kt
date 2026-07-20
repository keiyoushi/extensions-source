package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.source.model.SManga
import java.util.Locale

fun String.parseThumbnailUrl(): String = if (this.matches(urlRegex)) {
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

private const val THUMBNAIL_CDN = "https://wp.youtube-anime.com/aln.youtube-anime.com/"
private val titleSpecialCharactersRegex = Regex("[^a-z\\d]+")
