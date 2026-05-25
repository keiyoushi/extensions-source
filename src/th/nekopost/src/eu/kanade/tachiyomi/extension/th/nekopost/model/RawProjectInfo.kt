package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectInfo(
    @SerialName("ListCate")
    val projectCategoryUsed: List<RawProjectCategory>?,
    @SerialName("ListChapter")
    val projectChapterList: List<RawProjectChapter>?,
    @SerialName("Project")
    val projectInfo: RawProjectInfoData,
)
