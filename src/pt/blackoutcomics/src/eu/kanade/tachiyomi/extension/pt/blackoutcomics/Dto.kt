package eu.kanade.tachiyomi.extension.pt.blackoutcomics

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val items: List<SearchItem> = emptyList(),
)

@Serializable
class SearchItem(
    @SerialName("PJT_ID") private val id: Int,
    @SerialName("PJT_NAME") private val name: String,
    @SerialName("PJT_IMG_PR") private val imgPr: String? = null,
    @SerialName("PJT_IMG_PR_URL") private val imgUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        url = "/comics/$id"
        thumbnail_url = imgUrl ?: ("$baseUrl/" + imgPr)
    }
}
