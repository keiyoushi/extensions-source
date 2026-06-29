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
    val cstatus: String? = null,
)
