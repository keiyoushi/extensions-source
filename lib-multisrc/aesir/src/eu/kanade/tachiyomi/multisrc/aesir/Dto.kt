package eu.kanade.tachiyomi.multisrc.aesir

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class ResponseDto<T>(
    val data: T,
)

@Serializable
class ComicListDto(
    val data: List<ComicDto>,
    private val meta: MetaDto,
) {
    val hasNextPage get() = meta.page < meta.totalPages
}

@Serializable
class MetaDto(
    val page: Int,
    val totalPages: Int,
)

@Serializable
class ComicDto(
    val slug: String,
    private val title: String,
    private val cover: String?,
    private val altTitles: List<String> = emptyList(),
    private val description: String?,
    private val author: String?,
    private val artist: String?,
    private val type: String?,
    private val year: Int?,
    private val status: String?,
    private val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/comic/$slug"
        title = this@ComicDto.title
        thumbnail_url = cover?.let { cdnUrl.toHttpUrl().newBuilder().addPathSegments(it).build().toString() }
        author = this@ComicDto.author
        artist = this@ComicDto.artist
        description = MarkdownDescription.Builder()
            .description(this@ComicDto.description)
            .field("Year", year?.toString())
            .altTitles(altTitles)
            .build()
        genre = (listOfNotNull(type?.lowercase()?.replaceFirstChar(Char::uppercase)) + genres.map { it.name })
            .joinToString()
            .ifEmpty { null }
        status = when (this@ComicDto.status) {
            "ONGOING" -> SManga.ONGOING
            "END" -> SManga.COMPLETED
            "END_SEASON" -> SManga.ON_HIATUS
            "DROP" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: String,
    private val title: String?,
    private val isLocked: Boolean = false,
    private val publishedAt: String?,
) {
    fun toSChapter(chapterUrl: String) = SChapter.create().apply {
        url = chapterUrl
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter ")
            append(number)
            title?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
        }
        number.toFloatOrNull()?.let { chapter_number = it }
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderPropsDto(
    val readerTicket: String,
)

@Serializable
class ReaderDto(
    val locked: Boolean,
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val order: Int,
    val token: String,
    val scrambleSeed: String?,
)
