package eu.kanade.tachiyomi.extension.pt.mangalivreorg

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val series: List<ListItemDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class CategoryListDto(
    val series: List<ListItemDto> = emptyList(),
)

@Serializable
class ListItemDto(
    private val name: String,
    private val link: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = link.substringAfterLast('/')
        title = name
        thumbnail_url = cover?.takeIf(String::isNotBlank)
    }
}

@Serializable
class MangaDetailsDto(
    val manga: MangaDto,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class MangaDto(
    val slug: String,
    private val title: String,
    private val alternative: String = "",
    private val description: String = "",
    @SerialName("coverUrl") private val cover: String = "",
    private val author: String = "",
    private val artist: String = "",
    private val status: String = "",
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover.takeIf(String::isNotBlank)
        author = this@MangaDto.author.takeIf(String::isNotBlank)
        artist = this@MangaDto.artist.takeIf(String::isNotBlank)
        genre = genres.joinToString { it.name }
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@MangaDto.description.takeIf(String::isNotBlank)?.let(::appendLine)
            alternative.takeIf(String::isNotBlank)?.let {
                appendLine()
                append("Título alternativo: ", it)
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
class ChapterDto(
    private val id: String,
    private val legacyId: Long,
    private val number: Float,
    private val title: String = "",
    private val publishedAt: String? = null,
    private val scan: ScanDto? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val formattedNumber = number.formatted()
        url = id
        name = buildString {
            append("Capítulo ", formattedNumber)
            title.takeIf { it.isNotBlank() && it != formattedNumber && !it.equals("Capítulo $formattedNumber", ignoreCase = true) }
                ?.let { append(": ", it) }
        }
        chapter_number = number
        date_upload = publishedAt?.let(Instant::parseOrNull)?.toEpochMilliseconds() ?: 0L
        scanlator = scan?.name?.takeIf(String::isNotBlank)
        memo = buildJsonObject {
            put("slug", mangaSlug)
            put("legacyId", legacyId)
            put("number", formattedNumber)
        }
    }

    private fun Float.formatted(): String = if (this % 1 == 0f) toInt().toString() else toString()
}

@Serializable
class ScanDto(
    val name: String = "",
)

@Serializable
class ChapterPagesDto(
    private val pages: List<PageDto> = emptyList(),
) {
    fun toPageList() = pages.sortedBy { it.number }
        .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
}

@Serializable
class PageDto(
    val number: Int = 0,
    val imageUrl: String,
)

@Serializable
class FilterData(
    val genres: List<GenreDto> = emptyList(),
)
