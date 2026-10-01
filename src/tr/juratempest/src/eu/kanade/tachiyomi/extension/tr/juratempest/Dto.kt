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
class EmptyPayload

@Serializable
class SearchRequestPayload(
    val q: String,
    val limit: Int,
    val offset: Int,
)

@Serializable
class MangaSlugPayload(
    val slug: String,
)

@Serializable
class ChapterReleasePayload(
    val mangaSlug: String,
    val chapterSlug: String,
)

@Serializable
class RpcResponse<T>(
    val json: T,
)

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class MangaDto(
    val slug: String,
    private val titleTr: String? = null,
    private val titleEn: String? = null,
    private val titleJp: String? = null,
    private val titleJpRomaji: String? = null,
    private val description: String? = null,
    private val synopsis: String? = null,
    private val coverImageUrl: String? = null,
    private val coverImageKey: String? = null,
    private val seriesStatus: String? = null,
    private val genres: List<NameDto>? = null,
    private val themes: List<NameDto>? = null,
    private val writers: List<NameDto>? = null,
    private val artists: List<NameDto>? = null,
) {
    val title: String
        get() = titleTr?.ifBlank { null }
            ?: titleEn?.ifBlank { null }
            ?: titleJp?.ifBlank { null }
            ?: titleJpRomaji?.ifBlank { null }
            ?: slug

    fun matches(query: String): Boolean {
        val q = query.normalize()
        return (titleTr?.normalize()?.contains(q) == true) ||
            (titleEn?.normalize()?.contains(q) == true) ||
            (titleJp?.normalize()?.contains(q) == true) ||
            (titleJpRomaji?.normalize()?.contains(q) == true) ||
            slug.normalize().contains(q)
    }

    fun toSManga(): SManga = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverImageUrl ?: coverImageKey?.let { "https://cdn.juratempe.st/$it" }
    }

    fun toSMangaDetails(): SManga = toSManga().apply {
        description = this@MangaDto.description?.ifBlank { null } ?: synopsis
        author = writers?.joinToString { it.name }
        artist = artists?.joinToString { it.name }
        genre = (genres.orEmpty() + themes.orEmpty())
            .map { it.name }
            .distinct()
            .joinToString()
        status = when (seriesStatus?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

private fun String.normalize(): String = lowercase()
    .replace('ç', 'c')
    .replace('ğ', 'g')
    .replace('ı', 'i')
    .replace("i̇", "i")
    .replace('ö', 'o')
    .replace('ş', 's')
    .replace('ü', 'u')

@Serializable
class ChapterDto(
    val slug: String,
    private val title: String,
    private val number: Float? = null,
    private val isSpecial: Boolean = false,
    private val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = title
        chapter_number = number ?: -1f
        date_upload = Instant.tryParse(createdAt)
        scanlator = if (isSpecial) "Özel Bölüm" else null
    }
}

@Serializable
class PageDto(
    val number: Int,
    private val imageUrl: String? = null,
    private val imageKey: String? = null,
) {
    val url: String?
        get() = imageUrl ?: imageKey?.let { "https://cdn.juratempe.st/$it" }
}

@Serializable
class ReleaseDto(
    val pages: List<PageDto>,
)

@Serializable
class LatestReleaseDto(
    private val chapter: LatestChapterDto,
) {
    fun toSManga(): SManga = chapter.manga.toSManga()
}

@Serializable
class LatestChapterDto(
    val manga: MangaDto,
)

@Serializable
class SearchResultDto(
    val hits: List<MangaDto>,
    val estimatedTotalHits: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
)
