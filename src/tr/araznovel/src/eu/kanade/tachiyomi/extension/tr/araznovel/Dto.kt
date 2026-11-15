package eu.kanade.tachiyomi.extension.tr.araznovel

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class SerieListDto(
    val items: List<SerieDto>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class SerieDto(
    private val id: Int,
    private val title: String,
    private val url: String,
    private val thumbnail: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = Parser.unescapeEntities(this@SerieDto.title, false)
        thumbnail_url = thumbnail?.replace(THUMB_SIZE_REGEX, "")
        memo = buildJsonObject { put("slug", this@SerieDto.url.trimEnd('/').substringAfterLast('/')) }
    }
}

@Serializable
class SerieDetailsDto(
    private val id: Int,
    private val title: String,
    val slug: String,
    private val description: String? = null,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val alternative: String? = null,
    private val genres: List<TermDto> = emptyList(),
    private val authors: List<TermDto> = emptyList(),
    private val artists: List<TermDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = Parser.unescapeEntities(this@SerieDetailsDto.title, false)
        thumbnail_url = cover
        author = authors.joinToString { it.name }.ifEmpty { null }
        artist = artists.joinToString { it.name }.ifEmpty { null }
        genre = (listOfNotNull(type?.replaceFirstChar(Char::uppercase)) + genres.map { it.name }).joinToString()
        description = buildString {
            this@SerieDetailsDto.description?.let { append(Parser.unescapeEntities(it, false).trim()) }
            alternative?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif isimler: ", it)
            }
        }
        status = when (this@SerieDetailsDto.status) {
            "on-going" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            "canceled", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject { put("slug", slug) }
    }
}

@Serializable
class TermDto(val name: String)

@Serializable
class ChapterDto(
    private val id: Int,
    private val name: String,
    private val slug: String,
    private val date: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = id.toString()
        name = Parser.unescapeEntities(this@ChapterDto.name, false)
        date_upload = DATE_FORMAT.tryParseDateTime(date, ZoneOffset.UTC)
        memo = buildJsonObject {
            put("series", seriesSlug)
            put("slug", slug)
        }
    }
}

@Serializable
class WpMangaDto(val id: Int)

private val THUMB_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
