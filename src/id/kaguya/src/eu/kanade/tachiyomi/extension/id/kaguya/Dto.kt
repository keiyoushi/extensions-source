package eu.kanade.tachiyomi.extension.id.kaguya

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
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val altTitles: List<String> = emptyList(),
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@ComicDto.title
        thumbnail_url = cover?.let { CDN_URL.toHttpUrl().newBuilder().addPathSegments(it).build().toString() }
        author = this@ComicDto.author
        artist = this@ComicDto.artist
        description = MarkdownDescription.Builder()
            .description(this@ComicDto.description)
            .altTitles(altTitles)
            .build()
        genre = (listOfNotNull(type?.lowercase()?.replaceFirstChar(Char::uppercase)) + genres.map { it.name })
            .joinToString()
            .ifEmpty { null }
        status = when (this@ComicDto.status) {
            "ONGOING" -> SManga.ONGOING
            "END", "END_SEASON" -> SManga.COMPLETED
            "DROP" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapters() = chapters.map { it.toSChapter(slug) }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String,
    private val title: String? = null,
    private val isLocked: Boolean = false,
    private val publishedAt: String? = null,
) {
    fun toSChapter(comicSlug: String) = SChapter.create().apply {
        url = "/comic/$comicSlug/chapter/$id"
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter ")
            append(number)
            title?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(
    val locked: Boolean = false,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val order: Int,
    val token: String,
)
