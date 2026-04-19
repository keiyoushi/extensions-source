package eu.kanade.tachiyomi.extension.es.anzmanga

import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val suggestions: List<SearchSuggestionDto> = emptyList(),
)

@Serializable
class SearchSuggestionDto(
    val value: String,
    val data: String,
)
