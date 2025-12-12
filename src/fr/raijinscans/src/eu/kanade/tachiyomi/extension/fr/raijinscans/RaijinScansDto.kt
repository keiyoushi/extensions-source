package eu.kanade.tachiyomi.extension.fr.raijinscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LatestUpdatesDto(
    val success: Boolean,
    val data: LatestUpdatesDataDto,
)

@Serializable
class LatestUpdatesDataDto(
    @SerialName("manga_html") val mangaHtml: String,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ImageResponseDto(
    val data: ImageResponseDataDto,
)

@Serializable
class ImageResponseDataDto(
    val u: List<String>,
)
