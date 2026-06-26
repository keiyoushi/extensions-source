package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SeriesListResponse(
    val data: List<SeriesItem>,
    val meta: Meta? = null,
)

@Serializable
class SeriesItem(
    private val id: Int,
    private val data: SeriesData,
) {
    fun toSManga(): SManga = SManga.create().apply {
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
}

@Serializable
class SeriesData(
    val slug: String?,
    val title: String,
    val author: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val coverImage: String? = null,
    val genres: List<GenreData>? = null,
)

@Serializable
class GenreData(
    val data: GenreInfo,
)

@Serializable
class GenreInfo(
    val name: String,
)

@Serializable
class Meta(
    val page: Int? = null,
    val lastPage: Int? = null,
)

@Serializable
class SeriesDetailResponse(
    val data: SeriesItem,
)

@Serializable
class ChapterItem(
    private val data: ChapterData,
    private val createdAt: String? = null,
    private val updatedAt: String? = null,
    private val chapterIndex: Float? = null,
) {
    fun toSChapter(seriesSlug: String?): SChapter = SChapter.create().apply {
        val index = (data.index ?: chapterIndex)!!
        val formattedIndex = chapterNumberFormatter.format(index)
        url = "/series/$seriesSlug/chapter/$formattedIndex"
        name = if (data.title.isNullOrBlank()) {
            "Chapter $formattedIndex"
        } else {
            "Chapter $formattedIndex: ${data.title}"
        }
        date_upload = parseChapterDate(createdAt ?: updatedAt ?: "")
        chapter_number = index
    }

    fun toPageList(): List<Page> = data.images?.mapIndexed { index, imageUrl ->
        Page(index, "", imageUrl)
    } ?: emptyList()
}

@Serializable
class ChapterData(
    val index: Float? = null,
    val title: String? = null,
    val images: List<String>? = null,
)

@Serializable
class ChapterListResponse(
    val data: List<ChapterItem>,
)

@Serializable
class ChapterDetailResponse(
    val data: ChapterItem,
)

private val chapterNumberFormatter = DecimalFormat(
    "#.##",
    DecimalFormatSymbols.getInstance(Locale.US),
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)

private fun parseChapterDate(dateString: String): Long {
    if (dateString.isBlank()) return 0L
    return dateFormat.tryParse(dateString) ?: 0L
}

fun SManga.getSlug(baseUrl: String): String = "$baseUrl$url".toHttpUrl().pathSegments[1]

fun SChapter.getSlugAndIndex(baseUrl: String): Pair<String, String> = if (url.startsWith("/chapter/")) {
    val slug = url.substringAfter("/chapter/").substringBefore("-chapter-")
    val chapterIndex = url.substringAfter("-chapter-").substringBefore("-bahasa-")
    slug to chapterIndex
} else {
    val path = "$baseUrl$url".toHttpUrl().pathSegments
    path[1] to path[3]
}
