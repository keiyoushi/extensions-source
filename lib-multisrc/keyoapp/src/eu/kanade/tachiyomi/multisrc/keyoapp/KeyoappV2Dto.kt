package eu.kanade.tachiyomi.multisrc.keyoapp

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
