package eu.kanade.tachiyomi.extension.en.luminaretranslations

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseZonedDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class EntryResponse(
    val data: List<EntryData>,
    val meta: Meta,
)

@Serializable
class EntryData(
    private val title: String,
    private val slug: String,
    val type: String?,
    @SerialName("cover_image") private val coverImage: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@EntryData.title
        thumbnail_url = coverImage
    }
}

@Serializable
class Meta(
    val total: Int,
)

@Serializable
class InfoRow(
    val label: String,
    val value: String,
)

@Serializable
class ChapterData(
    private val id: Int,
    private val number: Float,
    private val title: String?,
    private val subtitle: String?,
    @SerialName("published_at") private val publishedAt: String?,
) {
    fun toSChapter(entrySlug: String) = SChapter.create().apply {
        val chapterNum = if (number % 1f == 0f) number.toInt() else number
        url = id.toString()
        memo = buildJsonObject { put("seriesSlug", entrySlug) }
        name = (title?.takeIf { it.isNotBlank() } ?: "Chapter $chapterNum") +
            (subtitle?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: "")
        chapter_number = number
        date_upload = dateFormat.tryParseZonedDateTime(publishedAt)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[XXX][XX]", Locale.ROOT)

@Serializable
class FilterResponse(
    val genres: List<Filters>,
    val tags: List<Filters>,
    val authors: List<Filters>,
    val artists: List<Filters>,
    val statuses: List<Filters>,
    val sorts: List<Filters>,
)

@Serializable
class Filters(
    @JsonNames("label") val name: String,
    @JsonNames("value") val slug: String,
)
