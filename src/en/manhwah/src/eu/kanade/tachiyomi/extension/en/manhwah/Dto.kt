package eu.kanade.tachiyomi.extension.en.manhwah

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class ResponseDto<T>(
    val data: T,
)

@Serializable
class ListResponseDto(
    val data: List<ComicDto>,
    val meta: MetaDto,
)

@Serializable
class MetaDto(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ComicDto(
    private val id: String,
    private val title: String,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<GenreNameDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@ComicDto.title
        thumbnail_url = coverImage
        genre = genreOf(type, genres)
        status = parseStatus(this@ComicDto.status)
    }
}

@Serializable
class ComicDetailDto(
    private val id: String,
    private val title: String,
    @SerialName("alternative_titles") private val alternativeTitles: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val synopsis: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    @SerialName("release_year") private val releaseYear: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val rating: Double? = null,
    @SerialName("rating_count") private val ratingCount: Int? = null,
    private val genres: List<GenreNameDto>? = null,
    val chapters: List<ChapterDto>,
    @SerialName("raw_chapters") val rawChapters: List<ChapterDto>,
    @SerialName("related_comics") val relatedComics: List<ComicDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@ComicDetailDto.title
        thumbnail_url = coverImage
        author = this@ComicDetailDto.author?.takeUnless { it == PLACEHOLDER_CREATOR }
        artist = this@ComicDetailDto.artist?.takeUnless { it == PLACEHOLDER_CREATOR }
        genre = genreOf(type, genres)
        status = parseStatus(this@ComicDetailDto.status)
        description = MarkdownDescription.Builder()
            .rating(rating?.takeIf { (ratingCount ?: 0) > 0 }, votes = ratingCount)
            .info(releaseYear)
            .description(synopsis)
            .altTitles(
                alternativeTitles?.split(" | ").orEmpty()
                    .filterNot { it.equals(title, ignoreCase = true) }
                    .distinctBy { it.lowercase() },
            )
            .build()
    }
}

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)

@Serializable
class GenreNameDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("chapter_number") private val chapterNumber: Float,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(raw: Boolean) = SChapter.create().apply {
        url = id
        // The site shows the number, not the API's chapter title
        name = "Chapter ${chapterNumber.toString().removeSuffix(".0")}"
        chapter_number = chapterNumber
        scanlator = if (raw) "Raw" else null
        date_upload = DATE_FORMAT.tryParseDateTime(createdAt, ZoneOffset.UTC)
    }
}

@Serializable
class ReaderDto(
    @SerialName("comic_id") val comicId: String,
    @SerialName("comic_title") val comicTitle: String,
    @SerialName("comic_cover") val comicCover: String? = null,
    val pages: List<PageDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = comicId
        title = comicTitle
        thumbnail_url = comicCover
    }
}

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("backup_url") val backupUrl: String? = null,
    @SerialName("page_order") val pageOrder: Int,
)

private fun genreOf(type: String?, genres: List<GenreNameDto>?) = (listOfNotNull(type) + genres.orEmpty().map { it.name })
    .distinct().joinToString().ifEmpty { null }

private fun parseStatus(status: String?) = when (status?.lowercase()) {
    "ongoing", "on-going" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

// The site's author and artist for titles with no credited creators
private const val PLACEHOLDER_CREATOR = "Updating"

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
