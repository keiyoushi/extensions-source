package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import kotlinx.serialization.Serializable

@Serializable
class ChapterDTO(
    val result: ResultChapter,
)

@Serializable
class Chapters(
    val numberChapter: String,
    val stringUpdateTime: String,
)

@Serializable
class ResultChapter(
    val chapters: List<Chapters>,

)
