package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class WorkListDto(
    val items: List<WorkDto>,
    private val page: Int,
    private val pageCount: Int,
) {
    val hasNextPage get() = page < pageCount
}

@Serializable
class WorkDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val coverLarge: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList(),
    private val author: String? = null,
    private val synopsis: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@WorkDto.title
        thumbnail_url = coverLarge ?: cover
        description = synopsis
        author = this@WorkDto.author?.takeIf { it.isNotBlank() }
        genre = tags.joinToString()
        status = when (this@WorkDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float,
    private val title: String? = null,
    private val releasedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val num = number.toString().removeSuffix(".0")
        url = "/chapter/$id/$mangaSlug/$num"
        name = buildString {
            append("Capítulo $num")
            this@ChapterDto.title?.takeIf { it.isNotBlank() && !it.startsWith("Capítulo") }?.let {
                append(" - ")
                append(it)
            }
        }
        chapter_number = number
        // Older chapters use "yyyy-MM-dd HH:mm:ss", newer ones an ISO instant
        date_upload = if (releasedAt?.contains('T') == true) {
            Instant.tryParse(releasedAt)
        } else {
            dateFormat.tryParseDateTime(releasedAt, ZoneOffset.UTC)
        }
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<String>,
)

@Serializable
class GenreTagDto(
    val slug: String,
    val label: String,
    val isNsfw: Boolean = false,
)

@Serializable
class FilterData(
    val genres: List<GenreTagDto>,
    val tags: List<GenreTagDto>,
)

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
