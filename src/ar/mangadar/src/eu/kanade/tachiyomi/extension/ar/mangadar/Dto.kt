package eu.kanade.tachiyomi.extension.ar.mangadar

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val success: Boolean = false,
    val data: List<SearchMangaItemDto> = emptyList(),
)

@Serializable
data class SearchMangaItemDto(
    val id: Int,
    val title: String,
    val url: String,
    val cover: String,
)
