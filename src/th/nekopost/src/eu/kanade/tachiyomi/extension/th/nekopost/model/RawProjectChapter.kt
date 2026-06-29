package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawProjectChapter(
    @SerialName("ChapterID")
    val chapterId: Int,
    @SerialName("ChapterNo")
    val chapterNo: String,
    @SerialName("ChapterName")
    val chapterName: String,
    @SerialName("PublishDate")
    val publishDate: RawValidString,
    @SerialName("ProviderName")
    val providerName: String,
)
