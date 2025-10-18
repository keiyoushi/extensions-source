package eu.kanade.tachiyomi.extension.en.weebdex.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaListDto(
    val data: List<MangaDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page * limit < total
}

@Serializable
data class MangaDto(
    val id: String,
    val title: String,
    val description: String = "",
    val status: String? = null,
    val relationships: RelationshipsDto? = null,
)

@Serializable
data class RelationshipsDto(
    val authors: List<NamedEntity> = emptyList(),
    val artists: List<NamedEntity> = emptyList(),
    val tags: List<NamedEntity> = emptyList(),
    val cover: CoverDto? = null,
)

@Serializable
data class NamedEntity(
    val id: String,
    val name: String,
)

@Serializable
data class CoverDto(
    val id: String,
    val ext: String = ".jpg",
)
