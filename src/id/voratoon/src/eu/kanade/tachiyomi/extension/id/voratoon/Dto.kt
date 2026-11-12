package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.time.Instant

@Serializable
class BrowseDto(
    val series: List<SeriesItem>,
    val seriesMeta: Meta? = null,
)

@Serializable
class FilterPayloadDto(
    val genres: List<GenreItem>,
)

@Serializable
class SeriesDetailsDto(
    val series: List<SeriesItem>,
    val chapters: List<ChapterItem>,
)

@Serializable
class ChapterPageDto(
    val chapter: ChapterItem,
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
class GenreItem(
    private val data: GenreInfo,
) {
    fun toPair(): Pair<String, String> = data.name to data.name.lowercase()
}

@Serializable
class Meta(
    val page: Int? = null,
    val lastPage: Int? = null,
)

@Serializable
class ChapterItem(
    private val data: ChapterData,
    private val createdAt: String? = null,
    private val updatedAt: String? = null,
    private val chapterIndex: Float? = null,
) {
    fun toSChapter(seriesSlug: String?): SChapter = SChapter.create().apply {
        val index = (data.index ?: chapterIndex) ?: 0f
        val formattedIndex = index.toString().removeSuffix(".0")
        url = "/series/$seriesSlug/chapter/$formattedIndex"
        name = if (data.title.isNullOrBlank()) {
            "Chapter $formattedIndex"
        } else {
            "Chapter $formattedIndex: ${data.title}"
        }
        date_upload = parseChapterDate(createdAt ?: updatedAt ?: "")
        chapter_number = index
    }

    fun toPageList(chapterUrl: String = ""): List<Page> = data.images?.mapIndexed { index, imageUrl ->
        Page(index, url = chapterUrl, imageUrl = imageUrl)
    } ?: emptyList()
}

@Serializable
class ChapterData(
    val index: Float? = null,
    val title: String? = null,
    val images: List<String>? = null,
)

private fun parseChapterDate(dateString: String): Long = Instant.tryParse(dateString)

fun SManga.getSlug(baseUrl: String): String = (url.toHttpUrlOrNull() ?: "$baseUrl$url".toHttpUrl()).pathSegments[1]

fun SChapter.getSlugAndIndex(baseUrl: String): Pair<String, String> = if (url.startsWith("/chapter/")) {
    val slug = url.substringAfter("/chapter/").substringBefore("-chapter-")
    val chapterIndex = url.substringAfter("-chapter-").substringBefore("-bahasa-")
    slug to chapterIndex
} else {
    val httpUrl = url.toHttpUrlOrNull() ?: "$baseUrl$url".toHttpUrl()
    httpUrl.pathSegments[1] to httpUrl.pathSegments[3]
}
