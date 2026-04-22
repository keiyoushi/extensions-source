package eu.kanade.tachiyomi.extension.ru.ninegrid

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class SeriesListResponse(
    val content: List<SeriesDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SeriesDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val publisherName: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        description = this@SeriesDto.description
        author = publisherName
        genre = genres.joinToString()
        status = when (this@SeriesDto.status) {
            "Continuing" -> SManga.ONGOING
            "Ended" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class IssuesResponse(
    val issues: List<IssueDto>,
)

@Serializable
class IssueDto(
    val id: Int,
    val number: String,
    val name: String? = null,
    val translations: List<TranslationDto>,
)

@Serializable
class TranslationDto(
    val id: String,
    val teamNames: List<String> = emptyList(),
    val pageCount: Int = 0,
    val createdAt: String? = null,
)

@Serializable
class PagesResponse(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val index: Int,
    val url: String,
)
