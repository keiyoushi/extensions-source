package eu.kanade.tachiyomi.extension.all.comicgrowl

import kotlinx.serialization.Serializable

@Serializable
data class PageResponse(
    val totalPages: Int,
    val result: List<PageResponseResult>,
)

@Serializable
data class PageResponseResult(
    val imageUrl: String,
    val scramble: String,
    val sort: Int,
)
