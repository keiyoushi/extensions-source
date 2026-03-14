package eu.kanade.tachiyomi.extension.id.mangakuri

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    @SerialName("poster_image_url") val posterImageUrl: String? = null,
)

@Serializable
class SeriesDetailDto(
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    @SerialName("poster_image_url") val posterImageUrl: String? = null,
    @SerialName("comic_status") val comicStatus: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val units: List<ChapterDto> = emptyList(),
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val slug: String,
    val number: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class ChapterDetailDto(
    val chapter: ChapterPagesDto,
)

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)
