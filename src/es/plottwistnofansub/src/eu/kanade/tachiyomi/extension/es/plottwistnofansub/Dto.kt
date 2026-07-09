package eu.kanade.tachiyomi.extension.es.plottwistnofansub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterAjaxResponse(
    val success: Boolean = false,
    val data: ChapterAjaxData = ChapterAjaxData(),
)

@Serializable
class ChapterAjaxData(
    val html: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
)
