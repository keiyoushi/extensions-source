package eu.kanade.tachiyomi.extension.tr.hattoriscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaListItemDto(
    @SerialName("_id") val id: String,
    val slug: String,
    val title: String,
    val type: String,
    val status: String,
    val genres: List<GenreDto>,
    val bayesianRating: Float,
    val createdAt: String,
    private val coverImage: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaListItemDto.title
        thumbnail_url = baseUrl + coverImage
    }
}

@Serializable
class SearchItemDto(
    @SerialName("_id") val id: String,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class PersonDto(
    val name: String,
)

@Serializable
class MangaDetailsDto(
    private val slug: String,
    private val title: String,
    private val coverImage: String? = null,
    private val description: String? = null,
    private val alternativeTitles: List<String> = emptyList(),
    private val author: String? = null,
    private val artist: String? = null,
    private val authors: List<PersonDto> = emptyList(),
    private val artists: List<PersonDto> = emptyList(),
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDetailsDto.title
        thumbnail_url = coverImage?.let { baseUrl + it }
        author = authors.joinToString { it.name }.ifBlank { this@MangaDetailsDto.author }
        artist = artists.joinToString { it.name }.ifBlank { this@MangaDetailsDto.artist }
        genre = buildList {
            type?.let { add(it.replaceFirstChar(Char::uppercase)) }
            genres.mapTo(this) { it.name }
        }.joinToString().ifBlank { null }
        description = buildString {
            this@MangaDetailsDto.description?.let { append(it.trim()) }
            if (alternativeTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif isimler: ")
                append(alternativeTitles.joinToString())
            }
        }.ifBlank { null }
        status = this@MangaDetailsDto.status.toStatus()
    }
}

fun String?.toStatus() = when (this) {
    "ongoing", "current" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    private val subtitle: String? = null,
    private val chapterNumber: Float,
    private val releaseDate: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = slug
        memo = buildJsonObject { put("manga", mangaSlug) }
        name = if (subtitle.isNullOrBlank()) title else "$title - $subtitle"
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(releaseDate)
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<String>,
)
