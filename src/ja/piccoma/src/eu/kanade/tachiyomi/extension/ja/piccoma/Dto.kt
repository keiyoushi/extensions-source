package eu.kanade.tachiyomi.extension.ja.piccoma

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val data: SearchDataDto,
)

@Serializable
class SearchDataDto(
    val products: List<SearchProductDto>,
    @SerialName("total_page") val totalPage: Int,
)

@Serializable
class SearchProductDto(
    val id: Int,
    val title: String,
    val img: String,
)

@Serializable
class PDataDto(
    val img: List<PDataImageDto>?,
    val contents: List<PDataImageDto>?,
    val isScrambled: Boolean,
)

@Serializable
class PDataImageDto(
    val path: String,
)
