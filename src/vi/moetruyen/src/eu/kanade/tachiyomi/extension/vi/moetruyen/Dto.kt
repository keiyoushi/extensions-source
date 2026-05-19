package eu.kanade.tachiyomi.extension.vi.moetruyen

import kotlinx.serialization.Serializable

@Serializable
class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val meta: MetaDto? = null,
)

@Serializable
class ApiListResponse<T>(
    val success: Boolean,
    val data: List<T>,
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
    val totalPages: Int = 0,
) {
    fun hasNextPage(): Boolean = page < totalPages
}

@Serializable
class MangaDto(
    val id: Long,
    val slug: String,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val status: String? = null,
    val coverUrl: String? = null,
    val altTitles: List<String>? = null,
    val genres: List<GenreDto>? = null,
)

@Serializable
class GenreDto(
    val id: Long,
    val name: String,
)

@Serializable
class ChapterListDataDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Long,
    val number: Double? = null,
    val numberText: String? = null,
    val title: String? = null,
    val date: String? = null,
    val groupName: String? = null,
)

@Serializable
class ChapterPagesDataDto(
    val pageUrls: List<String> = emptyList(),
)
