package eu.kanade.tachiyomi.extension.en.cartoonpornto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    @SerialName("data") val data: List<SearchItem> = emptyList(),
)

@Serializable
class SearchItem(
    @SerialName("title") val title: String = "",
    @SerialName("url") val url: String = "",
)
