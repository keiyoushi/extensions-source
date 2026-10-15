package eu.kanade.tachiyomi.extension.id.kumopoi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import kotlin.time.Instant

@Serializable
class ComicListResponse(
    private val data: ComicListData,
) {
    val items: List<ComicDto> get() = data.data
    val hasNextPage: Boolean get() = data.meta.page < data.meta.totalPages
}

@Serializable
class ComicListData(
    val data: List<ComicDto> = emptyList(),
    val meta: PaginationMeta,
)

@Serializable
class PaginationMeta(
    val page: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
class ComicDetailsResponse(
    val data: ComicDto,
)

@Serializable
class ComicDto(
    val slug: String,
    private val title: String,
    private val cover: String? = null,
    private val coverMedium: String? = null,
    private val coverSmall: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/comic/$slug"
        title = this@ComicDto.title
        thumbnail_url = buildCoverUrl(coverMedium ?: cover ?: coverSmall)
        description = this@ComicDto.description
        author = this@ComicDto.author
        artist = this@ComicDto.artist
        genre = genres.joinToString(", ") { it.name }
        status = when (this@ComicDto.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "END", "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapterList(): List<SChapter> = chapters.map { it.toSChapter(slug) }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String,
    private val title: String? = null,
    private val isLocked: Boolean = false,
    private val publishedAt: String? = null,
) {
    fun toSChapter(comicSlug: String): SChapter = SChapter.create().apply {
        val cleanNumber = number.trim()
        url = "/comic/$comicSlug/chapter/$cleanNumber#$id"
        name = buildString {
            if (isLocked) append("🔒 ")
            append("Chapter ").append(cleanNumber)
            if (!title.isNullOrBlank()) {
                append(" - ").append(title.trim())
            }
        }
        chapter_number = cleanNumber.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class PagesResponse(
    val data: PagesData,
)

@Serializable
class PagesData(
    val locked: Boolean = false,
    val pages: List<PageItemDto> = emptyList(),
)

@Serializable
class PageItemDto(
    val id: String,
    val token: String,
)

private fun buildCoverUrl(cover: String?): String? {
    if (cover.isNullOrBlank()) return null
    if (cover.startsWith("http://") || cover.startsWith("https://")) return cover
    val path = cover.removePrefix("/").split("/").joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    return "https://kumo.gorae.my.id/$path"
}
