package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

// A series card of the site's list
@Serializable
internal class Card(
    private val id: String,
    private val title: String,
    private val cover: String?,
    private val badges: List<String> = emptyList(),
    private val genres: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = this@Card.title
        thumbnail_url = cover?.let { baseUrl.toHttpUrl().resolve(it)?.newBuilder()?.query(null)?.build()?.toString() }
        genre = genres.joinToString().ifEmpty { null }
        status = statusOf(badges)
    }
}

@Serializable
class SystemDto(
    @SerialName("cap_sitekey") val capSitekey: String,
    @SerialName("cap_api_endpoint") val capApiEndpoint: String,
)

@Serializable
class CapChallengeResponse(
    val challenge: CapChallenge,
    val token: String,
)

@Serializable
class CapChallenge(
    @SerialName("c") val count: Int,
    @SerialName("s") val saltSize: Int,
    @SerialName("d") val difficulty: Int,
)

@Serializable
class CapRedeemRequest(
    private val token: String,
    private val solutions: List<Int>,
)

@Serializable
class CapRedeemResponse(
    val success: Boolean,
    val token: String?,
)

@Serializable
class ReaderPassRequest(
    private val token: String,
    private val sitekey: String,
)

@Serializable
internal class PageRead(
    val challenge: Boolean = false,
    val status: Int?,
    val obstacle: Obstacle?,
    val result: JsonObject?,
)

@Serializable
internal class Obstacle(val x: Float, val y: Float, val scale: Float)

/** A point in CSS pixels, and whether its element is the one found at that point. */
@Serializable
class Point(
    val x: Float,
    val y: Float,
    val clear: Boolean = true,
    val fromX: Float?,
    val fromY: Float?,
)

// What the reader script reads from the site's chapter reader
@Serializable
internal class ReaderPage(
    val scale: Float,
    val width: Float,
    val height: Float,
    val gate: Point?,
    val check: Boolean = false,
    val turnstile: Boolean = false,
    val textOnly: Boolean = false,
    val pages: List<ReaderItem> = emptyList(),
    val grip: Point?,
    val text: String?,
    val image: PageImage?,
)

@Serializable
internal class ReaderItem(val index: Int, val top: Float, val height: Float, val ready: Boolean, val retry: Point?)

@Serializable
internal class PageImage(val index: Int, val data: String)

@Serializable
internal class SeriesPage(
    val scale: Float,
    val id: String?,
    val height: Float,
    val width: Float,
    val tap: Point?,
    val title: String?,
    val altTitle: String?,
    val badges: List<String> = emptyList(),
    val description: String?,
    val cover: String?,
    val scans: List<String> = emptyList(),
    val author: String?,
    val rows: List<Row> = emptyList(),
    val more: Point?,
    val count: Int = -1,
    val rating: Float?,
    val votes: Int?,
)

@Serializable
internal class ListingPage(
    val scale: Float,
    val width: Float,
    val height: Float,
    val tap: Point?,
    val error: Int?,
    val cards: List<Card>?,
    val next: Boolean = false,
    val ids: Map<String, String?> = emptyMap(),
)

@Serializable
internal class GenrePanel(
    val scale: Float,
    val width: Float,
    val height: Float,
    val tap: Point?,
    val names: List<String>?,
)

// A genre of the filter panel, with its id in filter data stored by earlier versions
@Serializable
internal class GenreOption(val name: String, val id: String?)

@Serializable
internal class Row(val href: String, val heading: String, val title: String?, val age: String?)
