package eu.kanade.tachiyomi.extension.id.komikucom

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class ComicListResponse(
    val items: List<ComicDto>? = null,
    val page: Int? = null,
    val totalPages: Int? = null,
)

@Serializable
class ComicDto(
    private val id: Int,
    private val slug: String,
    private val title: String,
    private val genres: List<String>? = null,
    private val comicStatus: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val synopsis: String? = null,
    private val coverUrl: String? = null,
) {
    fun toSManga(): SManga {
        val comicTitle = title
        val comicThumbnail = coverUrl
        val comicAuthor = author
        val comicArtist = artist
        val comicSynopsis = synopsis
        val comicGenres = genres
        val comicId = id
        val comicUrl = "/manga/$slug"
        val lowerStatus = comicStatus?.lowercase()
        return SManga.create().apply {
            url = comicUrl
            this.title = comicTitle
            thumbnail_url = comicThumbnail
            author = listOfNotNull(comicAuthor, comicArtist).filter { it.isNotBlank() }.joinToString()
            description = comicSynopsis
            genre = comicGenres?.joinToString()
            status = when (lowerStatus) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            memo = buildJsonObject {
                put("id", comicId)
            }
            initialized = true
        }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val n: Float? = null,
    private val title: String? = null,
    private val releasedLabel: String? = null,
) {
    fun toSChapter(comicId: Int): SChapter = SChapter.create().apply {
        url = "/$comicId/$id"
        name = title ?: "Chapter ${n?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}"
        date_upload = parseRelativeLabel(releasedLabel)
        chapter_number = n ?: 0F
    }
}

@Serializable
class ChapterDetailDto(
    private val id: Int,
    private val pages: List<PageDto>? = null,
) {
    fun toPageList(): List<Page> = pages?.mapIndexed { index, page ->
        Page(index, "", page.imageUrl())
    } ?: emptyList()
}

@Serializable
class PageDto(
    private val url: String,
) {
    fun imageUrl(): String = url
}

@Serializable
class FilterResponse(
    val genres: List<String>? = null,
    val statuses: List<String>? = null,
    val types: List<String>? = null,
    val sorts: List<SortDto>? = null,
)

@Serializable
class SortDto(
    private val key: String? = null,
    private val label: String? = null,
) {
    fun toPair(): Pair<String, String> = (label ?: key ?: "") to (key ?: "")
}

fun parseRelativeLabel(label: String?): Long {
    if (label.isNullOrBlank()) return 0L
    val now = System.currentTimeMillis()
    when {
        "baru saja" in label -> return now
        "menit" in label -> return now - (label.filter { it.isDigit() }.toLongOrNull() ?: 0L) * 60_000L
        "jam" in label -> return now - (label.filter { it.isDigit() }.toLongOrNull() ?: 0L) * 3_600_000L
        "hari" in label -> return now - (label.filter { it.isDigit() }.toLongOrNull() ?: 0L) * 86_400_000L
    }
    // absolute date like "9 Feb 2022" / "28 Agu 2022" (mixed EN/ID month abbreviations)
    val parts = label.split(" ")
    if (parts.size == 3) {
        val day = parts[0].toIntOrNull() ?: return 0L
        val month = MONTH_ABBREVIATIONS[parts[1].lowercase()] ?: return 0L
        val year = parts[2].toIntOrNull() ?: return 0L
        return java.time.LocalDate.of(year, month, day)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
    return 0L
}

private val MONTH_ABBREVIATIONS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5,
    "jun" to 6, "jul" to 7, "agu" to 8, "aug" to 8, "sep" to 9, "okt" to 10,
    "oct" to 10, "nov" to 11, "des" to 12, "dec" to 12,
)
