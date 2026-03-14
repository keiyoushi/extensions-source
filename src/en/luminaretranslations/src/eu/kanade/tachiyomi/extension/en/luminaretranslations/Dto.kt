package eu.kanade.tachiyomi.extension.en.luminaretranslations

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = slug
        title = this@EntryData.title
        thumbnail_url = "$cdnUrl/$coverImage"
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
    private val genres: List<Genre>?,
    private val description: String?,
    @SerialName("alternative_titles") private val alternativeTitles: List<String>?,
    private val people: List<People>?,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@Details.title
        author = people?.filter { it.role == "author" }?.joinToString { it.name }
        artist = people?.filter { it.role == "artist" }?.joinToString { it.name }
        description = buildString {
            this@Details.description?.let { append(it) }
            if (!alternativeTitles.isNullOrEmpty()) {
                append("\n\nAlternative Titles: ")
                append(alternativeTitles.joinToString())
            }
        }

        genre = genres?.joinToString { it.name }
        status = when (this@Details.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = "$cdnUrl/$coverImage"
    }
}

@Serializable
class Genre(
    val name: String,
)

@Serializable
class People(
    val name: String,
    val role: String,
)

@Serializable
class ChapterResponse(
    val data: List<ChapterData>,
)

@Serializable
class ChapterData(
    @SerialName("title") private val title: String?,
    @SerialName("chapter_number") private val chapterNumber: Float,
    private val slug: String,
) {
    fun toSChapter(entrySlug: String) = SChapter.create().apply {
        val chapterNum = if (chapterNumber % 1f == 0f) chapterNumber.toInt() else chapterNumber
        val chapterName = if (!title.isNullOrEmpty()) "Chapter $chapterNum - $title" else "Chapter $chapterNum"
        url = "$entrySlug/$slug"
        name = chapterName
        chapter_number = chapterNumber
    }
}

@Serializable
class ViewerResponse(
    val data: ViewerData,
)

@Serializable
class ViewerData(
    val images: List<Images>,
)

@Serializable
class Images(
    @SerialName("image_path") val imagePath: String,
    val order: Int,
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
    @JsonNames("value") val name: String,
    @JsonNames("label") val slug: String,
)
