package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class BrowseSeries(
    @SerialName("series_slug") private val slug: String,
    val title: String,
    @SerialName("alternative_names") val alternativeNames: String? = null,
    private val thumbnail: String? = null,
    val status: String? = null,
    @SerialName("update_chapter") private val updatedAt: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("total_views") val views: Long = 0,
) {
    val updated: Long by lazy {
        dateFormat.tryParse(updatedAt)
    }

    val created: Long by lazy {
        dateFormat.tryParse(createdAt)
    }

    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@BrowseSeries.title
        thumbnail_url = thumbnail
    }
}

@Serializable
class SeriesDetails(
    @SerialName("series_slug") val slug: String,
    val title: String,
    val thumbnail: String? = null,
    val author: String? = null,
    val studio: String? = null,
    @SerialName("release_year") val year: String? = null,
    @SerialName("alternative_names") val alternativeNames: String? = null,
    val adult: Boolean = false,
    val badge: String? = null,
    val status: String? = null,
)

@Serializable
class ChapterList(
    @SerialName("Season") val seasons: List<Chapters>,
) {
    @Serializable
    class Chapters(
        @SerialName("Chapter") val chapters: List<Chapter>,
    ) {
        @Serializable
        class Chapter(
            @SerialName("chapter_name") val name: String,
            @SerialName("chapter_title") val title: String? = null,
            @SerialName("chapter_slug") val slug: String,
            val price: Int,
            @SerialName("created_at") private val createdAt: String? = null,
        ) {
            val created: Long by lazy {
                dateFormat.tryParse(createdAt)
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

private fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }
}
