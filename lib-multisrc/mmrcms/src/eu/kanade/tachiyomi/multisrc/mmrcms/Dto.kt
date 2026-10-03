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

@Serializable
class FilterData(
    val categories: List<Pair<String, String>>,
    val statuses: List<Pair<String, String>>,
    val tags: List<Pair<String, String>>,
    val sortOptions: List<Pair<String, String>>,
)
