package eu.kanade.tachiyomi.extension.en.nexcomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class SeriesListDto(
    val data: List<SeriesDto>,
)

@Serializable
class PopularDto(
    val data: List<PopularEntryDto>,
)

@Serializable
class PopularEntryDto(
    val slug: String,
)

@Serializable
class CatalogDto(
    val series: List<SeriesDto>,
    val chapters: List<ChapterDto>,
)

@Serializable
class SeriesDto(
    val slug: String,
    val title: String,
    val type: String? = null,
    val status: String? = null,
    private val description: String? = null,
    val alternativeTitles: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    private val authors: List<String> = emptyList(),
    private val artists: List<String> = emptyList(),
    private val avatar: AssetDto? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = avatar?.let { baseUrl + it.url }
        author = authors.joinToString().ifEmpty { null }
        artist = artists.joinToString().ifEmpty { null }
        genre = (listOfNotNull(type) + genres + tags).distinct().joinToString()
        status = when (this@SeriesDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@SeriesDto.description?.let { append(it) }
            alternativeTitles.filter { it != title }.takeIf { it.isNotEmpty() }?.let {
                append("\n\nAlternative Titles:\n", it.joinToString("\n"))
            }
        }.trim()
    }
}

@Serializable
class AssetDto(
    val url: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    val seriesSlug: String,
    private val chapterNumber: Float,
    private val title: String? = null,
    val locked: Boolean = false,
    private val publishedAt: Long? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$seriesSlug/${id.substringAfter(':')}"
        name = buildString {
            append("Chapter ", chapterNumber.toString().removeSuffix(".0"))
            title?.takeIf { it.isNotBlank() }?.let { append(" - ", it) }
        }
        chapter_number = chapterNumber
        date_upload = publishedAt ?: 0L
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val pageNumber: Int,
    val url: String,
)
