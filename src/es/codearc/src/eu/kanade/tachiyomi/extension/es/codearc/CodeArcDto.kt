package eu.kanade.tachiyomi.extension.es.codearc

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val items: List<SearchItemDto>,
)

@Serializable
data class SearchItemDto(
    val slug: String,
    val titulo: String,
    val portada: String? = null,
)
