package eu.kanade.tachiyomi.extension.id.komikucc

import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    val title: String,
    val link: String,
    val img: String? = null,
)

@Serializable
class ChapterDto(
    val title: String,
    val link: String,
    val created_at: String? = null,
)

@Serializable
class MangaDetailsDto(
    val title: String,
    val author: String? = null,
    val status: String? = null,
    val des: String? = null,
    val img: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class GenreDto(
    val title: String,
    val link: String,
)

@Serializable
class PageListDto(
    val images: List<String> = emptyList(),
)
