package eu.kanade.tachiyomi.extension.ja.alphapolis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ViewerResponse(
    val page: ViewerPage?,
)

@Serializable
class ViewerPage(
    val images: List<ViewerImage>,
)

@Serializable
class ViewerImage(
    val url: String,
)

@Suppress("unused")
@Serializable
class ViewerRequestBody(
    @SerialName("episode_no") val episodeNo: Int,
    @SerialName("hide_page") val hidePage: Boolean,
    @SerialName("manga_sele_id") val mangaSeleId: Int,
    val preview: Boolean,
    val resolution: String,
)
