package eu.kanade.tachiyomi.multisrc.ezmanhwa

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal val EZMANHWA_DATE_FORMAT by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

@Serializable
data class EZManhwaSeriesListDto(
    val data: List<EZManhwaSeriesDto>,
    val totalPages: Int,
    @SerialName("current") val currentPage: Int,
)

// Used for both list (partial) and details (full) responses.
// Fields absent in the list response deserialize as null and are
// only populated after mangaDetailsParse sets initialized = true.
@Serializable
data class EZManhwaSeriesDto(
    val slug: String,
    val title: String,
    val cover: String? = null,
    val type: String? = null,
    val status: String? = null,
    val alternativeTitles: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<EZManhwaGenreDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@EZManhwaSeriesDto.title
        thumbnail_url = cover
        author = this@EZManhwaSeriesDto.author?.trim()?.takeIf { it.isNotBlank() }
        artist = this@EZManhwaSeriesDto.artist?.trim()?.takeIf { it.isNotBlank() }
        description = buildString {
            this@EZManhwaSeriesDto.description?.let { append(Jsoup.parse(it).text()) }
            if (!alternativeTitles.isNullOrBlank()) {
                append("\n\nAlternative Titles: $alternativeTitles")
            }
        }.trim().takeIf { it.isNotBlank() }
        genre = genres?.joinToString { it.name }
        status = when (this@EZManhwaSeriesDto.status) {
            "ONGOING", "MASS_RELEASED" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "DROPPED" -> SManga.CANCELLED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
data class EZManhwaGenreDto(val name: String)

@Serializable
data class EZManhwaChapterListDto(
    val data: List<EZManhwaChapterDto>,
    val totalPages: Int = 1,
    @SerialName("current") val currentPage: Int = 1,
)

@Serializable
data class EZManhwaChapterDto(
    val slug: String,
    val number: Double? = null,
    val title: String? = null,
    val requiresPurchase: Boolean? = null,
    val createdAt: String? = null,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        // Unified URL format: series/{seriesSlug}/chapters/{chapterSlug}
        // pageListRequest uses: $apiUrl/${chapter.url}
        // getChapterUrl uses:   $baseUrl/${chapter.url.replace("/chapters/", "/")}
        url = "series/$seriesSlug/chapters/$slug"
        val prefix = if (requiresPurchase == true) "\uD83D\uDD12 " else ""
        val numStr = number?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        } ?: ""
        val chapterTitle = title?.takeIf { it.isNotBlank() } ?: "Chapter $numStr".trim()
        name = prefix + chapterTitle
        chapter_number = number?.toFloat() ?: -1f
        date_upload = EZMANHWA_DATE_FORMAT.tryParse(createdAt)
    }
}

@Serializable
data class EZManhwaPageListDto(
    val images: List<EZManhwaImageDto>? = null,
    val requiresPurchase: Boolean? = null,
    val totalImages: Int? = null,
)

@Serializable
data class EZManhwaImageDto(val url: String)
