package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawChapterInfo(
    @SerialName("chapterId")
    val chapterId: Int,
    @SerialName("pageItem")
    val pageItem: List<RawPageItem>,
    @SerialName("projectId")
    val projectId: String,
)
