package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawProjectInfo(
    @SerialName("projectInfo")
    val info: RawProjectInfoData,
)

@Serializable
class RawProjectInfoData(
    @SerialName("Project")
    val project: RawProject,
    @SerialName("ListCate")
    val category: List<RawProjectCategory>?,
    @SerialName("ListChapter")
    val chapter: List<RawProjectChapter>?,
)
