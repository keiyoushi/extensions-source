package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal const val API_HOST = "https://api.taimumangas.com"
internal const val API_BASE_URL = "$API_HOST/api/v2"

private const val MEDIA_BASE_URL = "$API_HOST/media"

@Serializable
class SeriesListResponse(
    @JsonNames("data")
    val series: List<SeriesSummary> = emptyList(),
    val pagination: Pagination = Pagination(),
    @SerialName("total_series") val totalSeries: Int = 0,
)

@Serializable
class SeriesSummary(
    val id: String = "",
    val code: String,
    val title: String,
    val cover: String? = null,
    val status: String? = null,
    val year: Int? = null,
    @SerialName("total_likes") val totalLikes: Int = 0,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
    val rating: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
class Pagination(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("has_previous") val hasPrevious: Boolean = false,
)

@Serializable
class SeriesDetailResponse(
    val message: String = "",
    val series: SeriesDetail,
)

@Serializable
class SeriesDetail(
    val id: String = "",
    val title: String,
    val country: String? = null,
    val code: String,
    val cover: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    @SerialName("alternative_names") val alternativeNames: String? = null,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("total_read_later") val totalReadLater: Int = 0,
    @SerialName("average_rating") val averageRating: Double? = null,
    @SerialName("total_ratings") val totalRatings: Int = 0,
    val recommendation: Int = 0,
    val author: NameCode? = null,
    val artist: NameCode? = null,
    val authors: List<NameCode> = emptyList(),
    val artists: List<NameCode> = emptyList(),
    val group: GroupInfo? = null,
    val genres: List<Genre> = emptyList(),
)

@Serializable
class NameCode(
    val name: String = "",
    val code: String = "",
)

@Serializable
class GroupInfo(
    val name: String = "",
    val slug: String = "",
    val code: String = "",
    val avatar: String? = null,
)

@Serializable
class Genre(
    val id: String = "",
    val name: String = "",
)

@Serializable
class ChapterListResponse(
    val data: ChapterListData = ChapterListData(),
    val message: String = "",
)

@Serializable
class ChapterListData(
    val chapters: List<ChapterSummary> = emptyList(),
    @SerialName("total_chapters") val totalChapters: Int = 0,
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("has_previous") val hasPrevious: Boolean = false,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("first_chapter") val firstChapter: ChapterPointer? = null,
    @SerialName("last_chapter") val lastChapter: ChapterPointer? = null,
)

@Serializable
class ChapterSummary(
    val number: JsonElement = JsonPrimitive(""),
    val code: String = "",
    val title: String? = null,
    val thumbnail: String? = null,
    val season: Int = 1,
    @SerialName("total_likes") val totalLikes: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val read: Boolean = false,
) {
    val numberText: String
        get() = number.jsonPrimitive.contentOrNull.orEmpty()
}

@Serializable
class ChapterPointer(
    val number: String = "",
    val code: String = "",
)

@Serializable
class ChapterDetailResponse(
    val chapter: ChapterDetail,
    val message: String = "",
)

@Serializable
class ChapterDetail(
    @SerialName("series_code") val seriesCode: String = "",
    @SerialName("series_title") val seriesTitle: String = "",
    @SerialName("chapter_number") val chapterNumber: String = "",
    @SerialName("chapter_title") val chapterTitle: String? = null,
    @SerialName("chapter_code") val chapterCode: String = "",
    val pages: List<PageInfo> = emptyList(),
)

@Serializable
class PageInfo(
    val path: String = "",
    val number: Int = 0,
)

internal fun SeriesSummary.toSManga(): SManga = SManga.create().apply {
    url = code
    title = this@toSManga.title
    thumbnail_url = mediaUrl(cover)
    status = parseStatus(this@toSManga.status)
}

internal fun SeriesDetail.toSManga(): SManga = SManga.create().apply {
    url = code
    title = this@toSManga.title
    thumbnail_url = mediaUrl(cover)
    status = parseStatus(this@toSManga.status)
    author = authors.takeIf(List<NameCode>::isNotEmpty)
        ?.joinToString { it.name }
        ?: this@toSManga.author?.name
    artist = artists.takeIf(List<NameCode>::isNotEmpty)
        ?.joinToString { it.name }
        ?: this@toSManga.artist?.name
    genre = genres.joinToString { it.name }
    description = buildString {
        synopsis?.takeIf(String::isNotBlank)?.let { append(it) }
        alternativeNames?.takeIf(String::isNotBlank)?.let {
            if (isNotEmpty()) append("\n\n")
            append("Nomes alternativos: $it")
        }
        group?.name?.takeIf(String::isNotBlank)?.let {
            if (isNotEmpty()) append("\n\n")
            append("Scanlator: $it")
        }
    }
}

internal fun ChapterSummary.toSChapter(): SChapter = SChapter.create().apply {
    val number = numberText

    url = code
    chapter_number = number.toFloatOrNull() ?: -1f
    name = buildString {
        if (season > 1) {
            append("S")
            append(season)
            append(" - ")
        }
        append("Capitulo ")
        append(number)
        title?.takeIf(String::isNotBlank)?.let {
            append(" - ")
            append(it)
        }
    }
    date_upload = parseDate(createdAt)
}

internal fun PageInfo.toPage(index: Int): Page = Page(index, imageUrl = mediaUrl(path))

private fun mediaUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null

    val cleanPath = path.trim().trimStart('/')
    return when {
        cleanPath.startsWith("http") -> cleanPath
        cleanPath.startsWith("media/") -> "$API_HOST/$cleanPath"
        else -> "$MEDIA_BASE_URL/$cleanPath"
    }
}

private fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
    "ongoing", "em andamento" -> SManga.ONGOING
    "completed", "complete", "finalizado" -> SManga.COMPLETED
    "hiatus", "em hiato" -> SManga.ON_HIATUS
    "cancelled", "canceled", "cancelado", "dropped", "abandonada" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private fun parseDate(date: String?): Long {
    if (date.isNullOrBlank()) return 0L

    val normalized = date.trim()
        .replace(MICROSECONDS_REGEX, ".$1")
        .replace(TIMEZONE_COLON_REGEX) { "${it.groupValues[1]}${it.groupValues[2]}" }

    for (format in DATE_FORMATS) {
        runCatching {
            synchronized(format) {
                format.parse(normalized)?.time
            }
        }.getOrNull()?.let { return it }
    }

    return 0L
}

private val MICROSECONDS_REGEX = Regex("""\.(\d{3})\d+""")
private val TIMEZONE_COLON_REGEX = Regex("""([+-]\d{2}):(\d{2})$""")

private val UTC = TimeZone.getTimeZone("UTC")
private val DATE_FORMATS = listOf(
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = UTC },
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = UTC },
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT),
)
