package eu.kanade.tachiyomi.extension.vi.kirakira

import kotlinx.serialization.Serializable

@Serializable
class ComicListDto(
    val comics: List<ComicDto> = emptyList(),
    val current_page: Int = 1,
    val total_pages: Int = 1,
)

@Serializable
class ComicDto(
    val id: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val banner_image_url: String? = null,
)

@Serializable
class ComicDetailsDto(
    val id: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val banner_image_url: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class GenreDto(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
class ChapterDto(
    val id: Long? = null,
    val name: String? = null,
    val coinPrice: Int? = null,
    val unlockAt: String? = null,
)

@Serializable
class ChapterPagesDto(
    val images: List<PageImageDto> = emptyList(),
    val coinPrice: Int? = null,
    val isPurchased: Boolean? = null,
)

@Serializable
class PageImageDto(
    val page: Int? = null,
    val src: String? = null,
)

@Serializable
class ApiErrorDto(
    val status: Int? = null,
    val message: String? = null,
)
