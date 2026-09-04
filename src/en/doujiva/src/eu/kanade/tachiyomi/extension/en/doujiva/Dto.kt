package eu.kanade.tachiyomi.extension.en.doujiva

import kotlinx.serialization.Serializable

@Serializable
class MangaListResponse(
    val data: List<MangaDto> = emptyList(),
    val meta: Meta? = null,
    val ok: Boolean = false,
)

@Serializable
class SearchResponse(
    val data: List<MangaDto> = emptyList(),
    val meta: Meta? = null,
    val ok: Boolean = false,
)

@Serializable
class MangaDetailResponse(
    val data: MangaDto? = null,
    val ok: Boolean = false,
    val error: String? = null,
)

@Serializable
class Meta(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
class MangaDto(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String? = null,
    val coverUrl: String? = null,
    val firstPageUrl: String? = null,
    val language: String? = null,
    val mediaType: String? = null,
    val pageCount: Int = 0,
    val status: String? = null,
    val uploadedAt: String? = null,
    val tags: List<TagDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val sourceName: String? = null,
)

@Serializable
class TagDto(
    val name: String = "",
    val category: String = "",
)

@Serializable
class ChapterDto(
    val id: String = "",
    val number: Float = 1f,
    val title: String? = null,
    val pageCount: Int = 0,
    val createdAt: String? = null,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val number: Int = 0,
    val imageUrl: String = "",
)
