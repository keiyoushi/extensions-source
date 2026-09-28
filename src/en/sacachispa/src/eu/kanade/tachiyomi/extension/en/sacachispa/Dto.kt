package eu.kanade.tachiyomi.extension.en.sacachispa

import kotlinx.serialization.Serializable

@Serializable
class MangaListResponse(
    val data: List<MangaListDto>,
    val pagination: PaginationDto,
)

@Serializable
class MangaListDto(
    val id: String,
    val slug: String,
    val title: String,
    val cover: String? = null,
)

@Serializable
class MangaResponse(
    val data: MangaDetailDto,
)

@Serializable
class MangaDetailDto(
    val id: String,
    val slug: String,
    val title: String,
    val status: String,
    val authors: List<NamedDto> = emptyList(),
    val artists: List<NamedDto> = emptyList(),
    val genres: List<NamedDto> = emptyList(),
    val covers: List<CoverDto> = emptyList(),
    val synopses: List<SynopsisDto> = emptyList(),
)

@Serializable
class NamedDto(
    val name: String,
)

@Serializable
class CoverDto(
    val image: String,
)

@Serializable
class SynopsisDto(
    val synopsis: String,
)

@Serializable
class ReleaseListResponse(
    val data: List<ReleaseDto>,
    val pagination: PaginationDto,
)

@Serializable
class ReleaseDto(
    val id: String,
    val chapter: ChapterRefDto,
    val publishedAt: String,
)

@Serializable
class ChapterRefDto(
    val chapter: String,
    val title: String? = null,
)

@Serializable
class PageListResponse(
    val data: PageListDto,
)

@Serializable
class PageListDto(
    val items: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val page: Int,
    val url: String,
)

@Serializable
class ErrorResponse(
    val error: ErrorDto? = null,
)

@Serializable
class ErrorDto(
    val message: String? = null,
)

@Serializable
class PaginationDto(
    val page: Int,
    val pages: Int,
)
