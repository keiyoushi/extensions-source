package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class MangaListResponse(
    val success: Boolean,
    val mangas: List<MangaDto>,
    val pagination: PaginationDto,
) {
    fun toSMangaList(baseUrl: String): List<SManga> = mangas.map { dto ->
        SManga.create().apply {
            url = "/manga/${dto.slug}"
            title = dto.title
            artist = if (dto.artist == "N/A") null else dto.artist
            author = if (dto.author == "N/A") null else dto.author
            description = dto.description
            genre = dto.genres.joinToString(", ")
            status = when (dto.status.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = if (dto.cover.isAbsoluteUrl()) dto.cover else "$baseUrl${dto.cover}"

            description = dto.description
        }
    }

    fun String.isAbsoluteUrl(): Boolean {
        return this.matches(Regex("^(?:[a-z+]+:)?//", RegexOption.IGNORE_CASE))
    }
}

@Serializable
class MangaDto(
    val id: String,
    val slug: String,
    val title: String,
    val alternativeTitles: List<String>,
    val description: String,
    val cover: String,
    val type: String,
    val status: String,
    val author: String, // "N/A" if unknown
    val artist: String, // "N/A" if unknown
    val genres: List<String>,
    val views: Long,
    val pinned: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val chapterCount: Int,
)

@Serializable
class PaginationDto(
    val page: Int,
    val limit: Int,
    val hasNextPage: Boolean,
    val hasPrevPage: Boolean,
)

@Serializable
class RimuScansEmbeddedChapterDto(
    val id: String,
    val mangaId: String,
    val number: Float,
    val title: String,
    val releaseDate: String,
    val thumbnail: String? = null,
    val views: Int? = null,
    val status: String? = null,
    val scheduledAt: String? = null,
    val type: String? = null,
    val patreonUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
// images: List<ImageDto> can be added if needed
)
