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
    val data: List<SeriesItem>,
    val meta: Meta? = null,
)

@Serializable
data class SeriesItem(
    val id: Int,
    val data: SeriesData,
)

@Serializable
data class SeriesData(
    val slug: String?,
    val title: String,
    val author: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
    val genres: List<GenreData>? = null,
)

@Serializable
data class GenreData(
    val data: GenreInfo,
)

@Serializable
data class GenreInfo(
    val name: String,
)

@Serializable
data class Meta(
    val page: Int? = null,
    @SerialName("lastPage") val lastPage: Int? = null,
)

@Serializable
data class SeriesDetailResponse(
    val data: SeriesItem,
)

@Serializable
data class ChapterItem(
    val data: ChapterData,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    @SerialName("dataImages") val dataImages: Map<String, String>? = null,
)

@Serializable
data class ChapterData(
    val index: Float,
    val title: String? = null,
)

@Serializable
data class ChapterListResponse(
    val data: List<ChapterItem>,
)

@Serializable
data class ChapterDetailResponse(
    val data: ChapterItem,
)

fun SeriesItem.toSManga(): SManga = SManga.create().apply {
    url = "/series/${data.slug ?: id}"
    title = data.title
    thumbnail_url = data.coverImage
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

private fun formatChapterNumber(number: Float): String {
    return chapterNumberFormatter.format(number)
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)

private fun parseChapterDate(dateString: String): Long {
    if (dateString.isBlank()) return 0L
    return dateFormat.tryParse(dateString) ?: 0L
}
