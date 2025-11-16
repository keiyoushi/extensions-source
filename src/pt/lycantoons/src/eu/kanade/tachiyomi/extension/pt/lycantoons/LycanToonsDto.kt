package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PopularResponseDto(
    val success: Boolean,
    val data: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
data class PaginationDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
)

@Serializable
data class MangaDto(
    val id: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String,
    @SerialName("coverUrl") val coverUrl: String,
    val slug: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        url = "/series/$slug"
        author = this@MangaDto.author ?: "Desconhecido"
        artist = this@MangaDto.author ?: "Desconhecido"
        description = this@MangaDto.description
        genre = genres.joinToString(", ")
        status = parseStatus(this@MangaDto.status)
    }
}

@Serializable
data class SearchBodyDto(
    val limit: Int,
    val page: Int,
    val search: String,
    val status: String? = null,
    val seriesType: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class SearchResponseDto(
    val series: List<SearchMangaDto>,
    val pagination: SearchPaginationDto,
)

@Serializable
data class SearchPaginationDto(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val itemsPerPage: Int,
    val hasNextPage: Boolean,
    val hasPrevPage: Boolean,
)

@Serializable
data class SearchMangaDto(
    val id: String,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: String,
    @SerialName("coverUrl") val coverUrl: String,
    val slug: String,
    val seriesType: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@SearchMangaDto.title
        thumbnail_url = coverUrl
        url = "/series/$slug"
        author = this@SearchMangaDto.author ?: "Desconhecido"
        artist = this@SearchMangaDto.artist ?: author ?: "Desconhecido"
        description = this@SearchMangaDto.description
        genre = this@SearchMangaDto.genre.joinToString(", ")
        status = parseStatus(this@SearchMangaDto.status)
    }
}

@Serializable
data class ChapterDto(
    val numero: Float,
    val createdAt: String = "",
    val pageCount: Int = 0,
)

@Serializable
data class SeriesDataDto(
    val title: String,
    val slug: String,
    val description: String,
    val genre: List<String>,
    val seriesType: String,
    val coverUrl: String,
    val capitulos: List<ChapterDto>,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@SeriesDataDto.title
        thumbnail_url = coverUrl
        url = "/series/$slug"
        description = this@SeriesDataDto.description
        genre = this@SeriesDataDto.genre.joinToString(", ")
    }
}

private fun parseStatus(status: String): Int = when (status.uppercase()) {
    "ONGOING" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "HIATUS" -> SManga.ON_HIATUS
    "CANCELLED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
