package eu.kanade.tachiyomi.extension.tr.juratempest

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class RpcRequest<T>(
    val json: T,
)

@Serializable
class RpcResponse<T>(
    val json: T,
)

@Serializable
class EmptyRequest

@Serializable
class SearchRequest(
    val q: String,
    val limit: Int,
    val offset: Int,
)

@Serializable
class SlugRequest(
    val slug: String,
)

@Serializable
class ChapterRequest(
    val mangaSlug: String,
    val chapterSlug: String,
)

@Serializable
class SearchResponse(
    val hits: List<MangaDto> = emptyList(),
    val limit: Int = 0,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val titleTr: String,
    private val description: String? = null,
    private val coverImageUrl: String? = null,
    private val seriesStatus: String? = null,
    private val genres: List<NamedDto>? = null,
    private val themes: List<NamedDto>? = null,
    private val writers: List<NamedDto>? = null,
    private val artists: List<NamedDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = titleTr
        author = writers?.joinToString { it.name }
        artist = artists?.joinToString { it.name }
        description = description
        thumbnail_url = coverImageUrl
        genre = buildList {
            (genres ?: emptyList()).forEach { add(it.name) }
            (themes ?: emptyList()).forEach { add(it.name) }
        }.joinToString()
        status = parseSeriesStatus(seriesStatus)
    }
}

@Serializable
class NamedDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: Float,
    private val title: String,
    private val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/explore/$mangaSlug/$slug"
        name = title
        chapter_number = number
        date_upload = createdAt?.let { Instant.tryParse(it) } ?: 0L
    }
}

@Serializable
class ReleaseDto(
    private val pages: List<PageDto> = emptyList(),
    private val chapter: ReleaseChapterDto? = null,
) {
    fun pages(): List<PageDto> = pages

    fun toSMangaOrNull(): SManga? = chapter?.toSMangaOrNull()
}

@Serializable
class PageDto(
    private val imageUrl: String,
) {
    fun imageUrl(): String = imageUrl
}

@Serializable
class ReleaseChapterDto(
    private val manga: MangaDto? = null,
) {
    fun toSMangaOrNull(): SManga? = manga?.toSManga()
}

@Serializable
class RecommendationDto(
    private val manga: MangaDto,
) {
    fun toSManga(): SManga = manga.toSManga()
}

private fun parseSeriesStatus(status: String?): Int = when (status?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled", "dropped" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
