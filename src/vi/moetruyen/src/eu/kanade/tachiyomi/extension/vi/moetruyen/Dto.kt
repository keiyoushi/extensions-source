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
    val manga: ChapterPagesMangaDto? = null,
    val chapter: ChapterPagesChapterDto? = null,
)

@Serializable
class ChapterPagesMangaDto(
    val slug: String? = null,
)

@Serializable
class ChapterPagesChapterDto(
    val number: Int? = null,
)

@Serializable
class PageAccessRequest(
    val pageIndexes: List<Int>,
)

@Serializable
class PageAccessResponse(
    val ok: Boolean,
    val pages: List<PageAccessEntry> = emptyList(),
    val maxWindow: Int = 5,
)

@Serializable
class PageAccessEntry(
    val pageIndex: Int,
    val storageKey: String = "",
    val downloadUrl: String = "",
    val grant: ImgxGrant? = null,
)

@Serializable
class ImgxGrant(
    val version: Int? = null,
    val algorithm: String? = null,
    val imageId: String? = null,
    val issuedAt: Long? = null,
    val expiresAt: Long? = null,
    val nonce: String? = null,
    val keyNonce: String? = null,
    val signature: String? = null,
    val wrappedDecodeKey: String? = null,
    val decodeKey: String? = null,
)
