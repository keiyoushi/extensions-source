package eu.kanade.tachiyomi.extension.all.ninenineninehentai

import kotlinx.serialization.Serializable

@Serializable
data class GraphQL<T>(
    val variables: T,
    val query: String,
)

@Serializable
data class PopularVariables(
    val size: Int,
    val page: Int,
    val dateRange: Int,
    val language: String,
)

@Serializable
data class SearchVariables(
    val size: Int,
    val page: Int,
    val search: SearchPayload,
)

@Serializable
data class SearchPayload(
    val query: String?,
    val language: String,
    val sortBy: String?,
    val format: String?,
    val tags: List<String>?,
    val excludeTags: List<String>?,
    val pagesRangeStart: Int?,
    val pagesRangeEnd: Int?,
)

@Serializable
data class IdVariables(val id: String)
