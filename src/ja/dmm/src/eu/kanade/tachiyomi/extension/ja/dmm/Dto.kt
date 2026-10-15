package eu.kanade.tachiyomi.extension.ja.dmm

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class DetailsResponse(
    private val series: Series,
    private val volumes: Volumes,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DetailsResponse.series.title
        author = this@DetailsResponse.volumes.author?.joinToString { it.name }
        description = volumes.synopsis?.let { Jsoup.parse(it).text() }
        genre = buildList {
            volumes.genre?.mapTo(this) { it.name }
            volumes.publisher?.name?.let { add(it) }
            volumes.label?.name?.let { add(it) }
            volumes.category?.name?.let { add(it) }
        }.takeIf { it.isNotEmpty() }?.joinToString()
        status = if (series.status?.isCompletion == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = volumes.imageUrls?.pl
    }
}

@Serializable
class Series(
    val title: String,
    val status: SeriesStatus?,
)

@Serializable
class SeriesStatus(
    @SerialName("is_completion") val isCompletion: Boolean,
)

@Serializable
class Volumes(
    @SerialName("image_urls") val imageUrls: ImageUrls?,
    val author: List<Author>?,
    val genre: List<Genre>?,
    val synopsis: String?,
    val publisher: Publisher?,
    val label: Label?,
    val category: Category?,
)

@Serializable
class ImageUrls(
    val pl: String?,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Publisher(
    val name: String,
)

@Serializable
class Label(
    val name: String,
)

@Serializable
class Category(
    val name: String,
)

@Serializable
class ChapterResponse(
    @SerialName("volume_books") val volumeBooks: List<VolumeBook>,
    val pager: Pager,
)

@Serializable
class Pager(
    @SerialName("total_count") val totalCount: Int,
)

@Serializable
class VolumeBook(
    @SerialName("content_id") val contentId: String,
    private val title: String,
    @SerialName("volume_number") private val volumeNumber: Int?,
    @SerialName("content_publish_date") private val contentPublishDate: String?,
    @SerialName("free_streaming_url") private val freeStreamingUrl: List<String>?,
    @SerialName("tachiyomi_url") private val tachiyomiUrl: String?,
    private val purchased: Purchased?,
) {
    val isLocked: Boolean
        get() = purchased == null && freeStreamingUrl.isNullOrEmpty()

    val isPreview: Boolean
        get() = isLocked && !tachiyomiUrl.isNullOrEmpty()

    fun toSChapter(seriesId: String): SChapter = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isPreview) "(Preview) " else ""
        url = contentId
        name = lock + preview + title
        date_upload = Instant.tryParse(contentPublishDate)
        chapter_number = volumeNumber?.toFloat() ?: -1f
        memo = buildJsonObject {
            put("seriesId", seriesId)
            val viewerUrl = purchased?.streamingUrl ?: freeStreamingUrl?.firstOrNull() ?: tachiyomiUrl?.takeIf { it.isNotEmpty() }
            viewerUrl?.let { put("viewerUrl", it) }
        }
    }
}

@Serializable
class Purchased(
    @SerialName("streaming_url") val streamingUrl: String,
)
