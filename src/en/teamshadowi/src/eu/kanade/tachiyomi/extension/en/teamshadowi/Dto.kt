package eu.kanade.tachiyomi.extension.en.teamshadowi

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SeriesResponse(
    val data: List<Series>,
    val hasMore: Boolean = false,
)

@Serializable
class SearchResponse(
    val series: List<Series> = emptyList(),
)

@Serializable
class Series(
    private val title: String,
    private val slug: String,
    @SerialName("thumbnail_url") private val thumbnailUrl: String? = null,
    private val status: String? = null,
    private val description: String? = null,
    private val genres: List<String>? = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@Series.title
        url = "/series/${this@Series.slug}"
        thumbnail_url = this@Series.thumbnailUrl
        status = when (this@Series.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = this@Series.description
        genre = this@Series.genres?.joinToString()
    }
}

@Serializable
class PublicDataSeries(
    val series: SeriesDetails,
    val chapters: List<ChapterData> = emptyList(),
)

@Serializable
class SeriesDetails(
    val title: String,
    val description: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val status: String? = null,
    val genres: List<String>? = emptyList(),
    val tags: List<String>? = emptyList(),
)

@Serializable
class ChapterData(
    val id: String,
    val number: Float,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class PublicDataChapter(
    val pages: List<String>,
)
