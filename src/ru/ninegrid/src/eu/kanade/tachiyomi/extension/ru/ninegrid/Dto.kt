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
    private val id: Int,
    private val name: String,
    private val description: String? = null,
    private val publisherName: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
) {
    fun toSManga(apiBase: String): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = "$apiBase/series/${this@SeriesDto.id}/thumbnail"
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
