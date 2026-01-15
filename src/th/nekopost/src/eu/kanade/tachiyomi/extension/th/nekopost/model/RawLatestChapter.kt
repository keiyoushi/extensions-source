package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class RawLatestChapterList(
    val listChapter: List<RawLatestChapter>? = null,
)

@Serializable
data class RawLatestChapter(
    val noNewChapter: Int,
    val pid: Int,
    val projectName: String,
    val chapterId: Int,
    val chapterNo: String,
    val chapterName: String,
    val releaseDate: String,
    val cover: String,
    val editorId: Int,
    val editorName: String,
    val coverVersion: Int,
    val status: String,
    val projectType: String,
)
