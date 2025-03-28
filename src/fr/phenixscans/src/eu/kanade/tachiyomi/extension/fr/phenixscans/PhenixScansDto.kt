package eu.kanade.tachiyomi.extension.fr.phenixscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

// ---------------------------
// 1. SEARCH & PAGINATION DTOs
// ---------------------------
@Serializable
class SearchResultsDto(
    val mangas: List<LatestMangaItemDto>,
    val pagination: PaginationFilterDto? = null,
)

@Serializable
class PaginationFilterDto(
    val page: Int,
    val totalPages: Int,
)

// ---------------------------
// 2. MANGA DETAILS & CHAPTER DTOs
// ---------------------------
@Serializable
class MangaInfoDto(
    val title: String,
    val coverImage: String? = null,
    val slug: String,
    val synopsis: String? = "",
    val status: String? = null,
)

@Serializable
class ChapterInfoDto(
    val number: JsonPrimitive,
    val createdAt: String?,
)

@Serializable
class MangaDetailDto(
    val manga: MangaInfoDto,
    val chapters: List<ChapterInfoDto>,
)

// ---------------------------
// 3. LATEST & TOP MANGA DTOs
// ---------------------------
@Serializable
class LatestMangaItemDto(
    val title: String,
    val coverImage: String,
    val slug: String,
)

@Serializable
class PaginationDto(
    val currentPage: Int,
    val totalPages: Int,
)

@Serializable
class LatestMangaDto(
    val pagination: PaginationDto,
    val latest: List<LatestMangaItemDto>,
)

@Serializable
class TopMangaDto(
    val top: List<MangaInfoDto>,
)

// ---------------------------
// 4. CHAPTER READING DTOs
// ---------------------------
@Serializable
class ChapterImagesDto(
    val images: List<String>,
)

@Serializable
class ChapterContentDto(
    val chapter: ChapterImagesDto,
)

// ---------------------------
// 5. GENRE DTOs
// ---------------------------
@Serializable
class GenreDto(
    @SerialName("_id") val id: String,
    val name: String,
)

@Serializable
class GenreListDto(
    val data: List<GenreDto>,
)
