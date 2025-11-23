package eu.kanade.tachiyomi.extension.en.hyakuro

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class PaginatedResponse(
    val data: List<MangaResponse>,
    val meta: Meta,
)

@Serializable
class MangaResponse(
    val attributes: MangaAttributes,
)

@Serializable
class MangaAttributes(
    @SerialName("Title") private val title: String,
    private val slug: String,
    @SerialName("Synopsis") private val synopsis: String?,
    @SerialName("Artist") private val artist: String?,
    @SerialName("Author") private val author: String?,
    @SerialName("Status") private val status: String?,
    @SerialName("Cover") private val cover: CoverObject?,
    @SerialName("Chapters") val chapters: List<ChapterInListDto>?,
    @SerialName("Categories") private val categories: List<String>?,
    @SerialName("Longstrip") private val longstrip: Boolean?,
    @SerialName("Oneshot") val oneshot: Boolean?,
    val publishedAt: String?,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@MangaAttributes.title
        url = "/manga/$slug"
        thumbnail_url = cover?.data?.attributes?.url?.let { "$baseUrl/backend$it" }
        author = this@MangaAttributes.author
        artist = this@MangaAttributes.artist
        status = when (this@MangaAttributes.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        description = synopsis
        genre = (
            categories?.plus(
                listOfNotNull(
                    "Longstrip".takeIf { longstrip == true },
                    "Oneshot".takeIf { oneshot == true },
                ),
            )
            )?.joinToString()
    }
}

@Serializable
class CoverObject(
    val data: CoverData,
)

@Serializable
class CoverData(
    val attributes: CoverAttributes,
)

@Serializable
class CoverAttributes(
    val url: String,
)

@Serializable
class ChapterInListDto(
    val id: Int,
    @SerialName("Chapter") val chapter: Float,
    @SerialName("Title") private val title: String?,
    @SerialName("TranslatedOn") private val translatedOn: String?,
    @SerialName("Pages") val pages: PageListDto?,
) {
    fun toSChapter(mangaSlug: String, parent: MangaAttributes): SChapter = SChapter.create().apply {
        url = "$mangaSlug#$chapter#$id"
        val chapterStr = if (chapter % 1 == 0f) {
            chapter.toInt().toString()
        } else {
            chapter.toString()
        }
        name = when {
            title == null && parent.oneshot == true -> "Oneshot"
            title == null && parent.oneshot == false -> "Chapter $chapterStr"
            title != null && parent.oneshot == true -> "Oneshot - $title"
            title != null && parent.oneshot == false -> "Chapter $chapterStr - $title"
            else -> "Chapter $chapterStr"
        }
        val date = translatedOn ?: parent.publishedAt
        date_upload = when {
            date!!.contains("T") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).tryParse(date)
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).tryParse(date)
        }
        chapter_number = chapter
    }
}

@Serializable
class PageListDto(
    val data: List<PageData>,
)

@Serializable
class PageData(
    val attributes: PageAttributes,
)

@Serializable
class PageAttributes(
    val url: String,
)

@Serializable
class Meta(
    val pagination: Pagination,
)

@Serializable
class Pagination(
    val page: Int,
    val pageCount: Int,
)
