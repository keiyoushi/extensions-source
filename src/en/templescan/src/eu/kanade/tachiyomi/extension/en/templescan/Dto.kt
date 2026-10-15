package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.time.Instant

/** A series in the catalog the site's app holds on its comics page. */
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
    val updated: Long by lazy { Instant.tryParse(updatedAt) }

    val created: Long by lazy { Instant.tryParse(createdAt) }

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
class SiteChapter(
    val name: String,
    val title: String? = null,
    val slug: String,
    val premium: Boolean = false,
    val createdAt: Long = 0,
)

/** A page as the WebView loaded it, with the data the site's app holds for it. */
@Serializable
class SitePage(
    val status: Int,
    val cloudflareChallenge: Boolean,
    val html: String,
    val data: JsonElement? = null,
) {
    fun document(url: String): Document = Jsoup.parse(html, url)
}
