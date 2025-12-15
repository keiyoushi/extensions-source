package eu.kanade.tachiyomi.extension.pt.kuromangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat

/**
 * Builds the thumbnail URL, removing /uploads/ prefix if present.
 */
internal fun buildThumbnailUrl(cdnUrl: String, path: String): String {
    val cleanPath = path
        .removePrefix("/")
        .removePrefix("uploads/")
    return "$cdnUrl/$cleanPath"
}

@Serializable
data class MangaListResponse(
    val data: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    val page: Int,
    val limit: Int,
    @JsonNames("total_pages") val totalPages: Int? = null,
    val hasNext: Boolean? = null,
) {
    fun hasNextPage(): Boolean = hasNext ?: (page < (totalPages ?: 1))
}

@Serializable
data class MangaDto(
    val id: Int,
    val title: String,
    val description: String? = null,
    val status: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String>? = null,
    @SerialName("alternative_titles") val alternativeTitles: List<String>? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/manga/$id"
        title = this@MangaDto.title
        thumbnail_url = coverImage?.let { buildThumbnailUrl(cdnUrl, it) }
        description = buildString {
            this@MangaDto.description?.let { append(it) }
            this@MangaDto.alternativeTitles?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos: ${it.joinToString()}")
            }
        }.takeIf { it.isNotBlank() }
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        genre = this@MangaDto.genres?.joinToString()
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing", "em andamento" -> SManga.ONGOING
            "completed", "completo" -> SManga.COMPLETED
            "hiatus", "em hiato" -> SManga.ON_HIATUS
            "cancelled", "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
data class MangaDetailsResponse(
    val manga: MangaDto,
    val chapters: List<ChapterDto>,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val title: String? = null,
    @SerialName("chapter_number") val chapterNumber: String? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
) {
    fun toSChapter(mangaId: Int, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/chapter/$mangaId/$id"
        name = buildString {
            chapterNumber?.toFloatOrNull()?.let { append("Capítulo ${it.toString().removeSuffix(".0")}") }
            this@ChapterDto.title?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" - ")
                append(it)
            }
        }.ifBlank { "Capítulo ${this@ChapterDto.id}" }
        chapter_number = this@ChapterDto.chapterNumber?.toFloatOrNull() ?: 0f
        date_upload = uploadDate?.let { dateFormat.tryParse(it) } ?: 0L
    }
}

@Serializable
data class LatestResponse(
    val data: List<LatestMangaDto>,
    val pagination: PaginationDto,
)

@Serializable
data class LatestMangaDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_title") val mangaTitle: String,
    @SerialName("manga_cover") val mangaCover: String? = null,
    @SerialName("manga_genres") val mangaGenres: List<String>? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/manga/$mangaId"
        title = mangaTitle
        thumbnail_url = mangaCover?.let { buildThumbnailUrl(cdnUrl, it) }
        genre = mangaGenres?.joinToString()
    }
}

@Serializable
data class ChapterPagesResponse(
    val id: Int,
    val pages: List<String>,
)

// ========================= Auth =========================

@Serializable
data class LoginResponse(
    val token: String,
)
