package eu.kanade.tachiyomi.extension.en.weebdex.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterListDto(
    val data: List<ChapterDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page * limit < total
}

@Serializable
data class ChapterDto(
    val id: String,
    val title: String? = null,
    val chapter: String? = null,
    val published_at: String = "",
    val data: List<PageData>? = null,
    val data_optimized: List<PageData>? = null,
    val relationships: ChapterRelationshipsDto? = null,
)

@Serializable
data class ChapterRelationshipsDto(
    val groups: List<NamedEntity> = emptyList(),
)

@Serializable
data class PageData(
    val name: String? = null,
)
