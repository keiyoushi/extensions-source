package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class LibraryResponse(
    val items: List<SeriesSummary> = emptyList(),
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page * perPage < total
}

@Serializable
class SeriesSummary(
    val identifier: String,
    val title: String,
    val cover: String? = null,
    val status: String? = null,
)

@Serializable
class UpdatesResponse(
    val items: List<UpdateSummary> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class UpdateSummary(
    @SerialName("series_identifier") val seriesIdentifier: String,
    @SerialName("series_title") val seriesTitle: String,
    @SerialName("series_cover") val seriesCover: String? = null,
)

@Serializable
class SeriesDetail(
    val identifier: String,
    val title: String,
    val adult: Boolean = false,
    val artists: List<NameId> = emptyList(),
    val authors: List<NameId> = emptyList(),
    val cover: String? = null,
    val genres: List<Genre> = emptyList(),
    val group: GroupInfo? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val type: String? = null,
)

@Serializable
class NameId(
    val name: String = "",
)

@Serializable
class GroupInfo(
    val name: String = "",
)

@Serializable
class Genre(
    val name: String = "",
)

@Serializable
class ChapterListResponse(
    val items: List<ChapterSummary> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    val page: Int = 1,
)

@Serializable
class ChapterSummary(
    val identifier: String,
    val number: JsonElement = JsonPrimitive(""),
    @SerialName("published_at") val publishedAt: String? = null,
) {
    val numberText: String
        get() = number.jsonPrimitive.contentOrNull.orEmpty()
}

@Serializable
class ChapterDetailResponse(
    val pages: List<PageInfo> = emptyList(),
)

@Serializable
class PageInfo(
    val url: String,
    val number: Int = 0,
)

internal fun SeriesSummary.toSManga(): SManga = SManga.create().apply {
    url = identifier
    title = this@toSManga.title
    thumbnail_url = cover
    status = parseStatus(this@toSManga.status)
}

internal fun UpdateSummary.toSManga(): SManga = SManga.create().apply {
    url = seriesIdentifier
    title = seriesTitle
    thumbnail_url = seriesCover
}

internal fun SeriesDetail.toSManga(): SManga = SManga.create().apply {
    url = identifier
    title = this@toSManga.title
    thumbnail_url = cover
    status = parseStatus(this@toSManga.status)
    author = authors.takeIf(List<NameId>::isNotEmpty)?.joinToString { it.name }
    artist = artists.takeIf(List<NameId>::isNotEmpty)?.joinToString { it.name }
    genre = genres.takeIf(List<Genre>::isNotEmpty)?.joinToString { it.name }
    description = buildString {
        synopsis?.takeIf(String::isNotBlank)?.let { append(it) }
        type?.takeIf(String::isNotBlank)?.let {
            if (isNotEmpty()) append("\n\n")
            append("Tipo: $it")
        }
        group?.name?.takeIf(String::isNotBlank)?.let {
            if (isNotEmpty()) append("\n\n")
            append("Scanlator: $it")
        }
        if (adult) {
            if (isNotEmpty()) append("\n\n")
            append("Conteudo adulto")
        }
    }
}

internal fun ChapterSummary.toSChapter(): SChapter = SChapter.create().apply {
    val chapterNumber = number.jsonPrimitive.floatOrNull ?: -1f

    url = identifier
    chapter_number = chapterNumber
    name = "Capitulo $numberText"
    date_upload = parseDate(publishedAt)
}

internal fun PageInfo.toPage(index: Int): Page = Page(index, imageUrl = url)

private fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
    "ongoing" -> SManga.ONGOING
    "finished" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "dropped" -> SManga.CANCELLED
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
