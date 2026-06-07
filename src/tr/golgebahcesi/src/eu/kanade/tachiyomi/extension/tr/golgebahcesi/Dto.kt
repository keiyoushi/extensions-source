package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class SeriesDto(
    val slug: String,
    val title: String,
    val description: String? = null,
    val coverImage: String? = null,
    val type: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverImage
        author = this@SeriesDto.author?.takeIf { it.isNotBlank() }
        artist = this@SeriesDto.artist?.takeIf { it.isNotBlank() }
        status = parseStatus(this@SeriesDto.status)
        genre = buildGenre(this@SeriesDto.genres, this@SeriesDto.type)
        description = this@SeriesDto.description
        initialized = true
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun buildGenre(genres: List<String>, type: String?): String {
        val list = genres.toMutableList()
        type?.takeIf { it.isNotBlank() }?.let {
            val formattedType = it.lowercase().replaceFirstChar { c -> c.uppercase() }
            if (formattedType.lowercase() !in list.map { g -> g.lowercase() }) {
                list.add(formattedType)
            }
        }
        return list.joinToString()
    }
}

@Serializable
class SeriesListResponse(
    val data: List<SeriesDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    val currentPage: Int,
    val totalPages: Int,
)

@Serializable
class ChapterDto(
    val id: String,
    val seriesSlug: String,
    val number: Float,
    val title: String,
    val slug: String,
    val pages: List<PageDto>? = null,
    val releaseDate: String? = null,
    val createdAt: String? = null,
)

@Serializable
class PageDto(
    val index: Int,
    val url: String,
)
