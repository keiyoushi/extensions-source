package eu.kanade.tachiyomi.extension.en.sanascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class ChapterDto(
    val slug: String,
    val number: JsonPrimitive,
    val title: String? = null,
    val createdAt: String? = null,
    val isPermanentlyLocked: Boolean? = null,
    val unlockAt: String? = null,
    val price: Int? = null,
) {
    fun toSChapter(seriesSlug: String, locked: Boolean): SChapter {
        val numberText = number.content
        val cleanedTitle = title?.trim()?.trimStart(':')?.trim()
        val hasMeaningfulTitle = !cleanedTitle.isNullOrEmpty() &&
            cleanedTitle.any { it.isLetterOrDigit() }
        val chapterTitle = if (hasMeaningfulTitle) ": $cleanedTitle" else ""
        val prefix = if (locked) "🔒 " else ""

        return SChapter.create().apply {
            url = "/series/$seriesSlug/$slug"
            name = "${prefix}Chapter $numberText$chapterTitle"
            date_upload = createdAt?.let {
                try {
                    chapterDateFormat.parse(it)?.time ?: 0L
                } catch (_: ParseException) {
                    0L
                }
            } ?: 0L
        }
    }
}

@Serializable
class GenreDto(
    val name: String,
)

class SitemapSeries(
    val slug: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SitemapSeries.title
        thumbnail_url = thumbnailUrl
    }
}

private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
