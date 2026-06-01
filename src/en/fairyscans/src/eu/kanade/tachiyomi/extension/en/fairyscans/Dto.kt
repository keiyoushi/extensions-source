package eu.kanade.tachiyomi.extension.en.fairyscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BrowseResponseDto(
    val success: Boolean,
    private val data: BrowseDataDto? = null,
    private val html: String? = null,
    @SerialName("has_more") private val hasMoreField: Boolean? = null,
) {
    val hasMore get() = hasMoreField ?: data?.hasMore ?: false
    val gridHtml get() = data?.gridHtml ?: html ?: ""
}

@Serializable
class BrowseDataDto(
    @SerialName("grid_html") val gridHtml: String? = null,
    @SerialName("has_more") val hasMore: Boolean? = null,
)

@Serializable
class ReaderDto(
    private val sources: List<ReaderSourceDto>,
) {
    val images get() = sources.firstOrNull()?.images ?: emptyList()
}

@Serializable
class ReaderSourceDto(
    val images: List<String>,
)
