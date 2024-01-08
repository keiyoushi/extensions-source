package eu.kanade.tachiyomi.extension.en.allanime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphQL<T>(
    val variables: T,
    val query: String,
)

@Serializable
data class PopularVariables(
    val type: String,
    val size: Int,
    val dateRange: Int,
    val page: Int,
    val allowAdult: Boolean,
    val allowUnknown: Boolean,
)

@Serializable
data class SearchVariables(
    val search: SearchPayload,
    @SerialName("limit") val size: Int,
    val page: Int,
    val translationType: String,
    val countryOrigin: String,
)

@Serializable
data class SearchPayload(
    val query: String?,
    val sortBy: String?,
    val genres: List<String>?,
    val excludeGenres: List<String>?,
    val isManga: Boolean,
    val allowAdult: Boolean,
    val allowUnknown: Boolean,
)

@Serializable
data class IDVariables(
    val id: String,
)

@Serializable
data class ChapterListVariables(
    val id: String,
    val chapterNumStart: Float,
    val chapterNumEnd: Float,
)

@Serializable
data class PageListVariables(
    val id: String,
    val chapterNum: String,
    val translationType: String,
)
