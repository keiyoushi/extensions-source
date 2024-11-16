package eu.kanade.tachiyomi.extension.zh.komiic

import kotlinx.serialization.Serializable

@Serializable
class Payload<T>(
    val operationName: String,
    val variables: T,
    val query: String,
)

@Serializable
data class MangaListPagination(
    val limit: Int,
    val offset: Int,
    val orderBy: String,
    val status: String,
    val asc: Boolean,
)

@Serializable
data class HotComicsVariables(
    val pagination: MangaListPagination,
)

@Serializable
data class RecentUpdateVariables(
    val pagination: MangaListPagination,
)

@Serializable
data class SearchVariables(
    val keyword: String,
)

@Serializable
data class ComicByIdVariables(
    val comicId: String,
)

@Serializable
data class ChapterByComicIdVariables(
    val comicId: String,
)

@Serializable
data class ImagesByChapterIdVariables(
    val chapterId: String,
)
