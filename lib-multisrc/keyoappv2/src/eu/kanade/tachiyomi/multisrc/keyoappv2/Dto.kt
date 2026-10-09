package eu.kanade.tachiyomi.multisrc.keyoappv2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChaptersResponseDto(
    @SerialName("rows_html") val rowsHtml: String,
)

@Serializable
class AjaxSearchResponseDto(
    val html: String,
    @SerialName("has_more") val hasMore: Boolean,
)
