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

@Serializable
class MangaDTO(
    val result: Result,
)

@Serializable
class Result(
    val p: Int? = null,
    val next: Boolean? = null,
    val data: List<Manga>,
)

@Serializable
class Manga(
    val name: String,
    val photo: String,
    val nameEn: String,
)

@Serializable
class SearchDTO(
    val result: List<Manga>,
)

@Serializable
class ChapterWrapper(
    val headers: Map<String, String> = emptyMap(),
    val body: ChapterBody,
)

@Serializable
class ChapterBody(
    val result: ResultContent,
)

@Serializable
class ResultContent(
    val data: List<String>,
)
