package eu.kanade.tachiyomi.extension.en.mythtoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaDto(
    val id: String,
    private val title: String,
    private val contentKind: String,
    private val cover: String? = null,
    private val status: String? = null,
    val genres: List<String> = emptyList(),
) {
    val isNovel get() = contentKind == "novel"

    // The site writes some genres with different cases ("Age Gap", "Age gap")
    fun matches(query: String, status: String?, genre: String?) = title.contains(query, ignoreCase = true) &&
        (status == null || this.status?.lowercase() == status) &&
        (genre == null || genres.any { it.equals(genre, ignoreCase = true) })

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/manga/$id"
        title = this@MangaDto.title.trim()
        thumbnail_url = cover?.let { baseUrl.resolve(it)?.toString() }
    }
}

@Serializable
class ViewsDto(
    val id: String,
    val viewCount: Long,
)

@Serializable
class DetailsPageDto(
    val dataDetails: DetailsDto,
)

@Serializable
class DetailsDto(
    private val id: String,
    private val title: String,
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val cover: String? = null,
    private val alternativeTitle: String? = null,
    private val genres: List<String> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/manga/$id"
        title = this@DetailsDto.title.trim()
        thumbnail_url = cover?.let { baseUrl.resolve(it)?.toString() }
        author = this@DetailsDto.author?.takeUnless { it.isBlank() || it == "N/A" }
        artist = this@DetailsDto.artist?.takeUnless { it.isBlank() || it == "N/A" }
        genre = genres.joinToString().ifEmpty { null }
        status = when (this@DetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@DetailsDto.description)
            .altTitles(listOfNotNull(alternativeTitle))
            .build()
    }

    fun toSChapters() = chapters.reversed().map { it.toSChapter() }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float,
    private val title: String,
    private val addedAt: String? = null,
    private val isLocked: Boolean = false,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/read/$id"
        // Sold for the site's coins
        name = if (isLocked) "🔒 $title" else title
        chapter_number = number
        date_upload = Instant.tryParse(addedAt).takeIf { it != 0L } ?: OLD_DATE_FORMAT.tryParseDateTime(addedAt, ZoneOffset.UTC)
    }
}

@Serializable
class ReaderPageDto(
    val dataRead: ReaderDto,
)

@Serializable
class ReaderDto(
    val mangaId: String,
    val pages: List<String> = emptyList(),
    val isLocked: Boolean = false,
)

// The oldest chapters have their date as "28-08-2025, 09:09 am"
private val OLD_DATE_FORMAT = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("dd-MM-yyyy, hh:mm a")
    .toFormatter(Locale.ENGLISH)
