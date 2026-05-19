package eu.kanade.tachiyomi.extension.vi.moetruyen

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val meta: MetaDto? = null,
)

@Serializable
data class ApiListResponse<T>(
    val success: Boolean,
    val data: List<T>,
    val meta: MetaDto? = null,
)

@Serializable
data class MetaDto(
    val pagination: PaginationDto? = null,
)

@Serializable
data class PaginationDto(
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
    val totalPages: Int = 0,
) {
    fun hasNextPage(): Boolean = page < totalPages
}

@Serializable
data class MangaDto(
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
data class GenreDto(
    val id: Long,
    val name: String,
)

@Serializable
data class ChapterListDataDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: Long,
    val number: Double? = null,
    val numberText: String? = null,
    val title: String? = null,
    val date: String? = null,
    val groupName: String? = null,
)

@Serializable
data class ChapterPagesDataDto(
    val pageUrls: List<String> = emptyList(),
)
