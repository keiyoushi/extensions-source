package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

// The site's React Server Components payload renames a fixed subset of field names to
// deterministic short keys. They are derived in the site's client bundle as
// FNV-1a("6b3ad0e8a808:<name>:<collision index>") and stay valid as long as that salt is
// unchanged. If the site ever rotates the salt, recompute this mapping:
//   title -> pnsk6q, series_slug -> s20a8oj, Chapter -> qmy3ca, chapter_name -> u171tuh,
//   chapter_slug -> y26ma5t, price -> u1e8nmi, Season -> u2ytwc, images -> nu7315
@Serializable
class BrowseSeries(
    @SerialName("pnsk6q") val title: String,
    @SerialName("s20a8oj") val slug: String,
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
class SeriesDataWrapper(
    val seriesData: SeriesData,
) {
    @Serializable
    class SeriesData(
        @SerialName("u2ytwc") private val seasons: List<Season>? = null,
    ) {
        val chapters: List<Chapter> get() = seasons.orEmpty().flatMap { it.chapters }

        @Serializable
        class Season(
            @SerialName("qmy3ca") private val items: List<Chapter>? = null,
        ) {
            val chapters: List<Chapter> get() = items.orEmpty()
        }

        @Serializable
        class Chapter(
            @SerialName("u171tuh") val name: String,
            @SerialName("chapter_title") val title: String? = null,
            @SerialName("y26ma5t") val slug: String,
            @SerialName("u1e8nmi") val price: Int = 0,
            @SerialName("created_at") private val createdAt: String? = null,
        ) {
            val created: Long by lazy { dateFormat.tryParse(createdAt) }
        }
    }
}

@Serializable
class PagesList(
    @SerialName("nu7315") val images: List<String>,
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
