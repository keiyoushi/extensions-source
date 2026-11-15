package eu.kanade.tachiyomi.extension.all.mangadraft.dto

import kotlinx.serialization.Serializable

@Serializable
class MangaDraftCatalogResponseDto(
    val data: List<MangaDraftCatalogProjectDto> = emptyList(),
)

@Serializable
class MangaDraftCatalogProjectDto(
    val name: String,
    val avatar: String? = null,
    val genres: String? = null,
    val description: String? = null,
    val url: String,
)
