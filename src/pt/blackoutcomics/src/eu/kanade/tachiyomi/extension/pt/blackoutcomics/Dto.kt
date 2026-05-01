package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val items: List<SearchItem> = emptyList(),
)

@Serializable
class SearchItem(
    @SerialName("PJT_ID") val id: Int,
    @SerialName("PJT_NAME") val name: String,
    @SerialName("PJT_IMG_PR") val imgPr: String? = null,
    @SerialName("PJT_IMG_PR_URL") val imgUrl: String? = null,
)
