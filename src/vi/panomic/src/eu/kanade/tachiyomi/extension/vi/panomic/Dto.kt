package eu.kanade.tachiyomi.extension.vi.panomic

import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val data: List<SearchResultDto> = emptyList(),
)

@Serializable
class SearchResultDto(
    val title: String,
    val link: String,
    val img: String? = null,
)

@Serializable
class FilterData(
    val genres: List<FilterOption>,
    val groups: List<FilterOption>,
    val series: List<FilterOption>,
    val keywords: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val uri: String,
)
