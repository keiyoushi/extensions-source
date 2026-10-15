package eu.kanade.tachiyomi.extension.tr.mangaportali

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class PagedResponse<T>(
    val items: List<T>,
    private val page: Int,
    private val totalPages: Int,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    private val description: String? = null,
    private val coverImageUrl: String? = null,
    private val status: String? = null,
    private val genres: List<GenreWrapper> = emptyList(),
    private val tags: List<TagWrapper> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverImageUrl
        description = this@SeriesDto.description?.trim()?.ifBlank { null }
        genre = (genres.map { it.genre.name } + tags.map { it.tag.name })
            .distinct()
            .joinToString()
            .ifBlank { null }
        status = when (this@SeriesDto.status) {
            "ONGOING", "CURRENT" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreWrapper(val genre: NamedDto)

@Serializable
class TagWrapper(val tag: NamedDto)

@Serializable
class NamedDto(val name: String)

@Serializable
class ChapterDto(
    private val id: String,
    private val slug: String,
    private val title: String,
    private val number: Float,
    private val publishedAt: String,
    val isEarlyAccessLocked: Boolean,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = id
        memo = buildJsonObject {
            put("series", seriesSlug)
            put("slug", slug)
        }
        name = title
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterPagesResponse(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val index: Int,
    val imageUrl: String,
)
