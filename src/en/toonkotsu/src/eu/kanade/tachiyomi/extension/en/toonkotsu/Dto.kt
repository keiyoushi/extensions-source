package eu.kanade.tachiyomi.extension.en.toonkotsu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class BrowseDto(
    val items: List<SeriesDto>,
    val hasNextPage: Boolean,
)

@Serializable
class SeriesDto(
    private val hid: String,
    private val title: String,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val tags: List<String> = emptyList(),
    private val authors: List<String> = emptyList(),
    private val description: String? = null,
    private val alt: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/title/$hid"
        title = this@SeriesDto.title
        thumbnail_url = cover?.let { baseUrl + it }
        author = authors.joinToString().ifEmpty { null }
        genre = (listOfNotNull(type) + tags).joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "Ongoing" -> SManga.ONGOING
            "Complete" -> SManga.COMPLETED
            "Hiatus" -> SManga.ON_HIATUS
            "Canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@SeriesDto.description?.let { append(it) }
            if (alt.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                alt.joinTo(this, "\n") { "- $it" }
            }
        }.ifEmpty { null }
    }
}

@Serializable
class SeriesResponse(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
    val recs: List<SeriesDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val id: Long,
    private val label: String,
    private val at: Long? = null,
    private val group: GroupDto? = null,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/$id"
        name = label
        date_upload = at ?: 0L
        scanlator = group?.name
    }
}

@Serializable
class GroupDto(val name: String)

@Serializable
class ChapterResponse(val chapter: ChapterPagesDto)

@Serializable
class ChapterPagesDto(val pages: List<PageDto>)

@Serializable
class PageDto(
    val src: String,
    val k: String,
    val n: String,
)

@Serializable
class LimitDto(
    val error: String? = null,
    val code: String? = null,
    val reason: String? = null,
)

@Serializable
class UnlockBody(private val token: String)

@Serializable
class FacetsDto(
    val genres: List<FacetDto>,
    val types: List<FacetDto>,
)

@Serializable
class FacetDto(val name: String)
