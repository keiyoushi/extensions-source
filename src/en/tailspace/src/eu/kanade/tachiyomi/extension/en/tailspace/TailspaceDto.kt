package eu.kanade.tachiyomi.extension.en.tailspace

class ComicDetails(
    val id: Long,
    val name: String,
    val description: String?,
    val category: String?,
    val isSfw: Boolean?,
    val state: String?,
    val thumbnailVersion: Int?,
    val artists: String?,
    val tags: List<String>,
    val pages: List<PageItem>,
    val updated: Long?,
    val published: Long?,
    val previousComic: LinkedComic?,
    val nextComic: LinkedComic?,
)

class PageItem(
    val token: String,
    val pageNumber: Int,
)

class LinkedComic(
    val id: Long,
    val name: String,
)

class TagDto(
    val id: Int,
    val name: String,
    val category: String?,
)
