package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class SeriesListResponse(
    val data: List<SeriesItem> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class SeriesItem(
    val id: Int = 0,
    val data: SeriesData = SeriesData(),
    // Some APIs return coverImage at root level too
    @SerialName("coverImage") val rootCoverImage: String? = null,
)

@Serializable
data class SeriesData(
    val slug: String? = null,
    val title: String = "",
    val author: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
    // Alternative field names the API might use
    @SerialName("cover_image") val coverImageAlt: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("image") val image: String? = null,
    val genres: List<GenreData>? = null,
)

@Serializable
data class GenreData(
    val data: GenreInfo = GenreInfo(),
)

@Serializable
data class GenreInfo(
    val name: String = "",
)

@Serializable
data class Meta(
    val page: Int? = null,
    @SerialName("lastPage") val lastPage: Int? = null,
)

@Serializable
data class SeriesDetailResponse(
    val data: SeriesItem = SeriesItem(),
)

@Serializable
data class ChapterItem(
    val data: ChapterData = ChapterData(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ChapterData(
    val index: Float = 0f,
    val title: String? = null,
    val images: List<String>? = null,
)

@Serializable
data class ChapterListResponse(
    val data: List<ChapterItem> = emptyList(),
)

@Serializable
data class ChapterDetailResponse(
    val data: ChapterItem = ChapterItem(),
)

fun SeriesItem.toSManga(): SManga = SManga.create().apply {
    url = "/series/${data.slug ?: id}"
    title = data.title
    // Try multiple possible cover image sources
    thumbnail_url = data.coverImage
        ?: data.coverImageAlt
        ?: data.thumbnail
        ?: data.cover
        ?: data.image
        ?: rootCoverImage
    author = data.author
    description = data.synopsis
    genre = data.genres?.joinToString { it.data.name } ?: ""
    status = when (data.status?.lowercase()) {
        "ongoing", "on going" -> SManga.ONGOING
        "completed", "complete" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled", "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    initialized = true
}

fun ChapterItem.toSChapter(seriesSlug: String?): SChapter = SChapter.create().apply {
    val chapterIndex = data.index
    val formattedIndex = formatChapterNumber(chapterIndex)
    url = "/series/$seriesSlug/chapters/$chapterIndex"
    name = if (data.title.isNullOrBlank()) {
        "Chapter $formattedIndex"
    } else {
        "Chapter $formattedIndex: ${data.title}"
    }
    date_upload = parseChapterDate(createdAt ?: updatedAt ?: "")
    chapter_number = chapterIndex
}

private val chapterNumberFormatter = DecimalFormat("#.##")

private fun formatChapterNumber(number: Float): String = chapterNumberFormatter.format(number)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)

private fun parseChapterDate(dateString: String): Long {
    if (dateString.isBlank()) return 0L
    return dateFormat.tryParse(dateString) ?: 0L
}
