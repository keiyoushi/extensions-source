package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    Locale.ROOT,
).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class SeriesResponse(
    val data: List<SeriesDto>,
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    private val page: Int? = null,
    private val totalPages: Int? = null,
) {
    val current: Int
        get() = page ?: 1

    val total: Int
        get() = totalPages ?: 1
}

@Serializable
class SeriesDto(
    private val title: String,
    private val slug: String,
    private val coverUrl: String? = null,
    private val description: String? = null,

    @SerialName("publicationStatus")
    private val status: String? = null,

    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<String>? = null,
) {

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/series/$slug"
        this.title = this@SeriesDto.title
        thumbnail_url = coverUrl?.let { baseUrl + it }
    }

    fun toSMangaDetails(baseUrl: String) = toSManga(baseUrl).apply {
        description = this@SeriesDto.description

        status = when (this@SeriesDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        author = this@SeriesDto.author
        artist = this@SeriesDto.artist

        genre = genres?.joinToString {
            it.replaceFirstChar { char ->
                char.uppercase()
            }
        }

        initialized = true
    }
}

@Serializable
class ChapterListResponse(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val chapterId: String,
    private val chapterNumber: String,
    private val title: String? = null,
    private val createdAt: String? = null,
) {

    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        val formattedNumber = chapterNumber.toFloatOrNull()?.toString()?.removeSuffix(".0")
            ?: chapterNumber

        url = "/series/$seriesSlug/ch-$formattedNumber#$chapterId"

        name = "Chapter $formattedNumber" +
            (
                if (title?.isNotEmpty() == true) {
                    " - $title"
                } else {
                    ""
                }
                )

        date_upload = dateFormat.tryParse(createdAt)
        chapter_number = formattedNumber.toFloatOrNull() ?: -1f
    }
}

@Serializable
class ChapterDetailsDto(
    private val pages: List<PageDto> = emptyList(),
    private val images: List<PageDto> = emptyList(),
) {
    val allPages: List<PageDto>
        get() = pages.ifEmpty { images }
}

@Serializable
class PageDto(
    val pageNumber: Int = 0,
    val url: String,
)
