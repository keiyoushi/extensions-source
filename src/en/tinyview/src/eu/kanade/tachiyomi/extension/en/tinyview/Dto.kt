package eu.kanade.tachiyomi.extension.en.tinyview

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseZonedDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

@Serializable
class ComicResponseDto(val comics: ComicDto)

@Serializable
class ComicDto(
    val title: String,
    val description: String? = null,
    val links: Map<String, String> = emptyMap(),
    val panels: List<PanelDto>,
    @SerialName("show-to") val showTo: String? = null,
)

@Serializable
class PanelDto(
    private val title: String? = null,
    private val description: String? = null,
    val image: String? = null,
    val action: String? = null,
    private val actionType: String? = null,
    val template: String? = null,
    private val datetime: String? = null,
    @SerialName("show-to") private val showTo: String? = null,
    private val inAppProducts: JsonElement? = null,
) {
    // A series in the directory, or a comic in a series' list: "/<series>/.../index.json"
    val path get() = action?.removeSuffix("/index.json")

    fun matches(query: String) = title.orEmpty().contains(query, ignoreCase = true) || description.orEmpty().contains(query, ignoreCase = true)

    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = path!!
        title = this@PanelDto.title!!
        description = this@PanelDto.description
        thumbnail_url = image?.let { "$cdnUrl/".toHttpUrl().resolve(it).toString() }
    }

    // Links to other sites and subscription offers sit in the same list as the comics
    fun isComicOf(series: String) = action != null && action.startsWith("/$series/") && action.endsWith("/index.json") &&
        actionType.isNullOrEmpty() && inAppProducts == null

    fun toSChapter(number: Int) = SChapter.create().apply {
        url = path!!
        name = if (showTo == SUBSCRIBERS_ONLY) "🔒 ${title!!.trim()}" else title!!.trim()
        chapter_number = number.toFloat()
        date_upload = date()
    }

    // ISO 8601 for recent comics, RFC 1123 for older ones
    fun date() = Instant.tryParse(datetime).takeIf { it != 0L } ?: DateTimeFormatter.RFC_1123_DATE_TIME.tryParseZonedDateTime(datetime)
}

const val SUBSCRIBERS_ONLY = "subscribers-only"
