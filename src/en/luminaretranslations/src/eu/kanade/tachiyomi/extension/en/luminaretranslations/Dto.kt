package eu.kanade.tachiyomi.extension.en.luminaretranslations

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
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
class DetailsResponse(
    val data: Details,
)

@Serializable
class Details(
    private val title: String,
    private val status: String?,
    @SerialName("cover_image") private val coverImage: String?,
    private val genres: List<String>?,
    private val description: String?,
    private val author: String?,
    private val artist: String?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@Details.title
        author = this@Details.author
        artist = this@Details.artist
        description = this@Details.description
        genre = genres?.joinToString()
        status = when (this@Details.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = coverImage
    }
}

@Serializable
class ChapterResponse(
    val data: List<ChapterData>,
)

@Serializable
class ChapterData(
    private val title: String,
    private val number: Float,
    private val slug: String,
    @SerialName("published_at") private val publishedAt: String?,
) {
    fun toSChapter(entrySlug: String) = SChapter.create().apply {
        url = "$entrySlug/$slug"
        name = title
        chapter_number = number
        date_upload = dateFormat.tryParse(publishedAt)
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

@Serializable
class ViewerResponse(
    val data: ViewerData,
)

@Serializable
class ViewerData(
    val pages: List<String>,
)

@Serializable
class FilterResponse(
    val genres: List<Filters>,
    val tags: List<Filters>,
    val authors: List<Filters>,
    val artists: List<Filters>,
    val types: List<Filters>,
    val statuses: List<Filters>,
    val sorts: List<Filters>,
)

@Serializable
class Filters(
    @JsonNames("label") val name: String,
    @JsonNames("value") val slug: String,
)
