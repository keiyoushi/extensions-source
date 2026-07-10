package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class ApiResponse<T>(
    val items: List<T> = emptyList(),
    val meta: ApiMeta? = null,
)

@Serializable
class ApiMeta(
    val lastPage: Int = 1,
    val hasNext: Boolean = false,
)

@Serializable
class TagResponse(val data: List<TagDto> = emptyList())

@Serializable
class TagDto(val id: Int, val type: String)

@Serializable
class MangaDto(
    private val hid: String,
    private val slug: String? = null,
    private val title: String,
    private val poster: PosterDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$hid${slug?.let { "-$it" } ?: ""}"
        title = this@MangaDto.title
        thumbnail_url = poster?.large ?: poster?.medium ?: poster?.small
        initialized = false
    }
}

@Serializable
class MangaDetailsResponse(val data: MangaDetailsDto)

@Serializable
class MangaDetailsDto(
    private val hid: String,
    private val slug: String? = null,
    private val title: String,
    private val type: String? = null,
    private val status: String? = null,
    private val poster: PosterDto? = null,
    private val synopsisHtml: String? = null,
    private val authors: List<EntityDto>? = null,
    private val artists: List<EntityDto>? = null,
    private val genres: List<EntityDto>? = null,
    private val themes: List<EntityDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$hid${slug?.let { "-$it" } ?: ""}"
        title = this@MangaDetailsDto.title
        thumbnail_url = poster?.large ?: poster?.medium ?: poster?.small
        author = authors?.joinToString { it.title }
        artist = artists?.joinToString { it.title }
        description = synopsisHtml?.let { Jsoup.parseBodyFragment(it).text() }
        genre = buildList {
            type?.replaceFirstChar { it.uppercase() }?.let { add(it) }
            genres?.forEach { add(it.title) }
            themes?.forEach { add(it.title) }
        }.joinToString()
        status = when (this@MangaDetailsDto.status?.lowercase()) {
            "releasing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "on_hiatus" -> SManga.ON_HIATUS
            "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class PosterDto(val small: String? = null, val medium: String? = null, val large: String? = null)

@Serializable
class EntityDto(val title: String)

@Serializable
class VolumeDto(
    private val id: Int,
    private val number: Int,
    private val name: String? = null,
    private val chapterCount: Int,
    val language: String,
) {
    fun toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        url = "$mangaUrl/volume/$id"
        chapter_number = number.toFloat()
        name = buildString {
            append("Vol. $number")
            if (!this@VolumeDto.name.isNullOrBlank()) {
                append(" - ")
                append(this@VolumeDto.name)
            }
        }
        scanlator = "$chapterCount chapters"
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: Float,
    private val name: String? = null,
    private val createdAt: Long? = null,
    val type: String? = null,
) {
    fun toSChapter(mangaUrl: String, langCode: String): SChapter = SChapter.create().apply {
        url = "$mangaUrl/$id-chapter-${number.toString().removeSuffix(".0")}-$langCode"
        chapter_number = number
        name = buildString {
            append("Ch. ")
            append(number.toString().removeSuffix(".0"))
            if (!this@ChapterDto.name.isNullOrBlank()) {
                append(" - ")
                append(this@ChapterDto.name)
            }
        }
        scanlator = type ?: "Unknown"
        date_upload = createdAt?.times(1000L) ?: 0L
    }
}

@Serializable
class PagesResponse(val data: ChapterDataDto)

@Serializable
class ChapterDataDto(val pages: List<PageDto>)

@Serializable
class PageDto(val url: String)
