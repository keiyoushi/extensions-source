package eu.kanade.tachiyomi.extension.all.comicgrowl

import kotlinx.serialization.Serializable

@Serializable
class PageResponse(
    val totalPages: Int,
    val result: List<PageResponseResult>,
)

@Serializable
class PageResponseResult(
    val imageUrl: String,
    val scramble: String,
    val sort: Int,
)
