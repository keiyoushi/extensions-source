package eu.kanade.tachiyomi.extension.ru.remanga.dto

import kotlinx.serialization.Serializable

@Serializable
data class TagsDto(
    val id: Int,
    val name: String,
)

@Serializable
data class BranchesDto(
    val id: Long,
    val count_chapters: Int,
)

@Serializable
data class ImgDto(
    val high: String? = null,
    val mid: String? = null,
    val low: String? = null,
)

@Serializable
data class LibraryDto(
    val id: Long,
    val en_name: String,
    val rus_name: String,
    val dir: String,
    val img: ImgDto,
    val bookmark_type: String? = null,
)

@Serializable
data class MyLibraryDto(
    val title: LibraryDto,
)

@Serializable
data class StatusDto(
    val id: Int,
    val name: String,
)

@Serializable
data class MangaDetDto(
    val id: Long,
    val en_name: String,
    val rus_name: String,
    val another_name: String,
    val dir: String,
    val description: String?,
    val issue_year: Int?,
    val img: ImgDto,
    val type: TagsDto,
    val genres: List<TagsDto>,
    val categories: List<TagsDto>,
    val branches: List<BranchesDto>,
    val status: StatusDto,
    val avg_rating: String,
    val count_rating: Int,
    val age_limit: Int,
)

@Serializable
data class PropsDto(
    val total_pages: Int? = 0,
    val page: Int,
)

@Serializable
data class PageWrapperDto<T>(
    val content: List<T>,
    val props: PropsDto,
)

@Serializable
data class SeriesWrapperDto<T>(
    val content: T,
)

@Serializable
data class PublisherDto(
    val name: String,
)

@Serializable
data class BookDto(
    val id: Long,
    val tome: Int,
    val chapter: String,
    val name: String,
    val upload_date: String,
    val is_paid: Boolean,
    val is_bought: Boolean?,
    val publishers: List<PublisherDto>,
)

@Serializable
data class ExWrapperDto<T>(
    val data: T,
)

@Serializable
data class ExBookDto(
    val id: Long,
    val tome: Int,
    val chapter: String,
)

@Serializable
data class ExLibraryDto(
    val id: Long,
    val dir: String,
    val name: String = "Без названия",
    val img: String?,
)

@Serializable
data class PagesDto(
    val id: Int,
    val height: Int,
    val link: String,
    val page: Int,
)

@Serializable
data class PageDto(
    val pages: List<PagesDto>,
)

@Serializable
data class ChunksPageDto(
    val pages: List<List<PagesDto>>,
)

@Serializable
data class UserDto(
    val id: Long,
)
