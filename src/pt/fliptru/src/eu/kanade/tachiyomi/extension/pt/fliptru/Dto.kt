package eu.kanade.tachiyomi.extension.pt.fliptru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComicSeriesLd(
    @SerialName("@type") val type: String = "",
    val name: String = "",
    val description: String = "",
    val image: String = "",
    val genre: String = "",
    val author: AuthorLd? = null,
)

@Serializable
data class AuthorLd(
    val name: String = "",
)

@Serializable
data class SearchResultDto(
    val label: String = "",
    val url: String = "",
)
