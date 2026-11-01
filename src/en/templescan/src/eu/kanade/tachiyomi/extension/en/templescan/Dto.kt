package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.int
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant

/** The site salts these field names per build; they are told apart by value. */
private fun JsonObject.saltedStrings(plainKeys: Set<String>) = filterKeys { it !in plainKeys }.values
    .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
    .also { check(it.size == 2) { UNEXPECTED_ROW } }

private const val UNEXPECTED_ROW = "The site's data has changed"

private val RATING = Regex("""\s*\+18\s*""")

private val SLUG = Regex("[a-z0-9._-]+")

private fun List<String>.slug() = last(SLUG::matches)

private val CATALOG_KEYS = setOf("thumbnail", "badge", "status", "created_at", "alternative_names", "update_chapter")

private val CHAPTER_KEYS = setOf("index", "chapter_title", "chapter_thumbnail", "created_at")

@Serializable
class TurnstileToken(private val token: String)

@Serializable
class CatalogFields(
    @SerialName("alternative_names") val alternativeNames: String? = null,
    val thumbnail: String? = null,
    val badge: String? = null,
    val status: String? = null,
    @SerialName("update_chapter") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("total_views") val views: Long,
)

/** A series in the catalog of the comics page. */
class BrowseSeries(json: JsonObject) {
    val fields = json.parseAs<CatalogFields>()
    private val strings = json.saltedStrings(CATALOG_KEYS)

    val slug = strings.slug()
    val title = strings.firstOrNull { it != slug } ?: slug
    val updated: Long by lazy { Instant.tryParse(fields.updatedAt) }
    val created: Long by lazy { Instant.tryParse(fields.createdAt) }

    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@BrowseSeries.title
        thumbnail_url = fields.thumbnail
        status = parseStatus(fields.status)
        genre = fields.badge?.withoutRating()
    }
}

fun String.withoutRating() = replace(RATING, " ").trim().ifEmpty { null }

fun parseStatus(status: String?) = when (status?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "hiatus" -> SManga.ON_HIATUS
    "completed" -> SManga.COMPLETED
    "canceled", "dropped" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
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

/** The series page's data, its chapters grouped by season. */
@Serializable
class SeriesPage(private val seriesData: JsonObject) {
    val chapters get() = seriesData.values.firstInstanceOrNull<JsonArray>().orEmpty()
        .sortedByDescending { it.obj["index"]!!.int }
        .flatMap { season -> season.obj.values.firstInstance<JsonArray>().map { SiteChapter(it.obj) } }
}

class SiteChapter(json: JsonObject) {
    private val strings = json.saltedStrings(CHAPTER_KEYS)

    val slug = strings.slug()
    val name = strings.firstOrNull { it != slug } ?: slug
    val title = json.getStringOrNull("chapter_title")

    val premium = json.filterKeys { it !in CHAPTER_KEYS }.values
        .mapNotNull { (it as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull }
        .singleOrNull().let { it ?: error(UNEXPECTED_ROW) } > 0
    val createdAt = Instant.tryParse(json.getStringOrNull("created_at"))
}
