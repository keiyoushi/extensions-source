package eu.kanade.tachiyomi.extension.vi.baotangtruyen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class ComicsResponse(
    val data: List<ComicDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    @SerialName("current_page")
    val currentPage: Int = 1,
    val limit: Int = 0,
    @SerialName("total_comics")
    val totalComics: Int = 0,
    @SerialName("total_pages")
    val totalPages: Int = 1,
)

@Serializable
class ComicDto(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: JsonElement? = null,
    val thumbnail: String? = null,
    @SerialName("hot_thumbnail")
    val hotThumbnail: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Long? = null,
    val slug: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("chapter_number")
    val chapterNumber: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("is_free")
    val isFree: Boolean? = null,
    @SerialName("unlock_cost")
    val unlockCost: Int? = null,
)

@Serializable
class ChapterPageResponse(
    @SerialName("chapter_name")
    val chapterName: String? = null,
    @SerialName("comic_name")
    val comicName: String? = null,
    val images: List<String> = emptyList(),
    @SerialName("is_free")
    val isFree: Boolean? = null,
    @SerialName("unlock_cost")
    val unlockCost: Int? = null,
)
