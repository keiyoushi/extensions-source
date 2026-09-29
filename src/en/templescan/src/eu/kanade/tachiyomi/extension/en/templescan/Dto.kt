package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

// The site's React Server Components payload renames a fixed subset of field names to
// deterministic short keys. The client bundle (module that exports the rename map) derives
// each key from FNV-1a 32-bit of "<salt>:<name>:<i>" — incrementing i until the key is
// unique, processing the fields in the bundle's order: series_slug, Season, Chapter, price,
// title, chapter_name, chapter_slug, images — and encodes the hash as
// chr(97 + hash % 26) + (hash ushr 5).toString(36). If the site rotates its salt
// (currently "d1a803a73523"), recompute this mapping from the bundle.
@Serializable
class BrowseSeries(
    @SerialName("jdp5hu") val title: String,
    @SerialName("m15392f") val slug: String,
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
        @SerialName("m21n2s7") private val seasons: List<Season>? = null,
    ) {
        val chapters: List<Chapter> get() = seasons.orEmpty().flatMap { it.chapters }

        @Serializable
        class Season(
            @SerialName("opez75") private val items: List<Chapter>? = null,
        ) {
            val chapters: List<Chapter> get() = items.orEmpty()
        }

        @Serializable
        class Chapter(
            @SerialName("c27b2ow") val name: String,
            @SerialName("chapter_title") val title: String? = null,
            @SerialName("m1shekt") val slug: String,
            @SerialName("ivo8tc") val price: Int = 0,
            @SerialName("created_at") private val createdAt: String? = null,
        ) {
            val created: Long by lazy { dateFormat.tryParse(createdAt) }
        }
    }
}

@Serializable
class PagesList(
    @SerialName("xdmo0k") val images: List<String>,
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
