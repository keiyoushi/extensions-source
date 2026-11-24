package eu.kanade.tachiyomi.extension.tr.sunsetmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogResponse(
    val success: Boolean = false,
    val data: CatalogData? = null,
)

@Serializable
class CatalogData(
    val html: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class SearchResult(
    val title: String = "",
    val url: String = "",
    val thumbnail: String? = null,
)
