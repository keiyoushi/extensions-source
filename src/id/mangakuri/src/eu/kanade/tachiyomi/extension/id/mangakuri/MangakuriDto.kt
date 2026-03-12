package eu.kanade.tachiyomi.extension.id.mangakuri

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val data: List<MangaDto>,
    val total_pages: Int,
)

@Serializable
data class MangaDto(
    val title: String,
    val slug: String,
    val poster_image_url: String? = null,
)

@Serializable
data class SeriesDetailDto(
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    val poster_image_url: String? = null,
    val comic_status: String? = null,
    val author_name: String? = null,
    val artist_name: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val units: List<ChapterDto> = emptyList(),
)

@Serializable
data class GenreDto(
    val name: String,
)

@Serializable
data class ChapterDto(
    val slug: String,
    val number: String,
    val created_at: String? = null,
)

@Serializable
data class ChapterDetailDto(
    val chapter: ChapterPagesDto,
)

@Serializable
data class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
data class PageDto(
    val image_url: String,
)
