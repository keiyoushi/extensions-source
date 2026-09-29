package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

// Fields the site renames to short keys are declared under their logical names; the payload is
// remapped before decoding, so these stay valid across salt rotations. See [RscKeys].
@Serializable
class BrowseSeries(
    val title: String,
    @SerialName("series_slug") val slug: String,
    @SerialName("alternative_names") val alternativeNames: String? = null,
    val thumbnail: String? = null,
    val badge: String? = null,
    val status: String? = null,
    @SerialName("update_chapter") private val updatedAt: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("total_views") val views: Long = 0,
) {
    val updated: Long by lazy { dateFormat.tryParse(updatedAt) }

    val created: Long by lazy { dateFormat.tryParse(createdAt) }

    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@BrowseSeries.title
        thumbnail_url = thumbnail
    }
}

/** schema.org `ComicSeries` JSON-LD block embedded in the detail page. */
@Serializable
class ComicSeriesLd(
    @SerialName("@type") private val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    val image: String? = null,
    val author: Author? = null,
    val alternateName: String? = null,
    val genre: List<String>? = null,
) {
    @Serializable
    class Author(val name: String? = null)

    val isSeries: Boolean get() = type == "ComicSeries"
}

@Serializable
class SeriesData(
    @SerialName("Season") private val seasons: List<Season>? = null,
) {
    val chapters: List<Chapter> get() = seasons.orEmpty().flatMap { it.chapters }

    @Serializable
    class Season(
        @SerialName("Chapter") private val items: List<Chapter>? = null,
    ) {
        val chapters: List<Chapter> get() = items.orEmpty()
    }

    @Serializable
    class Chapter(
        @SerialName("chapter_name") val name: String,
        @SerialName("chapter_title") val title: String? = null,
        @SerialName("chapter_slug") val slug: String,
        val price: Int = 0,
        @SerialName("created_at") private val createdAt: String? = null,
    ) {
        val created: Long by lazy { dateFormat.tryParse(createdAt) }
    }
}

@Serializable
class PagesList(
    val images: List<String>,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

private fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }
}
