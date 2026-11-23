package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
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
    private val demographics: List<EntityDto>? = null,
    private val year: Int? = null,
    private val rating: Float? = null,
    private val ratingCount: Int? = null,
    private val altTitles: List<String>? = null,
    private val links: Map<String, String>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$hid${slug?.let { "-$it" } ?: ""}"
        title = this@MangaDetailsDto.title
        thumbnail_url = poster?.large ?: poster?.medium ?: poster?.small
        author = authors?.joinToString { it.title }
        artist = artists?.joinToString { it.title }
        description = MarkdownDescription.Builder()
            .rating(rating?.takeIf { (ratingCount ?: 0) > 0 }, votes = ratingCount)
            .info(year?.toString())
            .description(synopsisHtml?.let { Jsoup.parseBodyFragment(it).body() })
            .altTitles(altTitles.orEmpty())
            .links(links.orEmpty().mapNotNull { (key, url) -> LINK_NAMES[key]?.let { it to url } })
            .build()
        genre = buildList {
            type?.replaceFirstChar { it.uppercase() }?.let { add(it) }
            genres?.forEach { add(it.title) }
            demographics?.forEach { add(it.title) }
            themes?.forEach { add(it.title) }
        }.joinToString()
        status = when (this@MangaDetailsDto.status?.lowercase()) {
            "releasing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "on_hiatus" -> SManga.ON_HIATUS
            "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    companion object {
        private val LINK_NAMES = mapOf(
            "mal" to "MyAnimeList",
            "al" to "AniList",
            "mu" to "MangaUpdates",
            "md" to "MangaDex",
            "mb" to "MangaBaka",
        )
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
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: Float,
    private val name: String? = null,
    private val createdAt: Long? = null,
    private val type: String? = null,
) {
    fun toSChapter(mangaUrl: String, langCode: String): SChapter = SChapter.create().apply {
        val numberText = number.toString().removeSuffix(".0")
        url = "$mangaUrl/$id-chapter-$numberText-$langCode"
        chapter_number = number
        name = buildString {
            append("Ch. $numberText")
            if (!this@ChapterDto.name.isNullOrBlank()) {
                append(" - ")
                append(this@ChapterDto.name)
            }
        }
        scanlator = type
        createdAt?.let { date_upload = it * 1000 }
    }
}

@Serializable
class PagesResponse(val data: ChapterDataDto)

@Serializable
class ChapterDataDto(val pages: List<PageDto>)

@Serializable
class PageDto(val url: String)
