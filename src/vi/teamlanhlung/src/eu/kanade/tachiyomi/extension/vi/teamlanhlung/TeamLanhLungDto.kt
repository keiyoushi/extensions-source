package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val data: List<SearchEntryDto>,
    val success: Boolean,
)

@Serializable
class SearchEntryDto(
    val cstatus: String = "",
    val img: String = "",
    val isocm: Int = 0,
    val link: String = "",
    val star: Float = 0f,
    val title: String = "",
    val vote: String = "",
)
