package eu.kanade.tachiyomi.extension.en.azuki

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import kotlin.text.replace

@Serializable
class DetailsDto(
    private val slug: String,
    private val uuid: String,
    private val name: String,
    @SerialName("short_description") private val shortDescription: String?,
    @SerialName("is_complete") private val isComplete: Boolean?,
    private val image: Image?,
    private val tags: List<String>?,
    private val creators: List<Creator>?,
    private val credits: String?,
    @SerialName("release_schedule") private val releaseSchedule: String?,
    @SerialName("alt_titles") private val altTitles: List<AltTitle>?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "$slug#$uuid"
        title = name
        thumbnail_url = image?.webp?.maxBy { it.width }?.url?.replace(Regex("""/\d+_"""), "/2400_")
        author = creators?.joinToString { it.name }
        description = buildString {
            append(shortDescription)
            if (!credits.isNullOrBlank()) {
                append("\n\n$credits")
            }
            if (!altTitles.isNullOrEmpty()) {
                append("\n\nAlternative Titles:")
                altTitles
                    .map { it.name }
                    .forEach { append("\n$it") }
            }
            if (!releaseSchedule.isNullOrBlank()) {
                append("\n\n$releaseSchedule")
            }
        }
        genre = tags?.joinToString { it }
        status = if (isComplete == true) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class Image(
    val webp: List<Webp>,
)

@Serializable
class Webp(
    val url: String,
    val width: Int,
)

@Serializable
class Creator(
    val name: String,
)

@Serializable
class AltTitle(
    val name: String,
)

@Serializable
class ChapterDto(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    val uuid: String,
    private val title: String?,
    private val label: String,
    @SerialName("release_date") private val releaseDate: String?,
    @SerialName("free_published_date") val freePublishedDate: String?,
    @SerialName("free_unpublished_date") val freeUnpublishedDate: String?,
    @SerialName("is_upcoming") private val isUpcoming: Boolean?,
) {
    fun toSChapter(slug: String, isLocked: Boolean, dateFormat: SimpleDateFormat): SChapter = SChapter.create().apply {
        url = "$uuid#$slug"
        val chapter = "Chapter $label"
        val fullTitle = if (title != null) "$chapter - $title" else chapter
        val upcoming = if (isUpcoming == true) "$fullTitle - [Upcoming]" else fullTitle
        name = if (isLocked) "🔒 $upcoming" else upcoming
        date_upload = dateFormat.tryParse(releaseDate)
    }
}

@Serializable
class UserMangaStatusDto(
    @SerialName("purchased_chapter_uuids")
    val purchasedChapterUuids: List<String> = emptyList(),
    @SerialName("unlocked_chapter_uuids")
    val unlockedChapterUuids: List<String> = emptyList(),
)

@Serializable
class PageListDto(
    val data: PageDataDto?,
)

@Serializable
class PageDataDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val image: Image,
)
