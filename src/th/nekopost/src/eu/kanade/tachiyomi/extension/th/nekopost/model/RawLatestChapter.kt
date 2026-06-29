package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawLatestChapterList(
    @SerialName("listChapter")
    val listChapter: List<RawLatestChapter>? = null,
)

@Serializable
class RawLatestChapter(
    @SerialName("pid")
    val pid: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("coverVersion")
    val coverVersion: Int,
    @SerialName("status")
    val status: String,
)
