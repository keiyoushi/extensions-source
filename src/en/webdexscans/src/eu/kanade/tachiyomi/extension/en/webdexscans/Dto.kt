package eu.kanade.tachiyomi.extension.en.webdexscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class SearchSeriesDto(
    private val id: String,
    private val title: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@SearchSeriesDto.title
        this.url = id
        this.thumbnail_url = coverUrl?.toAbsoluteUrl(baseUrl)
        this.memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

@Serializable
class SeriesInfo(
    val id: String,
    val slug: String,
    private val title: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val genres: List<GenreInfo>? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@SeriesInfo.title
        this.url = id
        this.thumbnail_url = coverUrl?.toAbsoluteUrl(baseUrl)
        this.author = this@SeriesInfo.author
        this.artist = this@SeriesInfo.artist
        this.description = this@SeriesInfo.description?.let { raw ->
            val cleanHtml = blockTagRegex.replace(raw, "\n")
            Jsoup.parseBodyFragment(cleanHtml).wholeText()
                .replace('\u00a0', ' ')
                .replace(trimLinesRegex, "\n")
                .replace(multiNewlineRegex, "\n\n")
                .trim()
        }
        this.status = when (this@SeriesInfo.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        this.genre = genres?.joinToString { it.name }
        this.memo = buildJsonObject {
            put("slug", slug)
        }
    }

    companion object {
        private val blockTagRegex = Regex("""(?i)</(?:p|div|h[1-6])>""")
        private val trimLinesRegex = Regex("""[ \t\r]*\n[ \t\r]*""")
        private val multiNewlineRegex = Regex("""\n{3,}""")
    }
}

@Serializable
class ChapterInfo(
    val id: String,
    private val title: String? = null,
    private val slug: String,
    @SerialName("chapter_number") private val chapterNumber: Float? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("is_premium") private val isPremium: Boolean = false,
    @SerialName("free_at") private val freeAt: String? = null,
    private val series: SeriesSlugDto? = null,
) {
    val seriesSlug: String? get() = series?.slug

    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        val chapterName = title?.takeIf { it.isNotBlank() }
            ?: chapterNumber?.toString()?.removeSuffix(".0")?.let { "Chapter $it" }
            ?: "Chapter"
        val locked = isPremium()
        this.name = if (locked) "🔒 $chapterName" else chapterName
        this.url = id
        this.chapter_number = chapterNumber ?: -1f
        this.date_upload = createdAt?.let { Instant.parseOrNull(it) }?.toEpochMilliseconds() ?: 0L
        this.memo = buildJsonObject {
            put("slug", slug)
            put("seriesSlug", seriesSlug)
            put("isLocked", locked)
        }
    }

    fun isPremium(): Boolean {
        if (!isPremium) return false
        val freeAtInstant = freeAt?.let { Instant.parseOrNull(it) } ?: return true
        return freeAtInstant.toEpochMilliseconds() > System.currentTimeMillis()
    }
}

@Serializable
class SeriesSlugDto(
    val slug: String,
)

@Serializable
class GenreInfo(
    val name: String,
)

@Serializable
class PageInfo(
    @SerialName("image_url") val imageUrl: String,
)

fun SManga.updateSeriesSlug(slug: String) {
    if (slug.isNotEmpty()) {
        this.memo = buildJsonObject {
            put("slug", slug)
        }
    }
}

fun String.toAbsoluteUrl(baseUrl: String) = if (this.startsWith("/")) baseUrl + this else this
