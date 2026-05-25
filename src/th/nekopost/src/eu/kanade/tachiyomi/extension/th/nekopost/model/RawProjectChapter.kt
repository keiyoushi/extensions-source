package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectChapter(
    @SerialName("ChapterID")
    val chapterId: String?,
    @SerialName("ChapterNo")
    val chapterNo: String,
    @SerialName("ChapterName")
    val chapterName: String,
    @SerialName("PublishDate")
    val publishDate: RawValidationString,
    @SerialName("CreateDate")
    val createDate: RawValidationString,
    @SerialName("ProviderName")
    val providerName: String,
)
