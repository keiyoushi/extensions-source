package eu.kanade.tachiyomi.extension.en.webdexscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class SearchSeriesDto(
    private val title: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@SearchSeriesDto.title
        this.url = "/series/$slug"
        this.thumbnail_url = coverUrl?.toAbsoluteUrl(baseUrl)
    }
}

@Serializable
class SeriesPayload(
    val initialSeries: SeriesInfo,
    val initialChapters: List<ChapterInfo>? = null,
    val initialGenres: List<GenreInfo>? = null,
)

@Serializable
class SeriesInfo(
    val slug: String,
    private val title: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
) {
    fun toSManga(baseUrl: String, genres: List<GenreInfo>?) = SManga.create().apply {
        this.title = this@SeriesInfo.title
        this.url = "/series/$slug"
        this.thumbnail_url = coverUrl?.toAbsoluteUrl(baseUrl)
        this.author = this@SeriesInfo.author
        this.artist = this@SeriesInfo.artist
        this.description = this@SeriesInfo.description
        this.status = when (this@SeriesInfo.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        this.genre = genres?.joinToString { it.name }
        this.initialized = true
    }
}

@Serializable
class ChapterInfo(
    private val title: String? = null,
    private val slug: String,
    @SerialName("chapter_number") private val chapterNumber: Float? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
) {
    fun toSChapter(seriesSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        val chapterName = title?.takeIf { it.isNotBlank() }
            ?: chapterNumber?.toString()?.removeSuffix(".0")?.let { "Chapter $it" }
            ?: "Chapter"
        this.name = if (isPremium) "🔒 $chapterName" else chapterName
        this.url = "/series/$seriesSlug/$slug"
        // Grab the first 19 characters to format "yyyy-MM-dd'T'HH:mm:ss" properly and bypass timezone + subsecond issues
        this.date_upload = dateFormat.tryParse(createdAt?.take(19))
    }
}

@Serializable
class GenreInfo(
    val name: String,
)

@Serializable
class PagesPayload(
    val initialPages: List<PageInfo>,
)

@Serializable
class PageInfo(
    @SerialName("image_url") val imageUrl: String,
)

fun String.toAbsoluteUrl(baseUrl: String) = if (this.startsWith("/")) baseUrl + this else this
