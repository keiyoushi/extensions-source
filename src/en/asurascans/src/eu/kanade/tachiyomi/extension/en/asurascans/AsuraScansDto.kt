package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class DataDto<T>(
    val data: T? = null,
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class MangaDetailsDto(
    val series: MangaDto,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val cover: String,
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val genres: List<GenreDto>? = null,
    private val status: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover
        url = "/series/$slug" // Keep the old URL structure for compatibility with existing bookmarks
    }

    fun toSMangaDetails() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        description = this@MangaDto.description?.let { Jsoup.parseBodyFragment(it) }?.text()
        genre = genres?.joinToString { it.name }
        status = parseStatus()
    }

    fun parseStatus() = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class GenreDto(
    val name: String,
)

val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterWrapperDto(
    val chapter: ChapterDto,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String = "",
    @SerialName("is_locked") val isLocked: Boolean = false,
    val pages: List<PageDto>? = emptyList(),
    @SerialName("series_slug") private val seriesSlug: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        val numberStr = number.toString().removeSuffix(".0")
        url = "/series/$seriesSlug/chapter/$numberStr"
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter $numberStr")
            title?.let { append(" - $it") }
        }
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class PageDto(
    val url: String,
    val tiles: List<Int>? = null,
    @SerialName("tile_cols")
    val tileCols: Int? = null,
    @SerialName("tile_rows")
    val tileRows: Int? = null,
)

@Serializable
class PageData(
    val tiles: List<Int>,
    val tileCols: Int,
    val tileRows: Int,
)
