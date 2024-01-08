package eu.kanade.tachiyomi.extension.ru.newbie.dto

import kotlinx.serialization.Serializable

// Catalog API
@Serializable
data class PageWrapperDto<T>(
    val items: List<T>,
)

@Serializable
data class LibraryDto(
    val id: Long,
    val title: TitleDto,
    val image: ImgDto,
)

// Manga Details
@Serializable
data class MangaDetDto(
    val id: Long,
    val title: TitleDto,
    val author: AuthorDto?,
    val artist: AuthorDto?,
    val description: String,
    val image: ImgDto,
    val genres: List<TagsDto>,
    val type: String,
    val status: String,
    val rating: Float,
    val hearts: Long,
    val adult: String?,
    val branches: List<BranchesDto>,
)

@Serializable
data class TitleDto(
    val en: String,
    val ru: String,
)

@Serializable
data class AuthorDto(
    val name: String?,
)

@Serializable
data class ImgDto(
    val name: String,
)

@Serializable
data class TagsDto(
    val title: TitleDto,
)

@Serializable
data class BranchesDto(
    val id: Long,
    val is_default: Boolean,
)

// Chapters
@Serializable
data class SeriesWrapperDto<T>(
    val items: T,
)

@Serializable
data class BookDto(
    val id: Long,
    val tom: Int?,
    val name: String?,
    val number: Float,
    val created_at: String,
    val translator: String?,
    val is_available: Boolean,
)

@Serializable
data class PageDto(
    val id: Int,
    val slices: Int?,
)

// Search NEO in POST Request
@Serializable
data class SearchWrapperDto<T>(
    val result: T,
)

@Serializable
data class SubSearchDto<T>(
    val hits: List<T>,
)

@Serializable
data class SearchLibraryDto(
    val document: DocElementsDto,
)

@Serializable
data class DocElementsDto(
    val id: String,
    val title_en: String,
    val title_ru: String,
    val image_large: String,
    val image_small: String,
)
