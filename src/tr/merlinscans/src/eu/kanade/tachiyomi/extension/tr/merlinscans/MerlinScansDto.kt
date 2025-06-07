package eu.kanade.tachiyomi.extension.tr.merlinscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val success: Boolean,
    val results: List<SearchResult>,
)

@Serializable
class SearchResult(
    val title: String,
    @SerialName("cover_image")
    val coverImage: String,
    val url: String,
    val categories: String = "",
)
