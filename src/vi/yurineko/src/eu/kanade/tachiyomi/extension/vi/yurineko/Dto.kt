package eu.kanade.tachiyomi.extension.vi.yurineko

import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
    val page: Int,
    val lastPage: Int,
)

@Serializable
class ChapterListDto(
    val data: List<ChapterDto>,
    val totalPages: Int? = null,
    val lastPage: Int? = null,
) {
    val pageCount: Int
        get() = totalPages ?: lastPage ?: 1
}

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
)

@Serializable
class MangaDetailsDto(
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val status: String? = null,
    val tags: List<TagDto> = emptyList(),
    val linkedAuthors: List<LinkedPersonDto> = emptyList(),
    val linkedArtists: List<LinkedPersonDto> = emptyList(),
)

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class LinkedPersonDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val chapterNumber: String,
    val order: Double? = null,
    val publishedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
class FilterData(
    val doujins: List<FilterOption>,
    val authors: List<FilterOption>,
    val artists: List<FilterOption>,
    val tags: List<FilterOption>,
    val groups: List<FilterOption>,
    val couples: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)

@Serializable
class NamedCategoryItemDto(
    val id: String,
    val name: String,
)

@Serializable
class DoujinItemDto(
    val id: String,
    val name: String? = null,
    val title: String? = null,
) {
    val displayName: String
        get() = name ?: requireNotNull(title)
}

@Serializable
class SlugCategoryItemDto(
    val id: String,
    val name: String,
    val slug: String,
)

@Serializable
class CategoryMetaDto(
    val totalPages: Int,
)

@Serializable
class DoujinListDto(
    val data: List<DoujinItemDto>,
    val lastPage: Int? = null,
    val meta: CategoryMetaDto? = null,
) {
    val pageCount: Int
        get() = meta?.totalPages ?: lastPage ?: 1
}

@Serializable
class SlugCategoryListDto(
    val data: List<SlugCategoryItemDto>,
    val lastPage: Int,
) {
    val pageCount: Int
        get() = lastPage
}

@Serializable
class GroupListDto(
    val items: List<NamedCategoryItemDto>,
    val meta: CategoryMetaDto,
)
