package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val data: List<SearchEntryDto> = emptyList(),
    val success: Boolean = false,
)

@Serializable
class SearchEntryDto(
    val cstatus: String? = null,
    val img: String? = null,
    val isocm: Int? = null,
    val link: String? = null,
    val star: Float? = null,
    val title: String? = null,
    val vote: String? = null,
)
