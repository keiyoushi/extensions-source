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
    val chapterId: Long,
    val chapterToken: String,
    val baseSeed: List<Long>,
)
