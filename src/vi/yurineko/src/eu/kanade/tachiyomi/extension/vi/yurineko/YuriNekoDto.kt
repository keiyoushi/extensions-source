package eu.kanade.tachiyomi.extension.vi.yurineko

import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    val total: Int = 0,
    val page: Int = 1,
    val lastPage: Int = 1,
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
)

@Serializable
class MangaDetailsDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val status: String? = null,
    val tags: List<TagDto> = emptyList(),
    val linkedAuthors: List<LinkedPersonDto> = emptyList(),
    val linkedArtists: List<LinkedPersonDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class TagDto(
    val name: String,
    val slug: String? = null,
)

@Serializable
class LinkedPersonDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val chapterNumber: String,
    val publishedAt: String? = null,
    val createdAt: String? = null,
)
