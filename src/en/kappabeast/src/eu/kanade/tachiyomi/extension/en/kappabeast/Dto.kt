package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaResponse(
    val data: List<MangaDto>,
    val meta: MetaDto? = null,
)

@Serializable
class MangaDto(
    private val title: String,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    @SerialName("manga_status") private val mangaStatus: String? = null,
    private val slug: String,
    private val media: List<MediaWrapperDto>? = null,
    private val category: List<CategoryDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/series/$slug"
        this.title = this@MangaDto.title

        val imageObj = media?.firstOrNull()?.coverImage
        val imgUrl = imageObj?.formats?.large?.url
            ?: imageObj?.formats?.medium?.url
            ?: imageObj?.url
        if (imgUrl != null) {
            thumbnail_url = if (imgUrl.startsWith("http")) imgUrl else "https://strapi.kappabeast.com$imgUrl"
        }

        this.author = this@MangaDto.author
        this.artist = this@MangaDto.artist
        this.description = this@MangaDto.description
        this.status = parseStatus(mangaStatus)
        this.genre = category?.mapNotNull { it.name }?.joinToString(", ")
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

@Serializable
class MediaWrapperDto(
    val coverImage: CoverImageDto? = null,
)

@Serializable
class CoverImageDto(
    val url: String? = null,
    val formats: ImageFormatsDto? = null,
)

@Serializable
class ImageFormatsDto(
    val medium: ImageDto? = null,
    val large: ImageDto? = null,
)

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class CategoryDto(
    val name: String? = null,
)

@Serializable
class MetaDto(
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    val page: Int,
    val pageCount: Int,
)

@Serializable
class ChapterResponse(
    val data: List<ChapterDto>,
)

@Serializable
class SingleChapterResponse(
    val data: ChapterDto,
)

@Serializable
class ChapterDto(
    private val id: Int? = null,
    private val documentId: String? = null,
    private val title: String? = null,
    private val number: Float? = null,
    private val createdAt: String? = null,
    private val publishedAt: String? = null,
    val htmlContent: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = documentId ?: id?.toString() ?: ""

        var chapterName = title ?: "Chapter ${number?.toString()?.removeSuffix(".0")}"
        if (number != null && !chapterName.contains("Chapter", ignoreCase = true)) {
            chapterName = "Chapter ${number.toString().removeSuffix(".0")} - $chapterName"
        }
        this.name = chapterName

        this.chapter_number = number ?: -1f
    }

    fun getDateString(): String? = publishedAt ?: createdAt
}
