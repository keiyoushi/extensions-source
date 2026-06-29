package eu.kanade.tachiyomi.extension.en.nixmanga

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class PaginatedComicsDto(
    @JsonNames("results") val comics: List<ComicDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
    private val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page < totalPages || (page * 24) < total
}

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    private val synopsis: String? = null,
    private val cover: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@ComicDto.title
        thumbnail_url = cover
        status = parseStatus(this@ComicDto.status)
        description = synopsis
        genre = genres.joinToString { it.name }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class PaginatedChaptersDto(
    val chapters: List<ChapterDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
) {
    val hasNextPage: Boolean
        get() = page < totalPages
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float? = null,
    private val title: String? = null,
    private val slug: String,
    @SerialName("published_at") private val publishedAt: String? = null,
    private val scanlator: ScanlatorDto? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$slug#$id"

        val chapterName = buildString {
            if (number != null) {
                append("Chapter ")
                append(number.toString().removeSuffix(".0"))
            } else {
                append("Chapter")
            }
            if (!title.isNullOrEmpty()) {
                append(" - ")
                append(title)
            }
        }
        name = chapterName.trim()
        date_upload = parseDate(publishedAt)
        scanlator = this@ChapterDto.scanlator?.name
    }
}

@Serializable
class ScanlatorDto(val name: String)

@Serializable
class PagesDto(
    private val pages: List<PageDto> = emptyList(),
) {
    fun toPageList(): List<Page> = pages.mapIndexed { index, page -> page.toPage(index) }
}

@Serializable
class PageDto(
    @SerialName("image_url") private val imageUrl: String,
) {
    fun toPage(index: Int) = Page(index, imageUrl = imageUrl)
}

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private fun parseDate(dateStr: String?): Long {
    if (dateStr == null) return 0L
    val cleanDate = if (dateStr.contains(".")) {
        val split = dateStr.split(".")
        val decimals = split[1].removeSuffix("Z").take(3).padEnd(3, '0')
        split[0] + "." + decimals + "Z"
    } else {
        dateStr.removeSuffix("Z") + ".000Z"
    }
    return dateFormat.tryParse(cleanDate)
}
