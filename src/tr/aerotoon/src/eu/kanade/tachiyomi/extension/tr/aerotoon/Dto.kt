package eu.kanade.tachiyomi.extension.tr.aerotoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SeriesListDto(
    val series: List<SeriesDto>,
    val hasMore: Boolean,
)

@Serializable
class LatestUpdatesDto(
    val updates: List<SeriesDto>,
    val hasMore: Boolean,
)

@Serializable
class SeriesDto(
    private val id: Int,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val rating: Double? = null,
    private val type: String? = null,
    @SerialName("alt_names") private val altNames: String? = null,
) {
    val isNovel get() = type == "novel"

    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
        author = this@SeriesDto.author?.trim()?.ifEmpty { null }
        artist = this@SeriesDto.artist?.trim()?.ifEmpty { null }
        genre = (listOfNotNull(TYPE_LABELS[type]) + genres).distinct().joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "ongoing", "current" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        // Alternative names are one free text, and some of them contain commas
        description = MarkdownDescription.Builder()
            .rating(rating, max = 5)
            .description(this@SeriesDto.description)
            .altTitles(listOfNotNull(altNames))
            .build()
    }
}

@Serializable
class SeriesDetailsDto(
    val series: SeriesDto,
    private val chapters: List<ChapterDto>,
) {
    // Most older chapters only link to another group's site, and have no pages here
    fun toSChapterList(seriesId: String) = chapters.filter { it.externalUrl == null }
        .sortedByDescending { it.chapterNumber }
        .map { it.toSChapter(seriesId) }
}

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_number") val chapterNumber: Float,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("external_url") val externalUrl: String? = null,
) {
    fun toSChapter(seriesId: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        val subtitle = title?.takeUnless { it.isBlank() || it == number || it.equals("Bölüm $number", ignoreCase = true) }
        url = id.toString()
        name = if (subtitle == null) "Bölüm $number" else "Bölüm $number - $subtitle"
        chapter_number = chapterNumber
        date_upload = DATE_FORMAT.tryParseDateTime(createdAt, ZoneOffset.UTC)
        memo = buildJsonObject {
            put("series", seriesId)
            put("number", number)
        }
    }
}

@Serializable
class PageListDto(private val pages: List<PageDto>) {
    fun toPageList() = pages.mapIndexed { index, page -> Page(index, imageUrl = page.displayImage) }
}

@Serializable
class PageDto(@SerialName("display_image") val displayImage: String)

private val TYPE_LABELS = mapOf(
    "manga" to "Manga",
    "manhwa" to "Manhwa",
    "manhua" to "Manhua",
    "comic" to "Çizgi Roman",
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
