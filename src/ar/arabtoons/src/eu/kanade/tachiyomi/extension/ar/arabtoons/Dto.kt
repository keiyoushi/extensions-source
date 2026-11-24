package eu.kanade.tachiyomi.extension.ar.arabtoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

@Serializable
class Data<T>(val data: T)

@Serializable
class BrowseDto(
    val items: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(val hasNextPage: Boolean)

@Serializable
class MangaDto(
    private val id: Int? = null,
    private val title: String,
    private val slug: String,
    private val cover: String?,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover?.let { coverUrl(baseUrl, it) }
        if (id != null) memo = mangaIdMemo(memo, id)
    }
}

@Serializable
class DetailsDto(
    val mangaDetails: MangaDetailsDto,
    val recommendations: List<MangaDto>,
)

@Serializable
class MangaDetailsDto(
    val id: Int,
    private val title: String,
    private val cover: String?,
    private val description: String?,
    private val alternativeTitle: String?,
    private val author: String?,
    private val artist: String?,
    private val status: Int?,
    private val genres: List<GenreDto>,
) {
    fun applyTo(manga: SManga, baseUrl: String) = manga.apply {
        memo = mangaIdMemo(memo, id)
        title = this@MangaDetailsDto.title
        thumbnail_url = cover?.let { coverUrl(baseUrl, it) }
        author = this@MangaDetailsDto.author
        artist = this@MangaDetailsDto.artist
        description = listOfNotNull(
            this@MangaDetailsDto.description,
            alternativeTitle?.let { "Alternative Names: $it" },
        ).joinToString("\n\n").ifEmpty { null }
        genre = genres.joinToString { it.name }.ifEmpty { null }
        status = when (this@MangaDetailsDto.status) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3 -> SManga.CANCELLED
            4 -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class ChapterListDto(val items: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: Float,
    private val title: String?,
    private val date: String?,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = buildString {
            append("الفصل ")
            append(number.toString().removeSuffix(".0"))
            title?.let { append(" - ", it) }
        }
        chapter_number = number
        date_upload = Instant.tryParse(date)
    }
}

@Serializable
class ChapterPagesDto(
    val mangaDir: String,
    val chapterDir: String,
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(val name: String)

@Serializable
class FilterOptionDto(
    val label: String,
    val value: Int,
)

private fun mangaIdMemo(memo: JsonObject, id: Int) = JsonObject(memo + ("mangaId" to JsonPrimitive(id)))

private fun coverUrl(baseUrl: String, cover: String) = "$baseUrl/storage/covers/lg/$cover"
