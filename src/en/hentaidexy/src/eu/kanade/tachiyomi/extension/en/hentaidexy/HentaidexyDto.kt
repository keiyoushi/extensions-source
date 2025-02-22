package eu.kanade.tachiyomi.extension.en.hentaidexy

import kotlinx.serialization.Serializable

@Serializable
data class ApiMangaResponse(
    val page: Int,
    val totalPages: Int,
    val mangas: Array<Manga>,
)

@Serializable
data class Manga(
    val _id: String,
    val title: String,
    val altTitles: Array<String>?,
    val coverImage: String,
    val summary: String = "Unknown",
    val authors: Array<String>?,
    val genres: Array<String>?,
    val status: String,
)

@Serializable
data class MangaDetails(
    val manga: Manga,
)

@Serializable
data class ApiChapterResponse(
    val page: Int,
    val totalPages: Int,
    val chapters: MutableList<Chapter>,
)

@Serializable
data class Chapter(
    val _id: String,
    val serialNumber: Float,
    val createdAt: String,
)

@Serializable
data class PageList(
    val chapter: ChapterPageData,
)

@Serializable
data class ChapterPageData(
    val images: Array<String>,
)
