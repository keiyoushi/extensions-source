package eu.kanade.tachiyomi.extension.pt.plumacomics

import kotlinx.serialization.Serializable

@Serializable
class SearchDto(
    val results: List<MangaDto>,
)

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    val coverPath: String,
)

@Serializable
class ChapterDto(
    val seriesId: Int,
    val chapterId: Int,
    val chapterNumber: Int,
)

@Serializable
data class PagesList(
    val pages: List<PageItem>,
)

@Serializable
data class PageItem(
    val i: Int,
    val u: String,
)
