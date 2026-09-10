package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SearchResponse(
    val data: List<Data>,
    val meta: Meta,
)

@Serializable
class Data(
    private val documentId: String,
    private val title: String,
    private val description: String?,
    private val author: String?,
    @SerialName("manga_status") private val mangaStatus: String?,
    private val artist: String?,
    private val slug: String,
    private val media: List<Media>?,
    private val category: List<Category>?,
    private val chapters: List<ChapterData>? = null,
) {
    fun toSChapters() = chapters.orEmpty().map { it.toSChapter(slug, documentId) }

    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "$slug#$documentId"
        title = this@Data.title
        thumbnail_url = media?.firstOrNull()?.coverImage?.url?.let { cdnUrl + it }
        description = this@Data.description
        author = this@Data.author
        artist = this@Data.artist
        genre = category?.joinToString { it.name }
        status = when (mangaStatus) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class Meta(
    val pagination: Pagination,
)

@Serializable
class Pagination(
    private val page: Int,
    private val pageCount: Int,
) {
    fun hasNextPage() = page < pageCount
}

@Serializable
class Media(
    val coverImage: CoverImage?,
)

@Serializable
class CoverImage(
    val url: String?,
)

@Serializable
class Category(
    val name: String,
)

@Serializable
class ChapterPageResponse(
    val data: List<ChapterPage>,
)

@Serializable
class ChapterPage(
    val htmlContent: String?,
)

@Serializable
class ChapterData(
    private val number: Float,
    private val title: String?,
    private val createdAt: String?,
) {
    fun toSChapter(slug: String, documentId: String) = SChapter.create().apply {
        val chapterNum = if (number % 1f == 0f) number.toInt() else number
        url = "$slug/$number#$documentId"
        name = buildString {
            append("Chapter $chapterNum")
            if (!title.isNullOrBlank() && title != "Chapter $chapterNum") append(" - $title")
        }
        chapter_number = number
        date_upload = Instant.tryParse(createdAt)
    }
}
