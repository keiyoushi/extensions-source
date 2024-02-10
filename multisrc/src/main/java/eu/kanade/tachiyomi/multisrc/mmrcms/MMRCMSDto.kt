package eu.kanade.tachiyomi.multisrc.mmrcms

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultDto(
    val suggestions: List<SuggestionDto>,
)

@Serializable
data class SuggestionDto(
    val value: String,
    val data: String,
)
