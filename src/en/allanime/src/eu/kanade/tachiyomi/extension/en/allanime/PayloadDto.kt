package eu.kanade.tachiyomi.extension.en.allanime

import kotlinx.serialization.Serializable

@Serializable
class GraphQL<T>(
    private val variables: T,
    private val query: String,
)

@Serializable
class PopularVariables(
    private val type: String,
    private val size: Int,
    private val dateRange: Int,
    private val page: Int,
    private val allowAdult: Boolean,
    private val allowUnknown: Boolean,
)

@Serializable
class SearchVariables(
    private val search: SearchPayload,
    private val size: Int,
    private val page: Int,
    private val translationType: String,
    private val countryOrigin: String,
)

@Serializable
class SearchPayload(
    private val query: String?,
    private val sortBy: String?,
    private val genres: List<String>?,
    private val excludeGenres: List<String>?,
    private val isManga: Boolean,
    private val allowAdult: Boolean,
    private val allowUnknown: Boolean,
)

@Serializable
class IDVariables(
    private val id: String,
)

@Serializable
class ChapterListVariables(
    private val id: String,
    private val chapterNumStart: Float,
    private val chapterNumEnd: Float,
)

@Serializable
class PageListVariables(
    private val id: String,
    private val chapterNum: String,
    private val translationType: String,
)
