package eu.kanade.tachiyomi.extension.all.doujiva

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
)

@Serializable
class Meta(
    val totalPages: Int = 0,
)

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val language: String? = null,
    val mediaType: String? = null,
    val pageCount: Int = 0,
    val status: String? = null,
    val tags: List<TagDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val sourceName: String? = null,
)

@Serializable
class TagDto(
    val name: String,
    val category: String,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Float,
    val title: String? = null,
    val createdAt: String? = null,
)

@Serializable
class ChapterPagesResponse(
    val data: List<ChapterPageDto> = emptyList(),
    val ok: Boolean = false,
)

@Serializable
class ChapterPageDto(
    val imageUrl: String,
)
