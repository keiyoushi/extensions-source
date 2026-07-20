package eu.kanade.tachiyomi.extension.en.webdexscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.time.OffsetDateTime

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
        this.description = this@SeriesInfo.description?.let {
            val html = Jsoup.parseBodyFragment(it)
            whitespaceRegex.replace(html.wholeText(), "\n\n").trim()
        }
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

    companion object {
        private val whitespaceRegex = Regex("""([ \u00a0\t\r]*\n){3,}""")
    }
}

@Serializable
class ChapterInfo(
    private val title: String? = null,
    private val slug: String,
    @SerialName("chapter_number") private val chapterNumber: Float? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("is_premium") private val isPremium: Boolean = false,
    @SerialName("free_at") private val freeAt: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        val chapterName = title?.takeIf { it.isNotBlank() }
            ?: chapterNumber?.toString()?.removeSuffix(".0")?.let { "Chapter $it" }
            ?: "Chapter"
        this.name = if (isPremium()) "🔒 $chapterName" else chapterName
        this.url = "/series/$seriesSlug/$slug"
        this.date_upload = createdAt?.let(OffsetDateTime::parse)?.toInstant()?.toEpochMilli() ?: 0L
    }

    fun isPremium(): Boolean {
        val now = OffsetDateTime.now()
        val freeAt = freeAt?.let(OffsetDateTime::parse)
        return isPremium && freeAt?.isAfter(now) ?: true
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
