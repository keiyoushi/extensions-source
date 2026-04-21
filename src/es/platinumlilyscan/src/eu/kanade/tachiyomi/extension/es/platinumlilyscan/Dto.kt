package eu.kanade.tachiyomi.extension.es.platinumlilyscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

@Serializable
class SeriesDto(
    @SerialName("title") private val title: String,
    @SerialName("slug") val slug: String,
    @SerialName("description") private val description: String? = null,
    @SerialName("coverUrl") private val coverUrl: String? = null,
    @SerialName("author") private val author: String? = null,
    @SerialName("artist") private val artist: String? = null,
    @SerialName("genres") private val genres: List<SeriesGenreDto>? = emptyList(),
    @SerialName("chapters") val chapters: List<ChapterDto>? = emptyList(),
    @SerialName("_count") private val count: CountDto? = null,
    @SerialName("updatedAt") private val updatedAt: String? = null,
    @SerialName("type") private val type: String? = null,
    @SerialName("status") private val status: String? = null,
    @SerialName("contentRating") private val contentRating: String? = null,
    @SerialName("altTitles") private val altTitles: String? = null,
) {
    val bookmarkCount: Int get() = count?.bookmarks ?: 0

    val updatedAtMillis: Long get() = dateFormat.tryParse(updatedAt)

    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@SeriesDto.title
        this.url = "/api/series/$slug"
        this.thumbnail_url = this@SeriesDto.coverUrl?.let { baseUrl + it }
        this.description = this@SeriesDto.description
        this.author = this@SeriesDto.author
        this.artist = this@SeriesDto.artist
        this.genre = this@SeriesDto.genres?.mapNotNull { it.genre?.name }?.joinToString()
        this.status = when (this@SeriesDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        this.initialized = true
    }

    fun hasGenre(genreName: String): Boolean = genres?.any { it.genre?.name.equals(genreName, ignoreCase = true) } == true

    fun matchQuery(query: String): Boolean {
        if (title.contains(query, ignoreCase = true)) return true
        if (altTitles?.contains(query, ignoreCase = true) == true) return true
        return false
    }

    fun matches(type: String?, status: String?, rating: String?, genre: String?): Boolean {
        val matchType = type == null || this.type == type
        val matchStatus = status == null || this.status == status
        val matchRating = rating == null || this.contentRating == rating
        val matchGenre = genre == null || hasGenre(genre)
        return matchType && matchStatus && matchRating && matchGenre
    }
}

@Serializable
class SeriesGenreDto(
    @SerialName("genre") val genre: GenreDto? = null,
)

@Serializable
class GenreDto(
    @SerialName("name") val name: String,
)

@Serializable
class CountDto(
    @SerialName("bookmarks") val bookmarks: Int = 0,
)

@Serializable
class ChapterDto(
    @SerialName("id") val id: String = "",
    @SerialName("number") private val number: Float = -1f,
    @SerialName("title") private val title: String? = null,
    @SerialName("publishedAt") private val publishedAt: String? = null,
    @SerialName("pages") val pages: List<PageDto>? = emptyList(),
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        this.url = "/api/series/$seriesSlug#$id"
        this.chapter_number = this@ChapterDto.number
        this.date_upload = dateFormat.tryParse(this@ChapterDto.publishedAt)
        this.name = buildString {
            append("Capítulo ")
            append(this@ChapterDto.number.toString().removeSuffix(".0"))
            if (!this@ChapterDto.title.isNullOrBlank()) {
                append(" - ")
                append(this@ChapterDto.title)
            }
        }
    }
}

@Serializable
class PageDto(
    @SerialName("imageUrl") val imageUrl: String,
)
