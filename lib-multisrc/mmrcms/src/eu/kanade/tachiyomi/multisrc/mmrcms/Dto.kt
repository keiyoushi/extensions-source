package eu.kanade.tachiyomi.multisrc.mmrcms

import kotlinx.serialization.Serializable

@Serializable
class SearchResultDto(
    val suggestions: List<SuggestionDto>,
)

@Serializable
class SuggestionDto(
    val value: String,
    val data: String,
)
