package eu.kanade.tachiyomi.extension.en.mangade

import kotlinx.serialization.Serializable

@Serializable
data class PayloadDto<T>(
    val success: Boolean,
    val message: String,
    val data: T,
)

@Serializable
data class MangaListPageDto(
    val list: List<MangaDto>,
    val totalPage: Int,
    val page: String,
)

@Serializable
data class MangaDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    val image: String,
    val description: String? = null,
    val genre_names: String? = null,
    val status: String? = null,
    val news_chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    val chapter_number: String? = null,
    val published_date: String? = null,
    val chapter_images: List<PageDto> = emptyList(),
)

@Serializable
data class PageDto(
    val image: String,
)

@Serializable
data class GenreListPageDto(
    val genres: List<GenreDto>,
)

@Serializable
data class GenreDto(
    val id: String,
    val name: String,
)
