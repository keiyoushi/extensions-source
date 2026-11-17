package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val RATING = Regex("""\s*\+18\s*""")

/** What `catalog.js` gets: the statuses to list in turn, the site's order options, and a query for the site search. */
@Serializable
class CatalogArgs(
    private val statuses: List<String>,
    private val order: String?,
    private val direction: String?,
    private val query: String?,
)

/** What `run.js` reports instead of a script's result. */
@Serializable
class PageStatus(
    val check: Boolean = false,
    val status: Int? = null,
    val error: String? = null,
)

/** What `catalog.js` read: the comics page's cards per status, and the site search's results for a query. */
@Serializable
class CatalogPage(val cards: List<String>, val results: String? = null)

@Serializable
class HtmlPage(val html: String)

@Serializable
class ReaderPage(val pages: List<String>)

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
    val image: String? = null,
    val author: Author? = null,
    val alternateName: String? = null,
    val genre: List<String>? = null,
) {
    @Serializable
    class Author(val name: String? = null)

    val isSeries: Boolean get() = type == "ComicSeries"
}
