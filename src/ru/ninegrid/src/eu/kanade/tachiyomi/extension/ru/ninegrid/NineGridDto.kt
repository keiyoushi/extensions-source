package eu.kanade.tachiyomi.extension.ru.ninegrid

import kotlinx.serialization.Serializable

@Serializable
data class SeriesListResponse(
    val content: List<SeriesDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val publisherName: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
)

@Serializable
data class IssuesResponse(
    val issues: List<IssueDto>,
)

@Serializable
data class IssueDto(
    val id: Int,
    val number: String,
    val name: String? = null,
    val translations: List<TranslationDto>,
)

@Serializable
data class TranslationDto(
    val id: String,
    val teamNames: List<String> = emptyList(),
    val pageCount: Int = 0,
    val createdAt: String? = null,
)

@Serializable
data class PagesResponse(
    val pages: List<PageDto>,
)

@Serializable
data class PageDto(
    val index: Int,
    val url: String,
)
