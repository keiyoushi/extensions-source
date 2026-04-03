package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val DATE_FORMAT by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

@Serializable
data class SeriesListDto(
    val data: List<SeriesApiDto>,
    val totalPages: Int,
    val current: Int,
)

@Serializable
data class SeriesApiDto(
    val slug: String,
    val title: String,
    val alternativeTitles: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val cover: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<GenreDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SeriesApiDto.title
        thumbnail_url = cover
        author = this@SeriesApiDto.author?.trim()?.takeIf { it.isNotBlank() }
        artist = this@SeriesApiDto.artist?.trim()?.takeIf { it.isNotBlank() }

        description = buildString {
            this@SeriesApiDto.description?.let {
                append(Jsoup.parse(it).text())
            }
            if (!alternativeTitles.isNullOrBlank()) {
                append("\n\nAlternative Titles: $alternativeTitles")
            }
        }.trim().takeIf { it.isNotBlank() }

        genre = genres?.joinToString { it.name }

        status = when (this@SeriesApiDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "DROPPED" -> SManga.CANCELLED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
data class GenreDto(
    val name: String,
)

@Serializable
data class ChapterListApiDto(
    val data: List<ChapterApiDto>,
    val totalPages: Int = 1,
    val current: Int = 1,
)

@Serializable
data class ChapterApiDto(
    val slug: String,
    val number: Double? = null,
    val title: String? = null,
    val requiresPurchase: Boolean? = null,
    val createdAt: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = "/series/$seriesSlug/$slug"
        val prefix = if (requiresPurchase == true) "\uD83D\uDD12 " else ""

        val numStr = number?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        } ?: ""

        val chapterTitle = title?.takeIf { it.isNotBlank() } ?: "Chapter $numStr".trim()
        name = prefix + chapterTitle
        chapter_number = number?.toFloat() ?: -1f

        date_upload = try {
            createdAt?.let { DATE_FORMAT.parse(it)?.time } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

@Serializable
data class PageListApiDto(
    val images: List<ImageApiDto>? = null,
    val requiresPurchase: Boolean? = null,
    val totalImages: Int? = null,
)

@Serializable
data class ImageApiDto(
    val url: String,
)
