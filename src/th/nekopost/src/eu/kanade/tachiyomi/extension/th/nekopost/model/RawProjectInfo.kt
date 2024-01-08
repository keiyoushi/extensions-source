package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectInfo(
    @SerialName("code")
    val code: Int,
    @SerialName("listCate")
    val projectCategoryUsed: List<RawProjectCategory>?,
    @SerialName("listChapter")
    val projectChapterList: List<RawProjectChapter>?,
    @SerialName("projectInfo")
    val projectInfo: RawProjectInfoData,
)
