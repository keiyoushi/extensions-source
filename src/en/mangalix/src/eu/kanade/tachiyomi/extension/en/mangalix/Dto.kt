package eu.kanade.tachiyomi.extension.en.mangalix

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Serializable
internal class MangaDto(
    val slug: String,
    val title: String,
    val description: String,
    val coverImage: String,
    val author: String,
    val status: String,
    val rating: Double,
    val releaseYear: Double,
    val genres: List<String>,
    val latestChapter: LatestChapterDto?,
) {
    val latestTimestamp: Long
        get() = latestChapter?.releaseDate.toMangaTimestamp()

    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverImage.takeIf { it.isNotBlank() }?.let {
            when {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) -> it
                it.startsWith("//") -> "https:$it"
                else -> "${baseUrl.trimEnd('/')}/${it.trimStart('/')}"
            }
        }
        author = this@MangaDto.author.takeIf { it.isNotBlank() }
        description = this@MangaDto.description.takeIf { it.isNotBlank() }
        genre = genres.takeIf { it.isNotEmpty() }?.joinToString()
        status = this@MangaDto.status.toMangaStatus()
        initialized = true
    }
}

@Serializable
internal class LatestChapterDto(
    val releaseDate: String?,
)

internal class ChapterDto(
    val id: String,
    val number: Double,
    val title: String,
    val pages: List<String>,
    val releaseDate: String? = null,
)

internal fun String?.toMangaTimestamp(): Long = this?.let { value ->
    runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        .getOrDefault(0L)
} ?: 0L

private fun String.toMangaStatus(): Int = when (
    lowercase(Locale.ROOT)
        .trim()
        .replace("-", " ")
        .replace(Regex("""\s+"""), " ")
) {
    "ongoing", "publishing", "releasing", "active" -> SManga.ONGOING
    "completed", "complete", "finished" -> SManga.COMPLETED
    "hiatus", "on hiatus", "paused" -> SManga.ON_HIATUS
    "cancelled", "canceled", "dropped", "axed", "discontinued" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
