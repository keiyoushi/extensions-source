package eu.kanade.tachiyomi.extension.pt.remangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val comics: List<MangaDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class MangaDto(
    val slug: String,
    private val title: String,
    @SerialName("title_alt") private val alternativeTitles: List<String>? = null,
    private val synopsis: String? = null,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = cover
        genre = (listOfNotNull(type?.replaceFirstChar(Char::uppercase)) + genres.map(GenreDto::name)).joinToString()
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            synopsis?.takeIf(String::isNotBlank)?.let(::appendLine)
            alternativeTitles?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)?.let {
                appendLine()
                append("Títulos alternativos: ", it.joinToString())
            }
        }.trim()
    }
}

@Serializable
class GenreDto(
    val name: String,
    val slug: String = "",
)

@Serializable
class GenreListDto(
    val data: List<GenreDto> = emptyList(),
)

@Serializable
class ChapterUpdateListDto(
    private val data: List<ChapterUpdateDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
) {
    val mangas get() = data.distinctBy(ChapterUpdateDto::comicSlug).map(ChapterUpdateDto::toSManga)
    val hasNextPage get() = page < totalPages
}

@Serializable
class ChapterUpdateDto(
    @SerialName("comic_slug") val comicSlug: String,
    @SerialName("comic_title") private val comicTitle: String,
    @SerialName("comic_cover") private val comicCover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$comicSlug"
        title = comicTitle
        thumbnail_url = comicCover
    }
}

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float,
    private val slug: String,
    private val title: String? = null,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = id
        name = buildString {
            append("Capítulo ", number.formatted())
            title?.trim()
                ?.takeIf { it.isNotEmpty() && it != number.formatted() && !it.startsWith("Capítulo", ignoreCase = true) }
                ?.let { append(" - ", it) }
        }
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
        memo = buildJsonObject {
            put("mangaSlug", mangaSlug)
            put("slug", slug)
        }
    }

    private fun Float.formatted(): String = toString().removeSuffix(".0")
}

@Serializable
class ChapterPagesDto(
    private val pages: List<PageDto> = emptyList(),
) {
    fun toPageList() = pages.sortedBy(PageDto::number)
        .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
}

@Serializable
class PageDto(
    val number: Int = 0,
    @SerialName("image_url") val imageUrl: String,
)
