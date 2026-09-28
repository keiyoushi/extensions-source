package eu.kanade.tachiyomi.extension.vi.damconuong

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListResponse(
    val data: List<MangaDto>,
    val meta: PaginationDto,
)

@Serializable
class MangaResponse(
    val data: MangaDto,
)

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto>,
    val meta: PaginationDto,
)

@Serializable
class GenreListResponse(
    val data: List<GenreDto>,
    val meta: PaginationDto,
)

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
)

@Serializable
class MangaDto(
    val name: String,
    val slug: String,
    @SerialName("cover_full_url") val coverUrl: String? = null,
    val pilot: String? = null,
    val status: Int = 0,
    val author: AuthorDto? = null,
    val artist: AuthorDto? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

@Serializable
class ChapterDto(
    val name: String,
    val slug: String,
    @SerialName("chapter_number") val chapterNumber: Float? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class EncryptedPagesResponse(
    val e: String,
)

@Serializable
class PageListResponse(
    val p: List<String>,
    val s: List<String> = emptyList(),
)
