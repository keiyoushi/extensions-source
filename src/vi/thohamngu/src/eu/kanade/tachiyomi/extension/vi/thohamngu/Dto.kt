package eu.kanade.tachiyomi.extension.vi.thohamngu

import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val success: Boolean,
    val data: List<SearchResultDto>,
)

@Serializable
class SearchResultDto(
    val title: String,
    val link: String,
    val img: String? = null,
    val vote: String? = null,
    val star: Double? = null,
    val isocm: Int? = null,
    val cstatus: String? = null,
)
